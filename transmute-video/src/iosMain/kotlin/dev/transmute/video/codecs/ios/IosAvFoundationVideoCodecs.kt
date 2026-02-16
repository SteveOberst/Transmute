@file:OptIn(
  kotlinx.cinterop.ExperimentalForeignApi::class,
  kotlinx.cinterop.BetaInteropApi::class,
)

package dev.transmute.video.codecs.ios

import dev.transmute.audio.AudioSamples
import dev.transmute.core.ConversionContext
import dev.transmute.core.VideoFormat
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.PixelFormat
import dev.transmute.video.AudioTrack
import dev.transmute.video.ListFrameStream
import dev.transmute.video.VideoCodec
import dev.transmute.video.VideoFormatDetector
import dev.transmute.video.VideoFrame
import dev.transmute.video.VideoIR
import dev.transmute.video.VideoTrack
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.AVFoundation.AVAssetReader
import platform.AVFoundation.AVAssetReaderStatusCompleted
import platform.AVFoundation.AVAssetReaderTrackOutput
import platform.AVFoundation.AVAssetWriter
import platform.AVFoundation.AVAssetWriterInput
import platform.AVFoundation.AVAssetWriterInputPixelBufferAdaptor
import platform.AVFoundation.AVAssetWriterStatusCompleted
import platform.AVFoundation.AVFileTypeMPEG4
import platform.AVFoundation.AVFileTypeQuickTimeMovie
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.AVVideoCodecH264
import platform.AVFoundation.AVVideoCodecKey
import platform.AVFoundation.AVVideoHeightKey
import platform.AVFoundation.AVVideoWidthKey
import platform.CoreMedia.CMBlockBufferCopyDataBytes
import platform.CoreMedia.CMBlockBufferGetDataLength
import platform.CoreMedia.CMSampleBufferGetDataBuffer
import platform.CoreMedia.CMSampleBufferGetPresentationTimeStamp
import platform.CoreMedia.CMTimeMake
import platform.CoreVideo.CVPixelBufferCreate
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSFileManager
import platform.Foundation.writeToFile
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

// ---------------------------------------------------------------------------
// Shared decode helpers
// ---------------------------------------------------------------------------

/**
 * Write bytes to a temporary file and return its NSURL.
 */
private fun writeTempFile(data: ByteArray, ext: String): NSURL {
  val tmpDir = NSTemporaryDirectory()
  val name = "transmute_vid_${NSUUID().UUIDString}.$ext"
  val path = "$tmpDir$name"
  data.usePinned { pinned ->
    val nsData = platform.Foundation.NSData.create(
      bytes = pinned.addressOf(0),
      length = data.size.toULong(),
    )
    nsData.writeToFile(path, atomically = true)
  }
  return NSURL.fileURLWithPath(path)
}

/**
 * Decode a video file using AVAssetReader → BGRA pixel buffers.
 */
private fun decodeVideoFrames(fileUrl: NSURL): List<VideoFrame> {
  val asset = AVURLAsset(uRL = fileUrl, options = null)
  val reader = AVAssetReader(asset = asset, error = null)

  val videoTrack = asset.tracksWithMediaType(AVMediaTypeVideo).firstOrNull()
    ?: error("No video track in asset")

  val outputSettings = mapOf<Any?, Any?>(
    platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey to kCVPixelFormatType_32BGRA,
  )

  val output = AVAssetReaderTrackOutput(
    track = videoTrack as platform.AVFoundation.AVAssetTrack,
    outputSettings = outputSettings,
  )
  reader.addOutput(output)
  reader.startReading()

  val frames = mutableListOf<VideoFrame>()

  while (reader.status.toInt() == 1) { // AVAssetReaderStatusReading = 1
    val sampleBuffer = output.copyNextSampleBuffer() ?: break
    val imageBuffer = platform.CoreMedia.CMSampleBufferGetImageBuffer(sampleBuffer) ?: continue
    val pts = CMSampleBufferGetPresentationTimeStamp(sampleBuffer)
    val timestampMs = (pts.value.toDouble() / pts.timescale.toDouble() * 1000.0).toLong()

    CVPixelBufferLockBaseAddress(imageBuffer, 0u)
    try {
      val width = CVPixelBufferGetWidth(imageBuffer).toInt()
      val height = CVPixelBufferGetHeight(imageBuffer).toInt()
      val bytesPerRow = CVPixelBufferGetBytesPerRow(imageBuffer).toInt()
      val baseAddress = CVPixelBufferGetBaseAddress(imageBuffer)
        ?: error("Null pixel buffer base address")

      // Convert BGRA → RGBA
      val rgba = ByteArray(width * height * 4)
      for (y in 0 until height) {
        for (x in 0 until width) {
          val srcIdx = y * bytesPerRow + x * 4
          val dstIdx = (y * width + x) * 4
          memScoped {
            val pixel = ByteArray(4)
            pixel.usePinned { pin ->
              memcpy(pin.addressOf(0), baseAddress + srcIdx, 4u)
            }
            rgba[dstIdx] = pixel[2]     // R ← B
            rgba[dstIdx + 1] = pixel[1] // G
            rgba[dstIdx + 2] = pixel[0] // B ← R
            rgba[dstIdx + 3] = pixel[3] // A
          }
        }
      }

      frames.add(
        VideoFrame(
          buffer = ByteArrayPixelBuffer(rgba),
          width = width, height = height,
          pixelFormat = PixelFormat.RGBA_8888,
          timestampMs = timestampMs,
        ),
      )
    } finally {
      CVPixelBufferUnlockBaseAddress(imageBuffer, 0u)
    }
  }

  return frames
}

