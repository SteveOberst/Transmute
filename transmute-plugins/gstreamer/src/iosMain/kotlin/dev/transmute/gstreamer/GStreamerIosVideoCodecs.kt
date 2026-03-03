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
// GStreamer-backed video codecs for iOS.
// Mirrors the Desktop / Android implementations but delegates to
// GStreamerIosVideoEngine (cinterop) instead of subprocess / JNI.
// ---------------------------------------------------------------------------

// --- MP4 (H.264 + AAC) ---

internal class GstIosMp4Codec : VideoCodec {
    override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.Mp4)
    override val encodableFormats: Set<VideoFormat> = setOf(VideoFormat.Mp4)

    override suspend fun decode(source: TSource, options: VideoDecodeOptions, context: PipelineContext): VideoIR =
        GStreamerIosVideoEngine.decode(source.readAll(), "mp4", options, context)

    override suspend fun encode(ir: VideoIR, format: VideoFormat, options: VideoEncodeOptions, context: PipelineContext): Bytes {
        require(format == VideoFormat.Mp4)
        return GStreamerIosVideoEngine.encode(
            ir, videoEncoder = "x264enc", audioEncoder = iosAacEncoderElement(),
            muxElement = "mp4mux", ext = "mp4",
            extraElements = listOf("h264parse"),
            context = context,
        ).asBytes()
    }
}

// --- MOV (H.264 + AAC) ---

internal class GstIosMovCodec : VideoCodec {
    override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.Mov)
    override val encodableFormats: Set<VideoFormat> = setOf(VideoFormat.Mov)

    override suspend fun decode(source: TSource, options: VideoDecodeOptions, context: PipelineContext): VideoIR =
        GStreamerIosVideoEngine.decode(source.readAll(), "mov", options, context)

    override suspend fun encode(ir: VideoIR, format: VideoFormat, options: VideoEncodeOptions, context: PipelineContext): Bytes {
        require(format == VideoFormat.Mov)
        return GStreamerIosVideoEngine.encode(
            ir, videoEncoder = "x264enc", audioEncoder = iosAacEncoderElement(),
            muxElement = "qtmux", ext = "mov",
            extraElements = listOf("h264parse"),
            context = context,
        ).asBytes()
    }
}

// --- WebM (VP8 + Vorbis) ---

internal class GstIosWebmCodec : VideoCodec {
    override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.Webm)
    override val encodableFormats: Set<VideoFormat> = setOf(VideoFormat.Webm)

    override suspend fun decode(source: TSource, options: VideoDecodeOptions, context: PipelineContext): VideoIR =
        GStreamerIosVideoEngine.decode(source.readAll(), "webm", options, context)

    override suspend fun encode(ir: VideoIR, format: VideoFormat, options: VideoEncodeOptions, context: PipelineContext): Bytes {
        require(format == VideoFormat.Webm)
        return GStreamerIosVideoEngine.encode(
            ir, videoEncoder = "vp8enc", audioEncoder = "vorbisenc",
            muxElement = "webmmux", ext = "webm",
            context = context,
        ).asBytes()
    }
}

// --- AVI (MPEG-4 + MP3) ---

internal class GstIosAviCodec : VideoCodec {
    override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.Avi)
    override val encodableFormats: Set<VideoFormat> = setOf(VideoFormat.Avi)

    override suspend fun decode(source: TSource, options: VideoDecodeOptions, context: PipelineContext): VideoIR =
        GStreamerIosVideoEngine.decode(source.readAll(), "avi", options, context)

    override suspend fun encode(ir: VideoIR, format: VideoFormat, options: VideoEncodeOptions, context: PipelineContext): Bytes {
        require(format == VideoFormat.Avi)
        return GStreamerIosVideoEngine.encode(
            ir, videoEncoder = "avenc_mpeg4", audioEncoder = "lamemp3enc",
            muxElement = "avimux", ext = "avi",
            context = context,
        ).asBytes()
    }
}

// --- MKV (H.264 + AAC) ---

internal class GstIosMkvCodec : VideoCodec {
    override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.Mkv)
    override val encodableFormats: Set<VideoFormat> = setOf(VideoFormat.Mkv)

    override suspend fun decode(source: TSource, options: VideoDecodeOptions, context: PipelineContext): VideoIR =
        GStreamerIosVideoEngine.decode(source.readAll(), "mkv", options, context)

    override suspend fun encode(ir: VideoIR, format: VideoFormat, options: VideoEncodeOptions, context: PipelineContext): Bytes {
        require(format == VideoFormat.Mkv)
        return GStreamerIosVideoEngine.encode(
            ir, videoEncoder = "x264enc", audioEncoder = iosAacEncoderElement(),
            muxElement = "matroskamux", ext = "mkv",
            extraElements = listOf("h264parse"),
            context = context,
        ).asBytes()
    }
}
