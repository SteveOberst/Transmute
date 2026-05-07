@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package dev.transmute.audio.codecs.ios

import dev.transmute.io.TSource
import dev.transmute.audio.AudioCodec
import dev.transmute.audio.AudioDecodeOptions
import dev.transmute.audio.AudioDecoder
import dev.transmute.audio.AudioEncodeOptions
import dev.transmute.audio.AudioFormat
import dev.transmute.audio.AudioIR
import dev.transmute.audio.AudioSamples
import dev.transmute.audio.CanonicalAudioEncodeOptions
import dev.transmute.audio.codecs.WavEncoder
import dev.transmute.model.core.Bytes
import dev.transmute.codec.TimeRangeMs
import dev.transmute.common.PipelineContext
import dev.transmute.model.core.asBytes
import kotlinx.cinterop.*
import platform.AVFoundation.*
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.CoreFoundation.CFRelease
import platform.CoreMedia.*
import platform.Foundation.*
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

// ---------------------------------------------------------------------------
// Shared decode logic - AVAssetReader -> float32 PCM.
// ---------------------------------------------------------------------------

private fun writeTempFile(data: ByteArray, ext: String): NSURL {
  val tmpDir = NSTemporaryDirectory()
  val name = "transmute_aud_${NSUUID().UUIDString}.$ext"
  val path = "$tmpDir$name"
  val nsData = data.usePinned { pinned ->
    NSData.dataWithBytes(pinned.addressOf(0), data.size.toULong())
  }
  nsData?.writeToFile(path, atomically = true) ?: error("Failed to write temp audio file")
  return NSURL.fileURLWithPath(path)
}

private suspend fun decodeWithAssetReader(
  source: ByteArray,
  ext: String,
  timeRangeMs: TimeRangeMs?,
  context: PipelineContext,
): AudioIR {
  val url = writeTempFile(source, ext)
  try {
    val asset = AVURLAsset(uRL = url, options = null)
    val tracks = asset.tracksWithMediaType(AVMediaTypeAudio)
    require(tracks.isNotEmpty()) { "No audio tracks" }
    val track = tracks.first() as AVAssetTrack

    memScoped {
      val errorPtr = alloc<ObjCObjectVar<NSError?>>()
      val reader = AVAssetReader(asset = asset, error = errorPtr.ptr)
        ?: error("AVAssetReader init failed: ${errorPtr.value?.localizedDescription}")

      if (timeRangeMs != null) {
        val start = CMTimeMake(timeRangeMs.startMs, 1000)
        val duration = CMTimeMake(timeRangeMs.durationMs, 1000)
        reader.timeRange = CMTimeRangeMake(start, duration)
      }

      val settings: Map<Any?, Any?> = mapOf(
        "AVFormatIDKey" to kAudioFormatLinearPCM,
        "AVLinearPCMIsFloatKey" to true,
        "AVLinearPCMBitDepthKey" to 32,
      )

      val output = AVAssetReaderTrackOutput(track = track, outputSettings = settings)
      output.alwaysCopiesSampleData = false
      require(reader.canAddOutput(output)) { "Cannot add AVAssetReader output" }
      reader.addOutput(output)
      require(reader.startReading()) { "AVAssetReader.startReading failed" }

      var sampleRate = 0
      var channels = 0
      val floats = FloatArrayList()

      while (reader.status == AVAssetReaderStatusReading) {
        val sampleBuffer = output.copyNextSampleBuffer() ?: break
        try {
          val formatDesc = CMSampleBufferGetFormatDescription(sampleBuffer) ?: continue
          if (sampleRate == 0 || channels == 0) {
            val asbdPtr = CMAudioFormatDescriptionGetStreamBasicDescription(formatDesc)
            if (asbdPtr != null) {
              sampleRate = asbdPtr.pointed.mSampleRate.toInt()
              channels = asbdPtr.pointed.mChannelsPerFrame.toInt()
            }
          }

          val block = CMSampleBufferGetDataBuffer(sampleBuffer) ?: continue
          val length = CMBlockBufferGetDataLength(block).toInt()
          if (length <= 0) continue

          val tmp = ByteArray(length)
          tmp.usePinned { pinned ->
            val status = CMBlockBufferCopyDataBytes(block, 0uL, length.toULong(), pinned.addressOf(0))
            if (status != kCMBlockBufferNoErr) error("CMBlockBufferCopyDataBytes failed: $status")
          }

          val count = length / 4
          val chunk = FloatArray(count)
          var bi = 0
          for (i in 0 until count) {
            val b0 = tmp[bi].toInt() and 0xFF
            val b1 = tmp[bi + 1].toInt() and 0xFF
            val b2 = tmp[bi + 2].toInt() and 0xFF
            val b3 = tmp[bi + 3].toInt() and 0xFF
            val bits = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
            chunk[i] = Float.fromBits(bits)
            bi += 4
          }
          floats.addAll(chunk)
        } finally {
          CFRelease(sampleBuffer)
        }
      }

      require(reader.status == AVAssetReaderStatusCompleted || reader.status == AVAssetReaderStatusReading) {
        "AVAssetReader failed: ${reader.error?.localizedDescription}"
      }
      require(sampleRate > 0 && channels > 0) { "Could not determine audio format" }

      val dataOut = floats.toFloatArray()
      val frameCount = dataOut.size / channels
      val durationMs = (frameCount.toDouble() / sampleRate.toDouble() * 1000.0).toLong()

      return AudioIR(
        samples = AudioSamples(data = dataOut, sampleRate = sampleRate, channelCount = channels),
        sampleRate = sampleRate,
        channelCount = channels,
        durationMs = durationMs,
      )
    }
  } finally {
    runCatching { NSFileManager.defaultManager.removeItemAtURL(url, null) }
  }
}

