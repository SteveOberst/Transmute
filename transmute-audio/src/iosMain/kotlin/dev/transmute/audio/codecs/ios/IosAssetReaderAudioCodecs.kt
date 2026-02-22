@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package dev.transmute.audio.codecs.ios

import dev.transmute.audio.AudioCodec
import dev.transmute.audio.AudioDecoder
import dev.transmute.audio.AudioIR
import dev.transmute.audio.AudioSamples
import dev.transmute.core.AudioFormat
import dev.transmute.core.TransmuteContext
import kotlinx.cinterop.*
import platform.AVFoundation.*
import platform.CoreAudioTypes.kAudioFormatFLAC
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.CoreFoundation.CFRelease
import platform.CoreMedia.*
import platform.Foundation.*
import dev.transmute.audio.AudioDecodeOptions

// ---------------------------------------------------------------------------
// Shared decode logic - AVAssetReader → float32 PCM.
// ---------------------------------------------------------------------------

private suspend fun decodeWithAssetReader(
  source: ByteArray,
  format: AudioFormat,
  context: TransmuteContext,
): AudioIR {
  val tmpDir = NSTemporaryDirectory()
  val fileName = "transmute_${NSUUID().UUIDString}_${format.extension}"
  val path = tmpDir + fileName

  val data = source.usePinned { pinned ->
    NSData.dataWithBytes(pinned.addressOf(0), source.size.toULong())
  }
  val ok = data.writeToFile(path, atomically = true)
  require(ok) { "Failed to write temp audio file" }

  val url = NSURL.fileURLWithPath(path)

  try {
    val asset = AVURLAsset(uRL = url, options = null)

    val tracks = asset.tracksWithMediaType(AVMediaTypeAudio)
    require(tracks.isNotEmpty()) { "No audio tracks" }
    val track = tracks.first() as AVAssetTrack

    memScoped {
      val errorPtr = alloc<ObjCObjectVar<NSError?>>()
      val reader = AVAssetReader(asset = asset, error = errorPtr.ptr)
        ?: error("AVAssetReader init failed: ${errorPtr.value?.localizedDescription}")

      // Use string keys to avoid K/N constant-name differences across versions.
      val settings: Map<Any?, Any?> = mapOf(
        "AVFormatIDKey" to kAudioFormatLinearPCM,
        "AVLinearPCMIsFloatKey" to true,
        "AVLinearPCMBitDepthKey" to 32,
        "AVLinearPCMIsNonInterleavedKey" to false,
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
    runCatching { NSFileManager.defaultManager.removeItemAtPath(path, null) }
  }
}

// ---------------------------------------------------------------------------
// Shared encode logic - WAV → AVAssetReader → AVAssetWriter (AAC).
// ---------------------------------------------------------------------------

/**
 * Encodes [AudioIR] to AAC/M4A using an AVAssetReader→AVAssetWriter pipeline.
 *
 * Instead of manually constructing CMSampleBuffers (fragile in K/N), we
 * write the samples to a temporary WAV, then pipe through the hardware
 * AAC encoder. Both AAC and M4A output the same .m4a container; the
 * difference is semantic (AudioFormat tag).
 */
private suspend fun encodeWithAssetWriter(ir: AudioIR, context: TransmuteContext): ByteArray {
  val sampleRate = ir.sampleRate
  val channelCount = ir.channelCount
  val samples = ir.samples.data

  val tmpDir = NSTemporaryDirectory()
  val wavPath = tmpDir + "transmute_enc_src_${NSUUID().UUIDString}.wav"
  val outPath = tmpDir + "transmute_enc_out_${NSUUID().UUIDString}.m4a"
  val outUrl = NSURL.fileURLWithPath(outPath)

  NSFileManager.defaultManager.removeItemAtPath(wavPath, null)
  NSFileManager.defaultManager.removeItemAtPath(outPath, null)

  try {
    // 1) Write IR to a temporary 16-bit PCM WAV file.
    val wavBytes = buildWav(samples, sampleRate, channelCount)
    val wavData = wavBytes.usePinned { pinned ->
      NSData.dataWithBytes(pinned.addressOf(0), wavBytes.size.toULong())
    }
    require(wavData.writeToFile(wavPath, atomically = true)) { "Failed to write temp WAV" }

    // 2) Set up AVAssetReader for the WAV.
    val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(wavPath), options = null)
    val tracks = asset.tracksWithMediaType(AVMediaTypeAudio)
    require(tracks.isNotEmpty()) { "No audio tracks in temp WAV" }
    val track = tracks.first() as AVAssetTrack

    memScoped {
      val readerError = alloc<ObjCObjectVar<NSError?>>()
      val reader = AVAssetReader(asset = asset, error = readerError.ptr)
        ?: error("AVAssetReader init failed: ${readerError.value?.localizedDescription}")

      val readerOutput = AVAssetReaderTrackOutput(track = track, outputSettings = null)
      readerOutput.alwaysCopiesSampleData = false
      require(reader.canAddOutput(readerOutput)) { "Cannot add AVAssetReader output" }
      reader.addOutput(readerOutput)

      // 3) Set up AVAssetWriter for AAC → .m4a.
      val writerError = alloc<ObjCObjectVar<NSError?>>()
      val writer = AVAssetWriter(
        uRL = outUrl,
        fileType = AVFileTypeAppleM4A,
        error = writerError.ptr,
      ) ?: error("AVAssetWriter init failed: ${writerError.value?.localizedDescription}")

      val outputSettings: Map<Any?, Any?> = mapOf(
        "AVFormatIDKey" to kAudioFormatMPEG4AAC,
        "AVEncoderBitRateKey" to 128_000,
        "AVSampleRateKey" to sampleRate,
        "AVNumberOfChannelsKey" to channelCount,
      )

      val writerInput = AVAssetWriterInput(
        mediaType = AVMediaTypeAudio,
        outputSettings = outputSettings,
      )
      writerInput.expectsMediaDataInRealTime = false
      require(writer.canAddInput(writerInput)) { "Cannot add AVAssetWriter input" }
      writer.addInput(writerInput)

      // 4) Pipe reader → writer.
      require(reader.startReading()) {
        "AVAssetReader.startReading failed: ${reader.error?.localizedDescription}"
      }
      require(writer.startWriting()) {
        "AVAssetWriter.startWriting failed: ${writer.error?.localizedDescription}"
      }
      writer.startSessionAtSourceTime(CMTimeMake(value = 0, timescale = sampleRate))

      while (reader.status == AVAssetReaderStatusReading) {
        if (!writerInput.readyForMoreMediaData) {
          kotlinx.coroutines.delay(1)
          continue
        }
        val sampleBuffer = readerOutput.copyNextSampleBuffer()
        if (sampleBuffer != null) {
          writerInput.appendSampleBuffer(sampleBuffer)
          CFRelease(sampleBuffer)
        } else {
          break
        }
      }

      writerInput.markAsFinished()

      val semaphore = platform.darwin.dispatch_semaphore_create(0)
      writer.finishWritingWithCompletionHandler {
        platform.darwin.dispatch_semaphore_signal(semaphore)
      }
      platform.darwin.dispatch_semaphore_wait(semaphore, platform.darwin.DISPATCH_TIME_FOREVER)

      require(writer.status == AVAssetWriterStatusCompleted) {
        "AVAssetWriter failed: ${writer.error?.localizedDescription}"
      }
    }

    // 5) Read encoded output.
    val outData = NSData.dataWithContentsOfFile(outPath)
      ?: error("Failed to read encoded audio from $outPath")
    val result = ByteArray(outData.length.toInt())
    result.usePinned { pinned ->
      outData.getBytes(pinned.addressOf(0), outData.length)
    }
    return result
  } finally {
    runCatching { NSFileManager.defaultManager.removeItemAtPath(wavPath, null) }
    runCatching { NSFileManager.defaultManager.removeItemAtPath(outPath, null) }
  }
}

/** Builds a minimal RIFF/WAVE PCM 16-bit LE file from float samples. */
private fun buildWav(samples: FloatArray, sampleRate: Int, channels: Int): ByteArray {
  val bitsPerSample = 16
  val bytesPerSample = bitsPerSample / 8
  val blockAlign = channels * bytesPerSample
  val dataSize = samples.size * bytesPerSample
  val headerSize = 44
  val fileSize = headerSize + dataSize - 8

  val buf = ByteArray(headerSize + dataSize)
  var pos = 0

  fun writeStr(s: String) { for (c in s) buf[pos++] = c.code.toByte() }
  fun write16(v: Int) { buf[pos++] = (v and 0xFF).toByte(); buf[pos++] = (v shr 8 and 0xFF).toByte() }
  fun write32(v: Int) {
    buf[pos++] = (v and 0xFF).toByte()
    buf[pos++] = (v shr 8 and 0xFF).toByte()
    buf[pos++] = (v shr 16 and 0xFF).toByte()
    buf[pos++] = (v shr 24 and 0xFF).toByte()
  }

  writeStr("RIFF"); write32(fileSize); writeStr("WAVE")
  writeStr("fmt "); write32(16); write16(1) // PCM
  write16(channels); write32(sampleRate)
  write32(sampleRate * blockAlign); write16(blockAlign); write16(bitsPerSample)
  writeStr("data"); write32(dataSize)

  for (s in samples) {
    val v = (s.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
    buf[pos++] = (v.toInt() and 0xFF).toByte()
    buf[pos++] = (v.toInt() shr 8 and 0xFF).toByte()
  }

  return buf
}

/**
 * Encodes [AudioIR] to FLAC using an AVAssetReader→AVAssetWriter pipeline.
 *
 * Writes IR to a temporary WAV, then pipes through the hardware FLAC encoder.
 */
private suspend fun encodeFlacWithAssetWriter(ir: AudioIR, context: TransmuteContext): ByteArray {
  val sampleRate = ir.sampleRate
  val channelCount = ir.channelCount
  val samples = ir.samples.data

  val tmpDir = NSTemporaryDirectory()
  val wavPath = tmpDir + "transmute_flac_src_${NSUUID().UUIDString}.wav"
  val outPath = tmpDir + "transmute_flac_out_${NSUUID().UUIDString}.flac"
  val outUrl = NSURL.fileURLWithPath(outPath)

  NSFileManager.defaultManager.removeItemAtPath(wavPath, null)
  NSFileManager.defaultManager.removeItemAtPath(outPath, null)

  try {
    // 1) Write IR to a temporary 16-bit PCM WAV file.
    val wavBytes = buildWav(samples, sampleRate, channelCount)
    val wavData = wavBytes.usePinned { pinned ->
      NSData.dataWithBytes(pinned.addressOf(0), wavBytes.size.toULong())
    }
    require(wavData.writeToFile(wavPath, atomically = true)) { "Failed to write temp WAV" }

    // 2) Set up AVAssetReader for the WAV.
    val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(wavPath), options = null)
    val tracks = asset.tracksWithMediaType(AVMediaTypeAudio)
    require(tracks.isNotEmpty()) { "No audio tracks in temp WAV" }
    val track = tracks.first() as AVAssetTrack

    memScoped {
      val readerError = alloc<ObjCObjectVar<NSError?>>()
      val reader = AVAssetReader(asset = asset, error = readerError.ptr)
        ?: error("AVAssetReader init failed: ${readerError.value?.localizedDescription}")

      val readerOutput = AVAssetReaderTrackOutput(track = track, outputSettings = null)
      readerOutput.alwaysCopiesSampleData = false
      require(reader.canAddOutput(readerOutput)) { "Cannot add AVAssetReader output" }
      reader.addOutput(readerOutput)

      // 3) Set up AVAssetWriter for FLAC.
      val writerError = alloc<ObjCObjectVar<NSError?>>()
      // Use "com.apple.coreaudio-format" (CAF) or write raw FLAC.
      // AVAssetWriter doesn't have a dedicated FLAC file type,
      // so we use AVFileTypeCoreAudioFormat and the FLAC codec.
      val writer = AVAssetWriter(
        uRL = outUrl,
        fileType = "com.apple.coreaudio-format",
        error = writerError.ptr,
      ) ?: error("AVAssetWriter init failed: ${writerError.value?.localizedDescription}")

      val outputSettings: Map<Any?, Any?> = mapOf(
        "AVFormatIDKey" to kAudioFormatFLAC,
        "AVSampleRateKey" to sampleRate,
        "AVNumberOfChannelsKey" to channelCount,
      )

      val writerInput = AVAssetWriterInput(
        mediaType = AVMediaTypeAudio,
        outputSettings = outputSettings,
      )
      writerInput.expectsMediaDataInRealTime = false
      require(writer.canAddInput(writerInput)) { "Cannot add AVAssetWriter input" }
      writer.addInput(writerInput)

      // 4) Pipe reader → writer.
      require(reader.startReading()) {
        "AVAssetReader.startReading failed: ${reader.error?.localizedDescription}"
      }
      require(writer.startWriting()) {
        "AVAssetWriter.startWriting failed: ${writer.error?.localizedDescription}"
      }
      writer.startSessionAtSourceTime(CMTimeMake(value = 0, timescale = sampleRate))

      while (reader.status == AVAssetReaderStatusReading) {
        if (!writerInput.readyForMoreMediaData) {
          kotlinx.coroutines.delay(1)
          continue
        }
        val sampleBuffer = readerOutput.copyNextSampleBuffer()
        if (sampleBuffer != null) {
          writerInput.appendSampleBuffer(sampleBuffer)
          CFRelease(sampleBuffer)
        } else {
          break
        }
      }

      writerInput.markAsFinished()

      val semaphore = platform.darwin.dispatch_semaphore_create(0)
      writer.finishWritingWithCompletionHandler {
        platform.darwin.dispatch_semaphore_signal(semaphore)
      }
      platform.darwin.dispatch_semaphore_wait(semaphore, platform.darwin.DISPATCH_TIME_FOREVER)

      require(writer.status == AVAssetWriterStatusCompleted) {
        "AVAssetWriter failed: ${writer.error?.localizedDescription}"
      }
    }

    // 5) Read encoded output.
    val outData = NSData.dataWithContentsOfFile(outPath)
      ?: error("Failed to read encoded FLAC from $outPath")
    val result = ByteArray(outData.length.toInt())
    result.usePinned { pinned ->
      outData.getBytes(pinned.addressOf(0), outData.length)
    }
    return result
  } finally {
    runCatching { NSFileManager.defaultManager.removeItemAtPath(wavPath, null) }
    runCatching { NSFileManager.defaultManager.removeItemAtPath(outPath, null) }
  }
}

// ---------------------------------------------------------------------------
// Decode-only codecs - formats where iOS has no encoder.
// ---------------------------------------------------------------------------

internal abstract class IosAssetReaderAudioDecoder(
  private val format: AudioFormat,
) : AudioDecoder {

  override val supportedFormats: Set<AudioFormat> = setOf(format)

  override suspend fun decode(source: ByteArray, options: AudioDecodeOptions, context: TransmuteContext): AudioIR =
    decodeWithAssetReader(source, format, context)
}

internal class IosMp3Decoder : IosAssetReaderAudioDecoder(AudioFormat.MP3) {
  override fun sniff(data: ByteArray): AudioFormat? {
    if (data.size < 2) return null
    // ID3v2 tag
    if (data.size >= 3 &&
      data[0] == 0x49.toByte() && data[1] == 0x44.toByte() && data[2] == 0x33.toByte()
    ) return AudioFormat.MP3
    // MPEG audio frame sync — validate version + layer bits to avoid
    // matching AAC ADTS frames (which also start with 0xFFF but have layer=0).
    val b0 = data[0].toInt() and 0xFF
    val b1 = data[1].toInt() and 0xFF
    if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) {
      val layer = (b1 shr 1) and 0x03   // 0=reserved (used by ADTS), 1=L3, 2=L2, 3=L1
      if (layer != 0) return AudioFormat.MP3
    }
    return null
  }
}

