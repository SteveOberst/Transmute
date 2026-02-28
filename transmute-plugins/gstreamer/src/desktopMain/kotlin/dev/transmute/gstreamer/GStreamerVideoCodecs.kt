package dev.transmute.gstreamer

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

    override fun sniff(data: Bytes): VideoFormat? {
        val bytes = data.data
        if (bytes.size < 12) return null
        if (bytes[4] != 0x66.toByte() || bytes[5] != 0x74.toByte() ||
            bytes[6] != 0x79.toByte() || bytes[7] != 0x70.toByte()
        ) return null
        val brand = (8 until 12).map { bytes[it].toInt().toChar() }.joinToString("")
        return when {
            brand.startsWith("mp4") || brand == "isom" || brand == "M4V " ||
                brand == "avc1" || brand == "iso2" || brand == "iso5" ||
                brand == "iso6" || brand == "mmp4" -> VideoFormat.Mp4
            brand.startsWith("3gp") || brand.startsWith("3g2") -> VideoFormat.Mp4
            else -> null
        }
    }

    override suspend fun decode(source: Bytes, options: VideoDecodeOptions, context: PipelineContext): VideoIR =
        GStreamerVideoEngine.decode(source.data, "mp4", options, context)

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

    override fun sniff(data: Bytes): VideoFormat? {
        val bytes = data.data
        if (bytes.size < 12) return null
        if (bytes[4] != 0x66.toByte() || bytes[5] != 0x74.toByte() ||
            bytes[6] != 0x79.toByte() || bytes[7] != 0x70.toByte()
        ) return null
        val brand = (8 until 12).map { bytes[it].toInt().toChar() }.joinToString("")
        return if (brand == "qt  ") VideoFormat.Mov else null
    }

    override suspend fun decode(source: Bytes, options: VideoDecodeOptions, context: PipelineContext): VideoIR =
        GStreamerVideoEngine.decode(source.data, "mov", options, context)

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

    override fun sniff(data: Bytes): VideoFormat? {
        val bytes = data.data
        if (bytes.size < 4) return null
        if (bytes[0] != 0x1A.toByte() || bytes[1] != 0x45.toByte() ||
            bytes[2] != 0xDF.toByte() || bytes[3] != 0xA3.toByte()
        ) return null
        if (bytes.size >= 40) {
            val content = bytes.copyOfRange(0, minOf(bytes.size, 64)).decodeToString()
            if (content.contains("matroska")) return null // MKV, not WebM
            if (content.contains("webm")) return VideoFormat.Webm
        }
        return VideoFormat.Webm
    }

    override suspend fun decode(source: Bytes, options: VideoDecodeOptions, context: PipelineContext): VideoIR =
        GStreamerVideoEngine.decode(source.data, "webm", options, context)

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

    override fun sniff(data: Bytes): VideoFormat? {
        val bytes = data.data
        if (bytes.size < 12) return null
        if (bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'A'.code.toByte() && bytes[9] == 'V'.code.toByte() &&
            bytes[10] == 'I'.code.toByte() && bytes[11] == ' '.code.toByte()
        ) return VideoFormat.Avi
        return null
    }

    override suspend fun decode(source: Bytes, options: VideoDecodeOptions, context: PipelineContext): VideoIR =
        GStreamerVideoEngine.decode(source.data, "avi", options, context)

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

    override fun sniff(data: Bytes): VideoFormat? {
        val bytes = data.data
        if (bytes.size < 4) return null
        if (bytes[0] != 0x1A.toByte() || bytes[1] != 0x45.toByte() ||
            bytes[2] != 0xDF.toByte() || bytes[3] != 0xA3.toByte()
        ) return null
        if (bytes.size >= 40) {
            val content = bytes.copyOfRange(0, minOf(bytes.size, 64)).decodeToString()
            if (content.contains("matroska")) return VideoFormat.Mkv
        }
        return null
    }

    override suspend fun decode(source: Bytes, options: VideoDecodeOptions, context: PipelineContext): VideoIR =
        GStreamerVideoEngine.decode(source.data, "mkv", options, context)

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
