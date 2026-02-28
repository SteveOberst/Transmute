package dev.transmute.gstreamer

import dev.transmute.audio.AudioCodec
import dev.transmute.audio.AudioDecodeOptions
import dev.transmute.audio.AudioEncodeOptions
import dev.transmute.audio.AudioEncoder
import dev.transmute.audio.AudioFormat
import dev.transmute.audio.AudioIR
import dev.transmute.common.PipelineContext
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes

// ---------------------------------------------------------------------------
// GStreamer-backed audio codecs for iOS.
// Mirrors the Desktop / Android implementations but delegates to
// GStreamerIosAudioEngine (cinterop) instead of subprocess / JNI.
// Sniff functions are shared via GStreamerSniff.
// ---------------------------------------------------------------------------

// --- AAC (ADTS) ---

internal class GstIosAacCodec : AudioCodec {
    override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.Aac)
    override val encodableFormats: Set<AudioFormat> = setOf(AudioFormat.Aac)

    override fun sniff(data: Bytes): AudioFormat? = GStreamerSniff.sniffAac(data)

    override suspend fun decode(source: Bytes, options: AudioDecodeOptions, context: PipelineContext): AudioIR =
        GStreamerIosAudioEngine.decode(source.data, "aac", options, context)

    override suspend fun encode(ir: AudioIR, format: AudioFormat, options: AudioEncodeOptions, context: PipelineContext): Bytes {
        require(format == AudioFormat.Aac)
        val encoder = iosAacEncoderElement()
        return GStreamerIosAudioEngine.encode(ir, encoder, tailElements = "! aacparse", ext = "aac", context = context).asBytes()
    }
}

// --- M4A (AAC in MP4/IPOD container) ---

internal class GstIosM4aCodec : AudioCodec {
    override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.M4a)
    override val encodableFormats: Set<AudioFormat> = setOf(AudioFormat.M4a)

    override fun sniff(data: Bytes): AudioFormat? = GStreamerSniff.sniffM4a(data)

    override suspend fun decode(source: Bytes, options: AudioDecodeOptions, context: PipelineContext): AudioIR =
        GStreamerIosAudioEngine.decode(source.data, "m4a", options, context)

    override suspend fun encode(ir: AudioIR, format: AudioFormat, options: AudioEncodeOptions, context: PipelineContext): Bytes {
        require(format == AudioFormat.M4a)
        val encoder = iosAacEncoderElement()
        return GStreamerIosAudioEngine.encode(ir, encoder, tailElements = "! mp4mux", ext = "m4a", context = context).asBytes()
    }
}

// --- Opus (in OGG container) ---

internal class GstIosOpusCodec : AudioCodec {
    override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.Opus)
    override val encodableFormats: Set<AudioFormat> = setOf(AudioFormat.Opus)

    override fun sniff(data: Bytes): AudioFormat? = GStreamerSniff.sniffOpus(data)

    override suspend fun decode(source: Bytes, options: AudioDecodeOptions, context: PipelineContext): AudioIR =
        GStreamerIosAudioEngine.decode(source.data, "opus", options, context)

    override suspend fun encode(ir: AudioIR, format: AudioFormat, options: AudioEncodeOptions, context: PipelineContext): Bytes {
        require(format == AudioFormat.Opus)
        return GStreamerIosAudioEngine.encode(ir, "opusenc", tailElements = "! oggmux", ext = "opus", context = context).asBytes()
    }
}

// --- FLAC (encode only) ---

internal class GstIosFlacEncoder : AudioEncoder {
    override val supportedFormats: Set<AudioFormat> = setOf(AudioFormat.Flac)

    override suspend fun encode(ir: AudioIR, format: AudioFormat, options: AudioEncodeOptions, context: PipelineContext): Bytes {
        require(format == AudioFormat.Flac)
        return GStreamerIosAudioEngine.encode(ir, "flacenc", ext = "flac", context = context).asBytes()
    }
}

// --- OGG/Vorbis (encode only) ---

internal class GstIosOggVorbisEncoder : AudioEncoder {
    override val supportedFormats: Set<AudioFormat> = setOf(AudioFormat.Ogg)

    override suspend fun encode(ir: AudioIR, format: AudioFormat, options: AudioEncodeOptions, context: PipelineContext): Bytes {
        require(format == AudioFormat.Ogg)
        return GStreamerIosAudioEngine.encode(ir, "vorbisenc", tailElements = "! oggmux", ext = "ogg", context = context).asBytes()
    }
}

// ---------------------------------------------------------------------------
// AAC encoder element discovery for iOS
// ---------------------------------------------------------------------------

private val _iosAacEncoder: String by lazy {
    listOf("fdkaacenc", "voaacenc", "avenc_aac", "faac")
        .firstOrNull { GStreamerIosBridge.hasElement(it) }
        ?: "voaacenc"
}

internal fun iosAacEncoderElement(): String = _iosAacEncoder
