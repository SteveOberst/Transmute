package dev.transmute.video.codecs.jvm

import dev.transmute.core.TransmuteContext
import dev.transmute.core.VideoFormat
import dev.transmute.video.VideoCodec
import dev.transmute.video.VideoDecodeOptions
import dev.transmute.video.VideoEncodeOptions
import dev.transmute.video.VideoIR

// ---------------------------------------------------------------------------
// FFmpeg-backed video codecs for JVM/Desktop.
//
// Each codec shells out to ffmpeg for both decode and encode.
// All are gated on [FfmpegVideoEngine.available]; registration is skipped
// in [PlatformVideoCodecs] when FFmpeg is absent.
// ---------------------------------------------------------------------------

// --- MP4 (H.264 + AAC) ---

internal class JvmMp4Codec : VideoCodec {
  override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.MP4)
  override val encodableFormats: Set<VideoFormat> = setOf(VideoFormat.MP4)

  override fun sniff(data: ByteArray): VideoFormat? {
    if (data.size < 12) return null
    if (data[4] != 0x66.toByte() || data[5] != 0x74.toByte() ||
      data[6] != 0x79.toByte() || data[7] != 0x70.toByte()) return null
    val brand = (8 until 12).map { data[it].toInt().toChar() }.joinToString("")
    return when {
      brand.startsWith("mp4") || brand == "isom" || brand == "M4V " ||
        brand == "avc1" || brand == "iso2" || brand == "iso5" ||
        brand == "iso6" || brand == "mmp4" -> VideoFormat.MP4
      brand.startsWith("3gp") || brand.startsWith("3g2") -> VideoFormat.MP4
      else -> null
    }
  }

  override suspend fun decode(source: ByteArray, options: VideoDecodeOptions, context: TransmuteContext): VideoIR =
    FfmpegVideoEngine.decode(source, "mp4", context)

  override suspend fun encode(
    ir: VideoIR,
    format: VideoFormat,
    options: VideoEncodeOptions,
    context: TransmuteContext,
  ): ByteArray {
    require(format == VideoFormat.MP4) { "JvmMp4Codec only supports MP4, got $format" }
    return FfmpegVideoEngine.encode(
      ir,
      videoCodec = "libx264",
      audioCodec = "aac",
      format = "mp4",
      ext = "mp4",
      extraArgs = listOf("-movflags", "+faststart"),
      context = context,
    )
  }
}

// --- MOV (H.264 + AAC) ---

internal class JvmMovCodec : VideoCodec {
  override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.MOV)
  override val encodableFormats: Set<VideoFormat> = setOf(VideoFormat.MOV)

  override fun sniff(data: ByteArray): VideoFormat? {
    if (data.size < 12) return null
    if (data[4] != 0x66.toByte() || data[5] != 0x74.toByte() ||
      data[6] != 0x79.toByte() || data[7] != 0x70.toByte()) return null
    val brand = (8 until 12).map { data[it].toInt().toChar() }.joinToString("")
    return if (brand == "qt  ") VideoFormat.MOV else null
  }

  override suspend fun decode(source: ByteArray, options: VideoDecodeOptions, context: TransmuteContext): VideoIR =
    FfmpegVideoEngine.decode(source, "mov", context)

  override suspend fun encode(
    ir: VideoIR,
    format: VideoFormat,
    options: VideoEncodeOptions,
    context: TransmuteContext,
  ): ByteArray {
    require(format == VideoFormat.MOV) { "JvmMovCodec only supports MOV, got $format" }
    return FfmpegVideoEngine.encode(
      ir,
      videoCodec = "libx264",
      audioCodec = "aac",
      format = "mov",
      ext = "mov",
      context = context,
    )
  }
}

// --- WebM (VP8 + Vorbis) ---