// ---------------------------------------------------------------------------
// Shared encode logic - AudioIR -> WAV -> AVAssetExportSession -> M4A.
// ---------------------------------------------------------------------------

private suspend fun encodeToM4aWithExportSession(ir: AudioIR, context: PipelineContext): ByteArray {
  val wavBytes = WavEncoder().encode(ir, AudioFormat.Wav, CanonicalAudioEncodeOptions(), context).data
  val wavUrl = writeTempFile(wavBytes, "wav")

  val tmpDir = NSTemporaryDirectory()
  val outPath = tmpDir + "transmute_enc_${NSUUID().UUIDString}.m4a"
  val outUrl = NSURL.fileURLWithPath(outPath)

  try {
    val asset = AVURLAsset(uRL = wavUrl, options = null)
    val session = AVAssetExportSession(asset, AVAssetExportPresetAppleM4A)
      ?: error("AVAssetExportSession init failed")
    session.outputURL = outUrl
    session.outputFileType = AVFileTypeAppleM4A
    session.shouldOptimizeForNetworkUse = true

    suspendCoroutine { cont ->
      session.exportAsynchronouslyWithCompletionHandler {
        when (session.status) {
          AVAssetExportSessionStatusCompleted -> cont.resume(Unit)
          else -> cont.resumeWithException(IllegalStateException("Export failed: ${session.error?.localizedDescription}"))
        }
      }
    }

    val data = NSData.dataWithContentsOfURL(outUrl) ?: error("Failed to read export output")
    val result = ByteArray(data.length.toInt())
    result.usePinned { pin -> memcpy(pin.addressOf(0), data.bytes, data.length) }
    return result
  } finally {
    runCatching { NSFileManager.defaultManager.removeItemAtURL(wavUrl, null) }
    runCatching { NSFileManager.defaultManager.removeItemAtURL(outUrl, null) }
  }
}

// ---------------------------------------------------------------------------
// Decode-only codecs
// ---------------------------------------------------------------------------

internal class IosMp3Decoder : AudioDecoder {
  override val supportedFormats: Set<AudioFormat> = setOf(AudioFormat.Mp3)

  override suspend fun decode(source: TSource, options: AudioDecodeOptions, context: PipelineContext): AudioIR =
    decodeWithAssetReader(
      source.readAll(),
      "mp3",
      options.decodeRange?.timeframe(),
      context,
    )
}

// ---------------------------------------------------------------------------
// "Full" codec shapes (decode + encode), but iOS only encodes M4A reliably.
// ---------------------------------------------------------------------------

internal class IosFlacCodec : AudioCodec {
  override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.Flac)
  override val encodableFormats: Set<AudioFormat> = emptySet()

  override suspend fun decode(source: TSource, options: AudioDecodeOptions, context: PipelineContext): AudioIR =
    decodeWithAssetReader(
      source.readAll(),
      "flac",
      options.decodeRange?.timeframe(),
      context,
    )

  override suspend fun encode(
    ir: AudioIR,
    format: AudioFormat,
    options: AudioEncodeOptions,
    context: PipelineContext,
  ): Bytes = error("IosFlacCodec is decode-only")
}

internal class IosAacCodec : AudioCodec {
  override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.Aac)
  override val encodableFormats: Set<AudioFormat> = emptySet()

  override suspend fun decode(source: TSource, options: AudioDecodeOptions, context: PipelineContext): AudioIR =
    decodeWithAssetReader(
      source.readAll(),
      "aac",
      options.decodeRange?.timeframe(),
      context,
    )

  override suspend fun encode(
    ir: AudioIR,
    format: AudioFormat,
    options: AudioEncodeOptions,
    context: PipelineContext,
  ): Bytes = error("IosAacCodec is decode-only")
}

internal class IosM4aCodec : AudioCodec {
  override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.M4a)
  override val encodableFormats: Set<AudioFormat> = setOf(AudioFormat.M4a)

  override suspend fun decode(source: TSource, options: AudioDecodeOptions, context: PipelineContext): AudioIR =
    decodeWithAssetReader(
      source.readAll(),
      "m4a",
      options.decodeRange?.timeframe(),
      context,
    )

  override suspend fun encode(
    ir: AudioIR,
    format: AudioFormat,
    options: AudioEncodeOptions,
    context: PipelineContext,
  ): Bytes {
    require(format == AudioFormat.M4a) { "IosM4aCodec only supports M4A, got $format" }
    return encodeToM4aWithExportSession(ir, context).asBytes()
  }
}

private class FloatArrayList(initialCapacity: Int = 16) {
  private var data = FloatArray(initialCapacity)
  private var size = 0

  fun addAll(values: FloatArray) {
    ensureCapacity(size + values.size)
    values.copyInto(data, destinationOffset = size)
    size += values.size
  }

  fun toFloatArray(): FloatArray = data.copyOf(size)

  private fun ensureCapacity(capacity: Int) {
    if (capacity <= data.size) return
    var newSize = maxOf(16, data.size * 2)
    while (newSize < capacity) newSize *= 2
    data = data.copyOf(newSize)
  }
}
