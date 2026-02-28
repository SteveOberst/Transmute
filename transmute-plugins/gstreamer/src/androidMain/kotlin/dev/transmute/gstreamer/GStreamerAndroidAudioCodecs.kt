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
// GStreamer-backed audio codecs for Android.
// Mirrors the Desktop implementations but delegates to
// GStreamerAndroidAudioEngine (JNI) instead of subprocess.
// Sniff functions are shared via GStreamerSniff.
// ---------------------------------------------------------------------------

// --- AAC (ADTS) ---

internal class GstAndroidAacCodec : AudioCodec {
    override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.Aac)
    override val encodableFormats: Set<AudioFormat> = setOf(AudioFormat.Aac)

    override fun sniff(data: Bytes): AudioFormat? = GStreamerSniff.sniffAac(data)

    override suspend fun decode(source: Bytes, options: AudioDecodeOptions, context: PipelineContext): AudioIR =
        GStreamerAndroidAudioEngine.decode(source.data, "aac", options, context)

    override suspend fun encode(ir: AudioIR, format: AudioFormat, options: AudioEncodeOptions, context: PipelineContext): Bytes {
        require(format == AudioFormat.Aac)
        val encoder = androidAacEncoderElement()
        return GStreamerAndroidAudioEngine.encode(ir, encoder, tailElements = "! aacparse", ext = "aac", context = context).asBytes()
    }
}

// --- M4A (AAC in MP4/IPOD container) ---

internal class GstAndroidM4aCodec : AudioCodec {
    override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.M4a)
    override val encodableFormats: Set<AudioFormat> = setOf(AudioFormat.M4a)

    override fun sniff(data: Bytes): AudioFormat? = GStreamerSniff.sniffM4a(data)

    override suspend fun decode(source: Bytes, options: AudioDecodeOptions, context: PipelineContext): AudioIR =
        GStreamerAndroidAudioEngine.decode(source.data, "m4a", options, context)

    override suspend fun encode(ir: AudioIR, format: AudioFormat, options: AudioEncodeOptions, context: PipelineContext): Bytes {
        require(format == AudioFormat.M4a)
        val encoder = androidAacEncoderElement()
        return GStreamerAndroidAudioEngine.encode(ir, encoder, tailElements = "! mp4mux", ext = "m4a", context = context).asBytes()
    }
}

// --- Opus (in OGG container) ---

internal class GstAndroidOpusCodec : AudioCodec {
    override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.Opus)
    override val encodableFormats: Set<AudioFormat> = setOf(AudioFormat.Opus)

    override fun sniff(data: Bytes): AudioFormat? = GStreamerSniff.sniffOpus(data)

    override suspend fun decode(source: Bytes, options: AudioDecodeOptions, context: PipelineContext): AudioIR =
        GStreamerAndroidAudioEngine.decode(source.data, "opus", options, context)

    override suspend fun encode(ir: AudioIR, format: AudioFormat, options: AudioEncodeOptions, context: PipelineContext): Bytes {
        require(format == AudioFormat.Opus)
        return GStreamerAndroidAudioEngine.encode(ir, "opusenc", tailElements = "! oggmux", ext = "opus", context = context).asBytes()
    }
}

// --- FLAC (encode only) ---

internal class GstAndroidFlacEncoder : AudioEncoder {
    override val supportedFormats: Set<AudioFormat> = setOf(AudioFormat.Flac)

    override suspend fun encode(ir: AudioIR, format: AudioFormat, options: AudioEncodeOptions, context: PipelineContext): Bytes {
        require(format == AudioFormat.Flac)
        return GStreamerAndroidAudioEngine.encode(ir, "flacenc", ext = "flac", context = context).asBytes()
    }
}

// --- OGG/Vorbis (encode only) ---

internal class GstAndroidOggVorbisEncoder : AudioEncoder {
    override val supportedFormats: Set<AudioFormat> = setOf(AudioFormat.Ogg)

    override suspend fun encode(ir: AudioIR, format: AudioFormat, options: AudioEncodeOptions, context: PipelineContext): Bytes {
        require(format == AudioFormat.Ogg)
        return GStreamerAndroidAudioEngine.encode(ir, "vorbisenc", tailElements = "! oggmux", ext = "ogg", context = context).asBytes()
    }
}

// ---------------------------------------------------------------------------
// AAC encoder element discovery
// ---------------------------------------------------------------------------

private val _androidAacEncoder: String by lazy {
    listOf("fdkaacenc", "voaacenc", "avenc_aac", "faac")
        .firstOrNull { GStreamerJni.hasElement(it) }
        ?: "voaacenc"
}

internal fun androidAacEncoderElement(): String = _androidAacEncoder
