@file:Suppress("unused")

package dev.transmute.model.structure.audio

import dev.transmute.model.core.BitsPerSample
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.Channels
import dev.transmute.model.core.Hertz
import dev.transmute.model.identify.RiffChunkId
import dev.transmute.model.core.RawMediaStructure
import dev.transmute.model.structure.common.RiffChunk
import kotlinx.serialization.Serializable

// --- Well-known WAV audio format codes ---

/**
 * Audio format codes used in the WAV `fmt ` chunk.
 */
@Serializable
enum class WavAudioFormat(val code: UShort) {
    /** PCM (uncompressed). */
    Pcm(1u),
    /** IEEE 754 floating-point. */
    IeeeFloat(3u),
    /** A-law companding. */
    ALaw(6u),
    /** µ-law companding. */
    MuLaw(7u),
    /** Extensible format (WAVEFORMATEXTENSIBLE). */
    Extensible(0xFFFEu);

    companion object {
        fun fromCode(code: UShort): WavAudioFormat? = entries.find { it.code == code }
    }
}

// --- Typed model for the `fmt ` chunk ---

/**
 * Parsed contents of the WAV `fmt ` chunk (WAVEFORMATEX).
 */
@Serializable
data class WavFmtChunk(
    /** Audio format code (see [WavAudioFormat]). */
    val audioFormat: UShort,
    /** Number of audio channels. */
    val numChannels: UShort,
    /** Sample rate in Hz. */
    val sampleRate: UInt,
    /** Average byte rate (sampleRate × blockAlign). */
    val byteRate: UInt,
    /** Block align (channels × bitsPerSample / 8). */
    val blockAlign: UShort,
    /** Bits per sample. */
    val bitsPerSample: UShort,
    /** Extra format data beyond the base 16 bytes. */
    val extraData: Bytes = Bytes(ByteArray(0)),
)

// --- WAV file — complete on-disk representation ---

/**
 * Canonical representation of a WAV file as written to disk.
 *
 * WAV uses a RIFF container with form type `WAVE`.  The top-level
 * RIFF chunk wraps sub-chunks: `fmt ` (format), `data` (audio samples),
 * and optional chunks like `LIST`, `fact`, `smpl`, `cue `, etc.
 *
 * ```
 * | RIFF (4 B) | fileSize (4 B LE) | WAVE (4 B) | sub-chunks… |
 * ```
 */
@Serializable
data class WavRaw(
    /** The top-level RIFF container chunk (id = `RIFF`, formType = `WAVE`). */
    val riff: RiffChunk,
) : RawMediaStructure {

    // --- Binary serialization ---

    override fun toBytes(): Bytes = riff.toBytes()

    companion object {
        /** RIFF form type for WAV files. */
        val FORM_TYPE = RiffChunkId("WAVE")
    }
}

// --- Typed extension accessors ---

/** Sub-chunks inside the RIFF container. */
val WavRaw.chunks: List<RiffChunk> get() = riff.children

/** The raw `fmt ` chunk, or `null` if not found. */
val WavRaw.fmtChunk: RiffChunk?
    get() = chunks.firstOrNull { it.id.value == "fmt " }

/** Parsed `fmt ` data. */
val WavRaw.fmt: WavFmtChunk?
    get() {
        val c = fmtChunk ?: return null
        val d = c.data.data
        if (d.size < 16) return null
        fun u16(off: Int) = ((d[off].toUInt() and 0xFFu) or ((d[off+1].toUInt() and 0xFFu) shl 8)).toUShort()
        fun u32(off: Int) = (d[off].toUInt() and 0xFFu) or ((d[off+1].toUInt() and 0xFFu) shl 8) or
                ((d[off+2].toUInt() and 0xFFu) shl 16) or ((d[off+3].toUInt() and 0xFFu) shl 24)
        val extra = if (d.size > 16) Bytes(d.copyOfRange(16, d.size)) else Bytes(ByteArray(0))
        return WavFmtChunk(
            audioFormat = u16(0), numChannels = u16(2),
            sampleRate = u32(4), byteRate = u32(8),
            blockAlign = u16(12), bitsPerSample = u16(14),
            extraData = extra,
        )
    }

/** The raw `data` chunk (audio samples), or `null`. */
val WavRaw.dataChunk: RiffChunk?
    get() = chunks.firstOrNull { it.id.value == "data" }

/** Sample rate from the `fmt ` chunk. */
val WavRaw.sampleRate: Hertz? get() = fmt?.sampleRate?.toInt()?.let { Hertz(it) }

/** Channel count from the `fmt ` chunk. */
val WavRaw.channels: Channels? get() = fmt?.numChannels?.toInt()?.let { Channels(it) }

/** Bits per sample from the `fmt ` chunk. */
val WavRaw.bitsPerSample: BitsPerSample? get() = fmt?.bitsPerSample?.toInt()?.let { BitsPerSample(it) }

/** Resolved audio format, or `null` for unknown codes. */
val WavRaw.audioFormat: WavAudioFormat? get() = fmt?.let { WavAudioFormat.fromCode(it.audioFormat) }