internal class JvmWebmCodec : VideoCodec {
  override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.WEBM)
  override val encodableFormats: Set<VideoFormat> = setOf(VideoFormat.WEBM)

  override fun sniff(data: ByteArray): VideoFormat? {
    if (data.size < 4) return null
    if (data[0] != 0x1A.toByte() || data[1] != 0x45.toByte() ||
      data[2] != 0xDF.toByte() || data[3] != 0xA3.toByte()) return null
    // Check doctype in EBML header
    if (data.size >= 40) {
      val content = data.copyOfRange(0, minOf(data.size, 64)).decodeToString()
      if (content.contains("matroska")) return null // MKV, not WebM
      if (content.contains("webm")) return VideoFormat.WEBM
    }
    // Short EBML data without identifiable doctype - assume WebM (more common)
    return VideoFormat.WEBM
  }

  override suspend fun decode(source: ByteArray, options: VideoDecodeOptions, context: TransmuteContext): VideoIR =
    FfmpegVideoEngine.decode(source, "webm", context)

  override suspend fun encode(
    ir: VideoIR,
    format: VideoFormat,
    options: VideoEncodeOptions,
    context: TransmuteContext,
  ): ByteArray {
    require(format == VideoFormat.WEBM) { "JvmWebmCodec only supports WEBM, got $format" }
    return FfmpegVideoEngine.encode(
      ir,
      videoCodec = "libvpx",
      audioCodec = "libvorbis",
      format = "webm",
      ext = "webm",
      context = context,
    )
  }
}

// --- AVI (MPEG-4 + MP3) ---

internal class JvmAviCodec : VideoCodec {
  override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.AVI)
  override val encodableFormats: Set<VideoFormat> = setOf(VideoFormat.AVI)

  override fun sniff(data: ByteArray): VideoFormat? {
    if (data.size < 12) return null
    if (data[0] == 'R'.code.toByte() && data[1] == 'I'.code.toByte() &&
      data[2] == 'F'.code.toByte() && data[3] == 'F'.code.toByte() &&
      data[8] == 'A'.code.toByte() && data[9] == 'V'.code.toByte() &&
      data[10] == 'I'.code.toByte() && data[11] == ' '.code.toByte()) return VideoFormat.AVI
    return null
  }

  override suspend fun decode(source: ByteArray, options: VideoDecodeOptions, context: TransmuteContext): VideoIR =
    FfmpegVideoEngine.decode(source, "avi", context)

  override suspend fun encode(
    ir: VideoIR,
    format: VideoFormat,
    options: VideoEncodeOptions,
    context: TransmuteContext,
  ): ByteArray {
    require(format == VideoFormat.AVI) { "JvmAviCodec only supports AVI, got $format" }
    return FfmpegVideoEngine.encode(
      ir,
      videoCodec = "mpeg4",
      audioCodec = "mp3",
      format = "avi",
      ext = "avi",
      context = context,
    )
  }
}

// --- MKV / Matroska (H.264 + AAC) ---

internal class JvmMkvCodec : VideoCodec {
  override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.MKV)
  override val encodableFormats: Set<VideoFormat> = setOf(VideoFormat.MKV)

  override fun sniff(data: ByteArray): VideoFormat? {
    if (data.size < 4) return null
    if (data[0] != 0x1A.toByte() || data[1] != 0x45.toByte() ||
      data[2] != 0xDF.toByte() || data[3] != 0xA3.toByte()) return null
    if (data.size >= 40) {
      val content = data.copyOfRange(0, minOf(data.size, 64)).decodeToString()
      if (content.contains("matroska")) return VideoFormat.MKV
    }
    return null
  }

  override suspend fun decode(source: ByteArray, options: VideoDecodeOptions, context: TransmuteContext): VideoIR =
    FfmpegVideoEngine.decode(source, "mkv", context)

  override suspend fun encode(
    ir: VideoIR,
    format: VideoFormat,
    options: VideoEncodeOptions,
    context: TransmuteContext,
  ): ByteArray {
    require(format == VideoFormat.MKV) { "JvmMkvCodec only supports MKV, got $format" }
    return FfmpegVideoEngine.encode(
      ir,
      videoCodec = "libx264",
      audioCodec = "aac",
      format = "matroska",
      ext = "mkv",
      context = context,
    )
  }
}
