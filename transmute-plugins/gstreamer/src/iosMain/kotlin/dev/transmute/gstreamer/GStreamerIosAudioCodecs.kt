package dev.transmute.gstreamer

import dev.transmute.io.TSource
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
// ---------------------------------------------------------------------------

// --- AAC (ADTS) ---

internal class GstIosAacCodec : AudioCodec {
    override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.Aac)
    override val encodableFormats: Set<AudioFormat> = setOf(AudioFormat.Aac)

    override suspend fun decode(source: TSource, options: AudioDecodeOptions, context: PipelineContext): AudioIR =
        GStreamerIosAudioEngine.decode(source.readAll(), "aac", options, context)

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

    override suspend fun decode(source: TSource, options: AudioDecodeOptions, context: PipelineContext): AudioIR =
        GStreamerIosAudioEngine.decode(source.readAll(), "m4a", options, context)

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

    override suspend fun decode(source: TSource, options: AudioDecodeOptions, context: PipelineContext): AudioIR =
        GStreamerIosAudioEngine.decode(source.readAll(), "opus", options, context)

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

private val iosAacEncoderCandidates = listOf("fdkaacenc", "voaacenc", "avenc_aac", "faac")

private val _iosAacEncoder: String? by lazy {
    iosAacEncoderCandidates.firstOrNull { GStreamerIosBridge.hasElement(it) }
}

private fun hasAllElements(vararg elements: String): Boolean =
    elements.all(GStreamerIosBridge::hasElement)

internal fun iosAacEncoderElementOrNull(): String? = _iosAacEncoder

internal fun iosAacEncodeSupported(): Boolean =
    iosAacEncoderElementOrNull() != null && hasAllElements("aacparse")

internal fun iosM4aEncodeSupported(): Boolean =
    iosAacEncoderElementOrNull() != null && hasAllElements("mp4mux")

internal fun iosOpusEncodeSupported(): Boolean =
    hasAllElements("opusenc", "oggmux")

internal fun iosFlacEncodeSupported(): Boolean =
    hasAllElements("flacenc")

internal fun iosOggVorbisEncodeSupported(): Boolean =
    hasAllElements("vorbisenc", "oggmux")

private fun requireAacEncoderElement(): String =
    iosAacEncoderElementOrNull()
        ?: error("No supported AAC encoder element available on this iOS GStreamer runtime: ${iosAacEncoderCandidates.joinToString()}")

internal fun iosAacEncoderElement(): String = requireAacEncoderElement()
