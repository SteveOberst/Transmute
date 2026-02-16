package dev.transmute.video.codecs.jvm

import dev.transmute.core.ConversionContext
import dev.transmute.core.VideoFormat
import dev.transmute.video.VideoCodec
import dev.transmute.video.VideoFormatDetector
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
    val detected = VideoFormatDetector.detectByMagicBytes(data)
    return if (detected == VideoFormat.MP4) VideoFormat.MP4 else null
  }

  override suspend fun decode(source: ByteArray, context: ConversionContext): VideoIR =
    FfmpegVideoEngine.decode(source, "mp4", context)

  override suspend fun encode(ir: VideoIR, context: ConversionContext): ByteArray =
    FfmpegVideoEngine.encode(
      ir,
      videoCodec = "libx264",
      audioCodec = "aac",
      format = "mp4",
      ext = "mp4",
      extraArgs = listOf("-movflags", "+faststart"),
      context = context,
    )
}

// --- MOV (H.264 + AAC) ---

internal class JvmMovCodec : VideoCodec {
  override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.MOV)
  override val encodableFormats: Set<VideoFormat> = setOf(VideoFormat.MOV)

  override fun sniff(data: ByteArray): VideoFormat? {
    val detected = VideoFormatDetector.detectByMagicBytes(data)
    return if (detected == VideoFormat.MOV) VideoFormat.MOV else null
  }

  override suspend fun decode(source: ByteArray, context: ConversionContext): VideoIR =
    FfmpegVideoEngine.decode(source, "mov", context)

  override suspend fun encode(ir: VideoIR, context: ConversionContext): ByteArray =
    FfmpegVideoEngine.encode(
      ir,
      videoCodec = "libx264",
      audioCodec = "aac",
      format = "mov",
      ext = "mov",
      context = context,
    )
}

// --- WebM (VP8 + Vorbis) ---

internal class JvmWebmCodec : VideoCodec {
  override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.WEBM)
  override val encodableFormats: Set<VideoFormat> = setOf(VideoFormat.WEBM)

  override fun sniff(data: ByteArray): VideoFormat? {
    val detected = VideoFormatDetector.detectByMagicBytes(data)
    return if (detected == VideoFormat.WEBM) VideoFormat.WEBM else null
  }

  override suspend fun decode(source: ByteArray, context: ConversionContext): VideoIR =
    FfmpegVideoEngine.decode(source, "webm", context)

  override suspend fun encode(ir: VideoIR, context: ConversionContext): ByteArray =
    FfmpegVideoEngine.encode(
      ir,
      videoCodec = "libvpx",
      audioCodec = "libvorbis",
      format = "webm",
      ext = "webm",
      context = context,
    )
}

// --- AVI (MPEG-4 + MP3) ---

internal class JvmAviCodec : VideoCodec {
  override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.AVI)
  override val encodableFormats: Set<VideoFormat> = setOf(VideoFormat.AVI)

  override fun sniff(data: ByteArray): VideoFormat? {
    val detected = VideoFormatDetector.detectByMagicBytes(data)
    return if (detected == VideoFormat.AVI) VideoFormat.AVI else null
  }

  override suspend fun decode(source: ByteArray, context: ConversionContext): VideoIR =
    FfmpegVideoEngine.decode(source, "avi", context)

  override suspend fun encode(ir: VideoIR, context: ConversionContext): ByteArray =
    FfmpegVideoEngine.encode(
      ir,
      videoCodec = "mpeg4",
      audioCodec = "mp3",
      format = "avi",
      ext = "avi",
      context = context,
    )
}

// --- MKV / Matroska (H.264 + AAC) ---

internal class JvmMkvCodec : VideoCodec {
  override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.MKV)
  override val encodableFormats: Set<VideoFormat> = setOf(VideoFormat.MKV)

  override fun sniff(data: ByteArray): VideoFormat? {
    val detected = VideoFormatDetector.detectByMagicBytes(data)
    return if (detected == VideoFormat.MKV) VideoFormat.MKV else null
  }

  override suspend fun decode(source: ByteArray, context: ConversionContext): VideoIR =
    FfmpegVideoEngine.decode(source, "mkv", context)

  override suspend fun encode(ir: VideoIR, context: ConversionContext): ByteArray =
    FfmpegVideoEngine.encode(
      ir,
      videoCodec = "libx264",
      audioCodec = "aac",
      format = "matroska",
      ext = "mkv",
      context = context,
    )
}
