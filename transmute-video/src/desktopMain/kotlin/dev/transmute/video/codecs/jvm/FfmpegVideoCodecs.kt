package dev.transmute.video.codecs.jvm

import dev.transmute.core.Bytes
import dev.transmute.core.TransmuteContext
import dev.transmute.core.asBytes
import dev.transmute.video.VideoCodec
import dev.transmute.video.VideoDecodeOptions
import dev.transmute.video.VideoEncodeOptions
import dev.transmute.video.VideoFormat
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
  override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.Mp4)
  override val encodableFormats: Set<VideoFormat> = setOf(VideoFormat.Mp4)

  override fun sniff(data: Bytes): VideoFormat? {
    val bytes = data.data
    if (bytes.size < 12) return null
    if (bytes[4] != 0x66.toByte() || bytes[5] != 0x74.toByte() ||
      bytes[6] != 0x79.toByte() || bytes[7] != 0x70.toByte()) return null
    val brand = (8 until 12).map { bytes[it].toInt().toChar() }.joinToString("")
    return when {
      brand.startsWith("mp4") || brand == "isom" || brand == "M4V " ||
        brand == "avc1" || brand == "iso2" || brand == "iso5" ||
        brand == "iso6" || brand == "mmp4" -> VideoFormat.Mp4
      brand.startsWith("3gp") || brand.startsWith("3g2") -> VideoFormat.Mp4
      else -> null
    }
  }

  override suspend fun decode(source: Bytes, options: VideoDecodeOptions, context: TransmuteContext): VideoIR =
    FfmpegVideoEngine.decode(source.data, "mp4", context)

  override suspend fun encode(
    ir: VideoIR,
    format: VideoFormat,
    options: VideoEncodeOptions,
    context: TransmuteContext,
  ): Bytes {
    require(format == VideoFormat.Mp4) { "JvmMp4Codec only supports MP4, got $format" }
    return FfmpegVideoEngine.encode(
      ir,
      videoCodec = "libx264",
      audioCodec = "aac",
      format = "mp4",
      ext = "mp4",
      extraArgs = listOf("-movflags", "+faststart"),
      context = context,
    ).asBytes()
  }
}

// --- MOV (H.264 + AAC) ---

internal class JvmMovCodec : VideoCodec {
  override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.Mov)
  override val encodableFormats: Set<VideoFormat> = setOf(VideoFormat.Mov)

  override fun sniff(data: Bytes): VideoFormat? {
    val bytes = data.data
    if (bytes.size < 12) return null
    if (bytes[4] != 0x66.toByte() || bytes[5] != 0x74.toByte() ||
      bytes[6] != 0x79.toByte() || bytes[7] != 0x70.toByte()) return null
    val brand = (8 until 12).map { bytes[it].toInt().toChar() }.joinToString("")
    return if (brand == "qt  ") VideoFormat.Mov else null
  }

  override suspend fun decode(source: Bytes, options: VideoDecodeOptions, context: TransmuteContext): VideoIR =
    FfmpegVideoEngine.decode(source.data, "mov", context)

  override suspend fun encode(
    ir: VideoIR,
    format: VideoFormat,
    options: VideoEncodeOptions,
    context: TransmuteContext,
  ): Bytes {
    require(format == VideoFormat.Mov) { "JvmMovCodec only supports MOV, got $format" }
    return FfmpegVideoEngine.encode(
      ir,
      videoCodec = "libx264",
      audioCodec = "aac",
      format = "mov",
      ext = "mov",
      context = context,
    ).asBytes()
  }
}

// --- WebM (VP8 + Vorbis) ---

