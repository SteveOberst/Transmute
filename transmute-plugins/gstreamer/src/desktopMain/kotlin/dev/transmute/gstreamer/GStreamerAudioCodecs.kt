package dev.transmute.gstreamer

import dev.transmute.io.TSource
import dev.transmute.audio.AudioCodec
import dev.transmute.audio.AudioDecodeOptions
import dev.transmute.audio.AudioEncoder
import dev.transmute.audio.AudioEncodeOptions
import dev.transmute.audio.AudioFormat
import dev.transmute.audio.AudioIR
import dev.transmute.common.PipelineContext
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes

// ---------------------------------------------------------------------------
// AAC codec (ADTS container, GStreamer-backed)
// ---------------------------------------------------------------------------

/**
 * AAC codec for the JVM desktop target.
 *
 * Decodes and encodes AAC (ADTS) via GStreamer subprocess.
 * Encoder selection: tries `fdkaacenc`, `voaacenc`, `avenc_aac` in order.
 */
internal class GstAacCodec : AudioCodec {

    override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.Aac)
    override val encodableFormats: Set<AudioFormat> = setOf(AudioFormat.Aac)

    override suspend fun decode(source: TSource, options: AudioDecodeOptions, context: PipelineContext): AudioIR =
        GStreamerAudioEngine.decode(source.readAll(), "aac", options, context)

    override suspend fun encode(
        ir: AudioIR,
        format: AudioFormat,
        options: AudioEncodeOptions,
        context: PipelineContext,
    ): Bytes {
        require(format == AudioFormat.Aac) { "GstAacCodec only supports AAC, got $format" }
        val encoder = aacEncoderElement()
        return GStreamerAudioEngine.encode(
            ir, encoder, tailElements = "! aacparse", ext = "aac", context = context,
        ).asBytes()
    }
}

// ---------------------------------------------------------------------------
// M4A codec (AAC in MP4/IPOD container, GStreamer-backed)
// ---------------------------------------------------------------------------

/**
 * M4A codec for the JVM desktop target.
 *
 * Decodes and encodes M4A (AAC inside an MP4 container) via GStreamer subprocess.
 */
internal class GstM4aCodec : AudioCodec {

    override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.M4a)
    override val encodableFormats: Set<AudioFormat> = setOf(AudioFormat.M4a)

    override suspend fun decode(source: TSource, options: AudioDecodeOptions, context: PipelineContext): AudioIR =
        GStreamerAudioEngine.decode(source.readAll(), "m4a", options, context)

    override suspend fun encode(
        ir: AudioIR,
        format: AudioFormat,
        options: AudioEncodeOptions,
        context: PipelineContext,
    ): Bytes {
        require(format == AudioFormat.M4a) { "GstM4aCodec only supports M4A, got $format" }
        val encoder = aacEncoderElement()
        return GStreamerAudioEngine.encode(
            ir, encoder, tailElements = "! mp4mux", ext = "m4a", context = context,
        ).asBytes()
    }
}

// ---------------------------------------------------------------------------
// OPUS codec (Opus in OGG container, GStreamer-backed)
// ---------------------------------------------------------------------------

/**
 * Opus codec for the JVM desktop target.
 *
 * Decodes and encodes Opus (in an OGG container) via GStreamer subprocess.
 */
internal class GstOpusCodec : AudioCodec {

    override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.Opus)
    override val encodableFormats: Set<AudioFormat> = setOf(AudioFormat.Opus)

    override suspend fun decode(source: TSource, options: AudioDecodeOptions, context: PipelineContext): AudioIR =
        GStreamerAudioEngine.decode(source.readAll(), "opus", options, context)

    override suspend fun encode(
        ir: AudioIR,
        format: AudioFormat,
        options: AudioEncodeOptions,
        context: PipelineContext,
    ): Bytes {
        require(format == AudioFormat.Opus) { "GstOpusCodec only supports OPUS, got $format" }
        return GStreamerAudioEngine.encode(
            ir, "opusenc", tailElements = "! oggmux", ext = "opus", context = context,
        ).asBytes()
    }
}

// ---------------------------------------------------------------------------
// FLAC encoder (GStreamer-backed, decode is native JFlac)
// ---------------------------------------------------------------------------

/**
 * FLAC encoder for the JVM desktop target.
 *
 * Provides encode-only capability via GStreamer. FLAC decoding is handled
 * natively by JFlac in the platform audio codecs.
 */
internal class GstFlacEncoder : AudioEncoder {
    override val supportedFormats: Set<AudioFormat> = setOf(AudioFormat.Flac)

    override suspend fun encode(
        ir: AudioIR,
        format: AudioFormat,
        options: AudioEncodeOptions,
        context: PipelineContext,
    ): Bytes {
        require(format == AudioFormat.Flac) { "GstFlacEncoder only supports FLAC, got $format" }
        return GStreamerAudioEngine.encode(
            ir, "flacenc", ext = "flac", context = context,
        ).asBytes()
    }
}

// ---------------------------------------------------------------------------
// OGG/Vorbis encoder (GStreamer-backed, decode is native JOrbis)
// ---------------------------------------------------------------------------

/**
 * OGG/Vorbis encoder for the JVM desktop target.
 *
 * Provides encode-only capability via GStreamer. OGG/Vorbis decoding is
 * handled natively by JOrbis in the platform audio codecs.
 */
internal class GstOggVorbisEncoder : AudioEncoder {
    override val supportedFormats: Set<AudioFormat> = setOf(AudioFormat.Ogg)

    override suspend fun encode(
        ir: AudioIR,
        format: AudioFormat,
        options: AudioEncodeOptions,
        context: PipelineContext,
    ): Bytes {
        require(format == AudioFormat.Ogg) { "GstOggVorbisEncoder only supports OGG, got $format" }
        return GStreamerAudioEngine.encode(
            ir, "vorbisenc", tailElements = "! oggmux", ext = "ogg", context = context,
        ).asBytes()
    }
}

// ---------------------------------------------------------------------------
// AAC encoder element discovery
// ---------------------------------------------------------------------------

/** Cached AAC encoder element name. */
private val _aacEncoder: String by lazy {
    listOf("fdkaacenc", "voaacenc", "avenc_aac", "faac")
        .firstOrNull { GStreamerResolver.hasElement(it) }
        ?: "voaacenc" // fallback - will fail at runtime if none available
}

/** Returns the best available GStreamer AAC encoder element. */
internal fun aacEncoderElement(): String = _aacEncoder
