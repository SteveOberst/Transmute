package dev.transmute.gstreamer

import dev.transmute.io.TSource
import dev.transmute.common.PipelineContext
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes
import dev.transmute.video.VideoCodec
import dev.transmute.video.VideoDecodeOptions
import dev.transmute.video.VideoEncodeOptions
import dev.transmute.video.VideoFormat
import dev.transmute.video.VideoIR

// ---------------------------------------------------------------------------
// GStreamer-backed video codecs for JVM/Desktop.
//
// Each codec delegates to GStreamerVideoEngine for both decode and encode.
// All are gated on [GStreamerVideoEngine.available]; registration is skipped
// in [GStreamerCodecInstaller] when GStreamer is absent.
// ---------------------------------------------------------------------------

// --- MP4 (H.264 + AAC) ---

internal class GstMp4Codec : VideoCodec {
    override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.Mp4)
    override val encodableFormats: Set<VideoFormat> = setOf(VideoFormat.Mp4)

    override suspend fun decode(source: TSource, options: VideoDecodeOptions, context: PipelineContext): VideoIR =
        GStreamerVideoEngine.decode(source.readAll(), "mp4", options, context)

    override suspend fun encode(
        ir: VideoIR,
        format: VideoFormat,
        options: VideoEncodeOptions,
        context: PipelineContext,
    ): Bytes {
        require(format == VideoFormat.Mp4) { "GstMp4Codec only supports MP4, got $format" }
        return GStreamerVideoEngine.encode(
            ir,
            videoEncoder = "x264enc",
            audioEncoder = aacEncoderElement(),
            muxElement = "mp4mux",
            ext = "mp4",
            extraElements = listOf("!", "h264parse"),
            context = context,
        ).asBytes()
    }
}

// --- MOV (H.264 + AAC) ---

internal class GstMovCodec : VideoCodec {
    override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.Mov)
    override val encodableFormats: Set<VideoFormat> = setOf(VideoFormat.Mov)

    override suspend fun decode(source: TSource, options: VideoDecodeOptions, context: PipelineContext): VideoIR =
        GStreamerVideoEngine.decode(source.readAll(), "mov", options, context)

    override suspend fun encode(
        ir: VideoIR,
        format: VideoFormat,
        options: VideoEncodeOptions,
        context: PipelineContext,
    ): Bytes {
        require(format == VideoFormat.Mov) { "GstMovCodec only supports MOV, got $format" }
        return GStreamerVideoEngine.encode(
            ir,
            videoEncoder = "x264enc",
            audioEncoder = aacEncoderElement(),
            muxElement = "qtmux",
            ext = "mov",
            extraElements = listOf("!", "h264parse"),
            context = context,
        ).asBytes()
    }
}

// --- WebM (VP8 + Vorbis) ---

internal class GstWebmCodec : VideoCodec {
    override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.Webm)
    override val encodableFormats: Set<VideoFormat> = setOf(VideoFormat.Webm)

    override suspend fun decode(source: TSource, options: VideoDecodeOptions, context: PipelineContext): VideoIR =
        GStreamerVideoEngine.decode(source.readAll(), "webm", options, context)

    override suspend fun encode(
        ir: VideoIR,
        format: VideoFormat,
        options: VideoEncodeOptions,
        context: PipelineContext,
    ): Bytes {
        require(format == VideoFormat.Webm) { "GstWebmCodec only supports WEBM, got $format" }
        return GStreamerVideoEngine.encode(
            ir,
            videoEncoder = "vp8enc",
            audioEncoder = "vorbisenc",
            muxElement = "webmmux",
            ext = "webm",
            context = context,
        ).asBytes()
    }
}

// --- AVI (MPEG-4 + MP3) ---

internal class GstAviCodec : VideoCodec {
    override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.Avi)
    override val encodableFormats: Set<VideoFormat> = setOf(VideoFormat.Avi)

    override suspend fun decode(source: TSource, options: VideoDecodeOptions, context: PipelineContext): VideoIR =
        GStreamerVideoEngine.decode(source.readAll(), "avi", options, context)

    override suspend fun encode(
        ir: VideoIR,
        format: VideoFormat,
        options: VideoEncodeOptions,
        context: PipelineContext,
    ): Bytes {
        require(format == VideoFormat.Avi) { "GstAviCodec only supports AVI, got $format" }
        return GStreamerVideoEngine.encode(
            ir,
            videoEncoder = "avenc_mpeg4",
            audioEncoder = "lamemp3enc",
            muxElement = "avimux",
            ext = "avi",
            context = context,
        ).asBytes()
    }
}

// --- MKV / Matroska (H.264 + AAC) ---

internal class GstMkvCodec : VideoCodec {
    override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.Mkv)
    override val encodableFormats: Set<VideoFormat> = setOf(VideoFormat.Mkv)

    override suspend fun decode(source: TSource, options: VideoDecodeOptions, context: PipelineContext): VideoIR =
        GStreamerVideoEngine.decode(source.readAll(), "mkv", options, context)

    override suspend fun encode(
        ir: VideoIR,
        format: VideoFormat,
        options: VideoEncodeOptions,
        context: PipelineContext,
    ): Bytes {
        require(format == VideoFormat.Mkv) { "GstMkvCodec only supports MKV, got $format" }
        return GStreamerVideoEngine.encode(
            ir,
            videoEncoder = "x264enc",
            audioEncoder = aacEncoderElement(),
            muxElement = "matroskamux",
            ext = "mkv",
            extraElements = listOf("!", "h264parse"),
            context = context,
        ).asBytes()
    }
}