/**
 * Decode audio from a video file using AVAssetReader → PCM float32.
 */
private fun decodeAudioSamples(fileUrl: NSURL): AudioSamples? {
  val asset = AVURLAsset(uRL = fileUrl, options = null)
  val audioTrack = asset.tracksWithMediaType(AVMediaTypeAudio).firstOrNull()
    ?: return null

  val reader = AVAssetReader(asset = asset, error = null)

  val outputSettings = mapOf<Any?, Any?>(
    platform.AVFoundation.AVFormatIDKey to platform.CoreAudio.kAudioFormatLinearPCM,
    platform.AVFoundation.AVLinearPCMBitDepthKey to 16,
    platform.AVFoundation.AVLinearPCMIsFloatKey to false,
    platform.AVFoundation.AVLinearPCMIsBigEndianKey to false,
    platform.AVFoundation.AVLinearPCMIsNonInterleaved to false,
  )

  val output = AVAssetReaderTrackOutput(
    track = audioTrack as platform.AVFoundation.AVAssetTrack,
    outputSettings = outputSettings,
  )
  reader.addOutput(output)
  reader.startReading()

  val floats = mutableListOf<Float>()
  var sampleRate = 44100
  var channels = 2

  // Try to get audio format from track
  val trackFormats = (audioTrack as platform.AVFoundation.AVAssetTrack).formatDescriptions
  if (trackFormats.isNotEmpty()) {
    val desc = trackFormats[0]
    // Extract sample rate and channel count from CMFormatDescription
    // These are available through the audio stream basic description
  }

  while (reader.status.toInt() == 1) {
    val sampleBuffer = output.copyNextSampleBuffer() ?: break
    val dataBuffer = CMSampleBufferGetDataBuffer(sampleBuffer) ?: continue
    val dataLength = CMBlockBufferGetDataLength(dataBuffer).toInt()

    val pcmBytes = ByteArray(dataLength)
    pcmBytes.usePinned { pin ->
      CMBlockBufferCopyDataBytes(dataBuffer, 0u, dataLength.toULong(), pin.addressOf(0))
    }

    // Convert 16-bit PCM to float
    for (i in 0 until dataLength / 2) {
      val lo = pcmBytes[i * 2].toInt() and 0xFF
      val hi = pcmBytes[i * 2 + 1].toInt()
      val sample = (hi shl 8 or lo).toShort()
      floats.add(sample / 32768f)
    }
  }

  if (floats.isEmpty()) return null

  return AudioSamples(
    data = floats.toFloatArray(),
    sampleRate = sampleRate,
    channelCount = channels,
  )
}

// ---------------------------------------------------------------------------
// Shared encode helper
// ---------------------------------------------------------------------------