internal class JvmWebmCodec : VideoCodec {
  override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.Webm)
  override val encodableFormats: Set<VideoFormat> = setOf(VideoFormat.Webm)

  override fun sniff(data: Bytes): VideoFormat? {
    val bytes = data.data
    if (bytes.size < 4) return null
    if (bytes[0] != 0x1A.toByte() || bytes[1] != 0x45.toByte() ||
      bytes[2] != 0xDF.toByte() || bytes[3] != 0xA3.toByte()) return null
    // Check doctype in EBML header
    if (bytes.size >= 40) {
      val content = bytes.copyOfRange(0, minOf(bytes.size, 64)).decodeToString()
      if (content.contains("matroska")) return null // MKV, not WebM
      if (content.contains("webm")) return VideoFormat.Webm
    }
    // Short EBML data without identifiable doctype - assume WebM (more common)
    return VideoFormat.Webm
  }

  override suspend fun decode(source: Bytes, options: VideoDecodeOptions, context: TransmuteContext): VideoIR =
    FfmpegVideoEngine.decode(source.data, "webm", context)

  override suspend fun encode(
    ir: VideoIR,
    format: VideoFormat,
    options: VideoEncodeOptions,
    context: TransmuteContext,
  ): Bytes {
    require(format == VideoFormat.Webm) { "JvmWebmCodec only supports WEBM, got $format" }
    return FfmpegVideoEngine.encode(
      ir,
      videoCodec = "libvpx",
      audioCodec = "libvorbis",
      format = "webm",
      ext = "webm",
      context = context,
    ).asBytes()
  }
}

// --- AVI (MPEG-4 + MP3) ---

internal class JvmAviCodec : VideoCodec {
  override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.Avi)
  override val encodableFormats: Set<VideoFormat> = setOf(VideoFormat.Avi)

  override fun sniff(data: Bytes): VideoFormat? {
    val bytes = data.data
    if (bytes.size < 12) return null
    if (bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
      bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
      bytes[8] == 'A'.code.toByte() && bytes[9] == 'V'.code.toByte() &&
      bytes[10] == 'I'.code.toByte() && bytes[11] == ' '.code.toByte()) return VideoFormat.Avi
    return null
  }

  override suspend fun decode(source: Bytes, options: VideoDecodeOptions, context: TransmuteContext): VideoIR =
    FfmpegVideoEngine.decode(source.data, "avi", context)

  override suspend fun encode(
    ir: VideoIR,
    format: VideoFormat,
    options: VideoEncodeOptions,
    context: TransmuteContext,
  ): Bytes {
    require(format == VideoFormat.Avi) { "JvmAviCodec only supports AVI, got $format" }
    return FfmpegVideoEngine.encode(
      ir,
      videoCodec = "mpeg4",
      audioCodec = "mp3",
      format = "avi",
      ext = "avi",
      context = context,
    ).asBytes()
  }
}

// --- MKV / Matroska (H.264 + AAC) ---

internal class JvmMkvCodec : VideoCodec {
  override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.Mkv)
  override val encodableFormats: Set<VideoFormat> = setOf(VideoFormat.Mkv)

  override fun sniff(data: Bytes): VideoFormat? {
    val bytes = data.data
    if (bytes.size < 4) return null
    if (bytes[0] != 0x1A.toByte() || bytes[1] != 0x45.toByte() ||
      bytes[2] != 0xDF.toByte() || bytes[3] != 0xA3.toByte()) return null
    if (bytes.size >= 40) {
      val content = bytes.copyOfRange(0, minOf(bytes.size, 64)).decodeToString()
      if (content.contains("matroska")) return VideoFormat.Mkv
    }
    return null
  }

  override suspend fun decode(source: Bytes, options: VideoDecodeOptions, context: TransmuteContext): VideoIR =
    FfmpegVideoEngine.decode(source.data, "mkv", context)

  override suspend fun encode(
    ir: VideoIR,
    format: VideoFormat,
    options: VideoEncodeOptions,
    context: TransmuteContext,
  ): Bytes {
    require(format == VideoFormat.Mkv) { "JvmMkvCodec only supports MKV, got $format" }
    return FfmpegVideoEngine.encode(
      ir,
      videoCodec = "libx264",
      audioCodec = "aac",
      format = "matroska",
      ext = "mkv",
      context = context,
    ).asBytes()
  }
}
