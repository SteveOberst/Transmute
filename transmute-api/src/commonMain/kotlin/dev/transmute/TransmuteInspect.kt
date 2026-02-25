package dev.transmute

import dev.transmute.audio.AudioFormat
import dev.transmute.codec.DecodeRange
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.MediaFormat
import dev.transmute.codec.TimeRangeMs
import dev.transmute.model.core.UnknownFormat
import dev.transmute.model.core.asBytes
import dev.transmute.codec.pipeline.Decoded
import dev.transmute.codec.pipeline.EncodedBytes
import dev.transmute.image.AlphaSemantics
import dev.transmute.image.ColorInfo
import dev.transmute.image.ImageFormat
import dev.transmute.image.ImageEncodeOptions
import dev.transmute.image.ImageIR
import dev.transmute.image.PngEncodeOptions
import dev.transmute.video.CanonicalVideoDecodeOptions
import dev.transmute.video.VideoFormat

class TransmuteInspect internal constructor(
  private val codec: TransmuteCodec,
) {
  val image: InspectImage = InspectImage(codec)
  val audio: InspectAudio = InspectAudio(codec)
  val video: InspectVideo = InspectVideo(codec)

  fun detectFormat(bytes: Bytes): MediaFormat<*, *> {
    if (isBmff(bytes)) {
      val img = codec.image.detectFormat(bytes)
      if (img != ImageFormat.Unknown) return img

      val brand = bmffMajorBrand(bytes)
      if (brand == "qt  ") return VideoFormat.Mov

      val hasVideo = bmffHasVideoTrack(bytes)
      val hasAudio = bmffHasAudioTrack(bytes)
      if (hasVideo) return VideoFormat.Mp4
      if (hasAudio) return AudioFormat.M4a
    }

    val img = codec.image.detectFormat(bytes)
    if (img != ImageFormat.Unknown) return img

    val vid = codec.video.detectFormat(bytes)
    if (vid != VideoFormat.Unknown) return vid

    val aud = codec.audio.detectFormat(bytes)
    if (aud != AudioFormat.Unknown) return aud

    return UnknownFormat
  }

  fun detectFormat(bytes: ByteArray): MediaFormat<*, *> = detectFormat(bytes.asBytes())
}

class InspectImage internal constructor(
  private val codec: TransmuteCodec,
) {
  fun detectFormat(source: Bytes): ImageFormat = codec.image.detectFormat(source)
  fun detectFormat(source: ByteArray): ImageFormat = detectFormat(source.asBytes())
}

class InspectAudio internal constructor(
  private val codec: TransmuteCodec,
) {
  fun detectFormat(source: Bytes): AudioFormat = codec.audio.detectFormat(source)
  fun detectFormat(source: ByteArray): AudioFormat = detectFormat(source.asBytes())
}

class InspectVideo internal constructor(
  private val codec: TransmuteCodec,
) {
  fun detectFormat(source: Bytes): VideoFormat = codec.video.detectFormat(source)
  fun detectFormat(source: ByteArray): VideoFormat = detectFormat(source.asBytes())

  /**
   * Extract a thumbnail from the first decodable video frame and encode it using [imageEncodeOptions].
   *
   * This does not decode the full file: it requests a small decode range and stops after the first frame.
   */
  suspend fun thumbnailFirstFrame(
    source: Bytes,
    imageEncodeOptions: ImageEncodeOptions = PngEncodeOptions(),
    decodeRange: DecodeRange = TimeRangeMs(startMs = 0, endMsExclusive = 2_000),
  ): EncodedBytes<ImageFormat> {
    val opts = CanonicalVideoDecodeOptions(decodeRange = decodeRange)
    val decoded = codec.video.decode(source, opts)
    val frames = decoded.ir.videoTrack.frames
    try {
      val frame = frames.nextFrame() ?: error("No frames decoded")
      val stride = frame.width * frame.pixelFormat.bytesPerPixel
      val imageIr = ImageIR(
        buffer = frame.buffer,
        width = frame.width,
        height = frame.height,
        stride = stride,
        pixelFormat = frame.pixelFormat,
        alphaSemantics = AlphaSemantics.STRAIGHT,
        colorInfo = ColorInfo(),
      )
      return codec.image.encode(Decoded(ImageFormat.Unknown, imageIr), imageEncodeOptions)
    } finally {
      runCatching { frames.close() }
      runCatching { decoded.ir.audioTrack?.sampleStream?.close() }
    }
  }

  suspend fun thumbnailFirstFrame(
    source: ByteArray,
    imageEncodeOptions: ImageEncodeOptions = PngEncodeOptions(),
    decodeRange: DecodeRange = TimeRangeMs(startMs = 0, endMsExclusive = 2_000),
  ): EncodedBytes<ImageFormat> = thumbnailFirstFrame(source.asBytes(), imageEncodeOptions, decodeRange)
}

private fun isBmff(bytes: Bytes): Boolean =
  bytes.size >= 8 &&
    bytes.data[4] == 0x66.toByte() && bytes.data[5] == 0x74.toByte() &&
    bytes.data[6] == 0x79.toByte() && bytes.data[7] == 0x70.toByte()

private fun bmffMajorBrand(bytes: Bytes): String? {
  if (!isBmff(bytes) || bytes.size < 12) return null
  return (8 until 12).map { bytes.data[it].toInt().toChar() }.joinToString("")
}

private fun bmffHasVideoTrack(bytes: Bytes): Boolean {
  val max = 256 * 1024
  return containsAscii(bytes, "vide", max) || containsAscii(bytes, "avc1", max) || containsAscii(bytes, "hvc1", max)
}

private fun bmffHasAudioTrack(bytes: Bytes): Boolean {
  val max = 256 * 1024
  return containsAscii(bytes, "soun", max) || containsAscii(bytes, "mp4a", max)
}

private fun containsAscii(bytes: Bytes, needleAscii: String, maxBytes: Int): Boolean {
  val needle = needleAscii.encodeToByteArray()
  val limit = minOf(bytes.size, maxBytes)
  if (needle.isEmpty() || limit < needle.size) return false

  val data = bytes.data
  outer@ for (i in 0..(limit - needle.size)) {
    for (j in needle.indices) {
      if (data[i + j] != needle[j]) continue@outer
    }
    return true
  }
  return false
}