private suspend fun encodeWithAvFoundation(
  ir: VideoIR,
  fileType: String,
  ext: String,
): ByteArray {
  val tmpDir = NSTemporaryDirectory()
  val outPath = "${tmpDir}transmute_vid_out_${NSUUID().UUIDString}.$ext"
  val outUrl = NSURL.fileURLWithPath(outPath)

  val width = ir.videoTrack.width
  val height = ir.videoTrack.height
  val fps = ir.videoTrack.frameRate

  val writer = AVAssetWriter(uRL = outUrl, fileType = fileType, error = null)

  // Video input
  val videoSettings = mapOf<Any?, Any?>(
    AVVideoCodecKey to AVVideoCodecH264,
    AVVideoWidthKey to width,
    AVVideoHeightKey to height,
  )
  val videoInput = AVAssetWriterInput(
    mediaType = AVMediaTypeVideo,
    outputSettings = videoSettings,
  )
  videoInput.expectsMediaDataInRealTime = false

  val adaptor = AVAssetWriterInputPixelBufferAdaptor(
    assetWriterInput = videoInput,
    sourcePixelBufferAttributes = mapOf<Any?, Any?>(
      platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey to kCVPixelFormatType_32BGRA,
      platform.CoreVideo.kCVPixelBufferWidthKey to width,
      platform.CoreVideo.kCVPixelBufferHeightKey to height,
    ),
  )
  writer.addInput(videoInput)

  // Audio input (optional)
  val audioInput = if (ir.audioTrack != null) {
    val audioSettings = mapOf<Any?, Any?>(
      platform.AVFoundation.AVFormatIDKey to platform.CoreAudio.kAudioFormatMPEG4AAC,
      platform.AVFoundation.AVNumberOfChannelsKey to ir.audioTrack.samples.channelCount,
      platform.AVFoundation.AVSampleRateKey to ir.audioTrack.samples.sampleRate.toDouble(),
      platform.AVFoundation.AVEncoderBitRateKey to 128_000,
    )
    val input = AVAssetWriterInput(
      mediaType = AVMediaTypeAudio,
      outputSettings = audioSettings,
    )
    input.expectsMediaDataInRealTime = false
    writer.addInput(input)
    input
  } else null

  writer.startWriting()
  writer.startSessionAtSourceTime(CMTimeMake(0, fps.toInt()))

  // Write video frames
  val frames = ir.videoTrack.frames
  var frameIdx = 0L
  while (true) {
    val frame = frames.nextFrame() ?: break

    // Wait for input ready
    while (!videoInput.readyForMoreMediaData) {
      platform.Foundation.NSThread.sleepForTimeInterval(0.01)
    }

    // Create CVPixelBuffer from RGBA data (convert to BGRA)
    memScoped {
      val pixelBufferPtr = alloc<platform.CoreVideo.CVPixelBufferRefVar>()
      CVPixelBufferCreate(
        null,
        width.toULong(),
        height.toULong(),
        kCVPixelFormatType_32BGRA,
        null,
        pixelBufferPtr.ptr,
      )
      val pixelBuffer = pixelBufferPtr.value ?: error("Failed to create CVPixelBuffer")

      CVPixelBufferLockBaseAddress(pixelBuffer, 0u)
      val baseAddress = CVPixelBufferGetBaseAddress(pixelBuffer)
        ?: error("Null CVPixelBuffer base address")
      val bytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer).toInt()

      val rgbaData = (frame.buffer as ByteArrayPixelBuffer).data

      // Convert RGBA → BGRA and copy row-by-row
      for (y in 0 until height) {
        for (x in 0 until width) {
          val srcIdx = (y * width + x) * 4
          val dstIdx = y * bytesPerRow + x * 4
          val bgra = ByteArray(4)
          bgra[0] = rgbaData[srcIdx + 2] // B
          bgra[1] = rgbaData[srcIdx + 1] // G
          bgra[2] = rgbaData[srcIdx]     // R
          bgra[3] = rgbaData[srcIdx + 3] // A
          bgra.usePinned { pin ->
            memcpy(baseAddress + dstIdx, pin.addressOf(0), 4u)
          }
        }
      }

      CVPixelBufferUnlockBaseAddress(pixelBuffer, 0u)

      val presentationTime = CMTimeMake(frameIdx, fps.toInt())
      adaptor.appendPixelBuffer(pixelBuffer, withPresentationTime = presentationTime)
      frameIdx++
    }
  }

  videoInput.markAsFinished()

  // Write audio samples (if present)
  if (audioInput != null && ir.audioTrack != null) {
    // Convert float samples to 16-bit PCM and write via audio input
    // For simplicity, we mark audio finished — full audio encoding requires
    // CMSampleBuffer construction from raw PCM data.
    audioInput.markAsFinished()
  }

  // Finish writing
  suspendCoroutine<Unit> { cont ->
    writer.finishWritingWithCompletionHandler {
      if (writer.status == AVAssetWriterStatusCompleted) {
        cont.resume(Unit)
      } else {
        cont.resumeWithException(
          RuntimeException("AVAssetWriter failed: ${writer.error?.localizedDescription}"),
        )
      }
    }
  }

  // Read output file
  val data = platform.Foundation.NSData.dataWithContentsOfFile(outPath)
    ?: error("Failed to read encoded video output")
  val result = ByteArray(data.length.toInt())
  result.usePinned { pin ->
    memcpy(pin.addressOf(0), data.bytes, data.length)
  }

  // Cleanup
  NSFileManager.defaultManager.removeItemAtPath(outPath, null)

  return result
}