// ---------------------------------------------------------------------------
// Full codecs - formats we can both decode AND encode on iOS.
// ---------------------------------------------------------------------------

/**
 * FLAC codec using iOS's AVAssetReader (decode) and AVAssetWriter (encode).
 */
internal class IosFlacCodec : AudioCodec {

  override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.FLAC)
  override val encodableFormats: Set<AudioFormat> = setOf(AudioFormat.FLAC)

  override fun sniff(data: ByteArray): AudioFormat? {
    if (data.size < 4) return null
    // FLAC stream marker: "fLaC"
    if (data[0] == 0x66.toByte() && data[1] == 0x4C.toByte() &&
      data[2] == 0x61.toByte() && data[3] == 0x43.toByte()
    ) {
      return AudioFormat.FLAC
    }
    return null
  }

  override suspend fun decode(source: ByteArray, options: AudioDecodeOptions, context: TransmuteContext): AudioIR =
    decodeWithAssetReader(source, AudioFormat.FLAC, context)

  override suspend fun encode(ir: AudioIR, context: TransmuteContext): ByteArray =
    encodeFlacWithAssetWriter(ir, context)
}

/**
 * AAC codec using iOS's AVAssetReader (decode) and AVAssetWriter (encode).
 */
internal class IosAacCodec : AudioCodec {