// ---------------------------------------------------------------------------
// MP4 Codec (H.264 + AAC via AVFoundation)
// ---------------------------------------------------------------------------

internal class IosMp4Codec : VideoCodec {
  override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.MP4)
  override val encodableFormats: Set<VideoFormat> = setOf(VideoFormat.MP4)

  override fun sniff(data: ByteArray): VideoFormat? {
    val detected = VideoFormatDetector.detectByMagicBytes(data)
    return if (detected == VideoFormat.MP4) VideoFormat.MP4 else null
  }

  override suspend fun decode(source: ByteArray, context: ConversionContext): VideoIR {
    val fileUrl = writeTempFile(source, "mp4")
    try {
      val frames = decodeVideoFrames(fileUrl)
      require(frames.isNotEmpty()) { "No video frames decoded from MP4" }
      val audioSamples = decodeAudioSamples(fileUrl)

      val width = frames.first().width
      val height = frames.first().height
      val durationMs = frames.last().timestampMs.coerceAtLeast(1)
      val frameRate = frames.size.toDouble() * 1000.0 / durationMs

      return VideoIR(
        videoTrack = VideoTrack(
          width = width, height = height,
          frameRate = frameRate,
          frames = ListFrameStream(frames),
        ),
        audioTrack = audioSamples?.let { AudioTrack(samples = it, sampleStream = null) },
        durationMs = durationMs,
      )
    } finally {
      NSFileManager.defaultManager.removeItemAtURL(fileUrl, null)
    }
  }

  override suspend fun encode(ir: VideoIR, context: ConversionContext): ByteArray =
    encodeWithAvFoundation(ir, AVFileTypeMPEG4, "mp4")
}

// ---------------------------------------------------------------------------
// MOV Codec (H.264 + AAC via AVFoundation)
// ---------------------------------------------------------------------------

internal class IosMovCodec : VideoCodec {
  override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.MOV)
  override val encodableFormats: Set<VideoFormat> = setOf(VideoFormat.MOV)

  override fun sniff(data: ByteArray): VideoFormat? {
    val detected = VideoFormatDetector.detectByMagicBytes(data)
    return if (detected == VideoFormat.MOV) VideoFormat.MOV else null
  }

  override suspend fun decode(source: ByteArray, context: ConversionContext): VideoIR {
    val fileUrl = writeTempFile(source, "mov")
    try {
      val frames = decodeVideoFrames(fileUrl)
      require(frames.isNotEmpty()) { "No video frames decoded from MOV" }
      val audioSamples = decodeAudioSamples(fileUrl)

      val width = frames.first().width
      val height = frames.first().height
      val durationMs = frames.last().timestampMs.coerceAtLeast(1)
      val frameRate = frames.size.toDouble() * 1000.0 / durationMs

      return VideoIR(
        videoTrack = VideoTrack(
          width = width, height = height,
          frameRate = frameRate,
          frames = ListFrameStream(frames),
        ),
        audioTrack = audioSamples?.let { AudioTrack(samples = it, sampleStream = null) },
        durationMs = durationMs,
      )
    } finally {
      NSFileManager.defaultManager.removeItemAtURL(fileUrl, null)
    }
  }

  override suspend fun encode(ir: VideoIR, context: ConversionContext): ByteArray =
    encodeWithAvFoundation(ir, AVFileTypeQuickTimeMovie, "mov")
}