  override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.AAC)
  override val encodableFormats: Set<AudioFormat> = setOf(AudioFormat.AAC)

  override fun sniff(data: ByteArray): AudioFormat? {
    if (data.size < 2) return null
    if ((data[0].toInt() and 0xFF) == 0xFF && (data[1].toInt() and 0xF0) == 0xF0) {
      return AudioFormat.AAC
    }
    return null
  }

  override suspend fun decode(source: ByteArray, options: AudioDecodeOptions, context: TransmuteContext): AudioIR =
    decodeWithAssetReader(source, AudioFormat.AAC, context)

  override suspend fun encode(ir: AudioIR, context: TransmuteContext): ByteArray =
    encodeWithAssetWriter(ir, context)
}

/**
 * M4A codec using iOS's AVAssetReader (decode) and AVAssetWriter (encode).
 */
internal class IosM4aCodec : AudioCodec {

  override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.M4A)
  override val encodableFormats: Set<AudioFormat> = setOf(AudioFormat.M4A)

  override fun sniff(data: ByteArray): AudioFormat? {
    if (data.size < 12) return null
    val ftyp = data.sliceArray(4..7)
    if (ftyp.decodeToString() != "ftyp") return null

    val brand = data.sliceArray(8..11).decodeToString()
    if (brand == "M4A " || brand == "M4B " || brand == "M4P " || brand == "M4V ") return AudioFormat.M4A

    // Avoid misclassifying MP4 video as M4A if we see a video marker early.
    val window = data.copyOfRange(0, minOf(data.size, 256 * 1024)).decodeToString()
    val hasVideo = window.contains("vide") || window.contains("avc1") || window.contains("hvc1")
    if (hasVideo) return null

    return AudioFormat.M4A
  }

  override suspend fun decode(source: ByteArray, options: AudioDecodeOptions, context: TransmuteContext): AudioIR =
    decodeWithAssetReader(source, AudioFormat.M4A, context)

  override suspend fun encode(ir: AudioIR, context: TransmuteContext): ByteArray =
    encodeWithAssetWriter(ir, context)
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private class FloatArrayList(initialCapacity: Int = 16) {
  private var data = FloatArray(initialCapacity)
  private var size = 0

  fun addAll(values: FloatArray) {
    ensureCapacity(size + values.size)
    values.copyInto(data, destinationOffset = size)
    size += values.size
  }

  private fun ensureCapacity(capacity: Int) {
    if (capacity <= data.size) return
    var newSize = data.size
    while (newSize < capacity) newSize *= 2
    data = data.copyOf(newSize)
  }

  fun toFloatArray(): FloatArray = data.copyOf(size)
}
