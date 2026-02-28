@file:Suppress("unused")

package dev.transmute.model.structure.audio

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.Channels
import dev.transmute.model.core.Hertz
import dev.transmute.model.core.asBytes
import dev.transmute.model.core.RawMediaStructure
import dev.transmute.model.structure.common.OggPage
import dev.transmute.model.structure.common.OggSerialNumber
import kotlinx.serialization.Serializable

// --- Typed model for Opus identification header ---

/**
 * Parsed Opus identification header (OpusHead).
 *
 * The first packet in the Opus logical stream carries this header.
 * Layout (19 bytes minimum):
 * ```
 * | "OpusHead" (8 B) | version (1 B) | channels (1 B) | preSkip (2 B LE) |
 * | sampleRate (4 B LE) | outputGain (2 B LE) | channelMapping (1 B) | … |
 * ```
 */
@Serializable
data class OpusIdentification(
    val version: Int,
    val channels: UByte,
    val preSkipSamples: UShort,
    val inputSampleRate: UInt,
    val outputGain: Short,
    val channelMappingFamily: UByte,
)

// --- Opus file — complete on-disk representation ---

/**
 * Canonical representation of an Opus file as written to disk.
 *
 * Opus is encapsulated in an Ogg container.  The file is a sequence
 * of Ogg pages carrying OpusHead, OpusTags, and audio data packets.
 */
@Serializable
data class OpusRaw(
    /** All Ogg pages in file order. */
    val pages: List<OggPage>,
) : RawMediaStructure {

    // --- Binary serialization ---

    override fun toBytes(): Bytes {
        val parts = pages.map { it.toBytes().data }
        val total = parts.sumOf { it.size }
        val out = ByteArray(total)
        var pos = 0
        for (part in parts) { part.copyInto(out, pos); pos += part.size }
        return out.asBytes()
    }
}

// --- Typed extension accessors ---

/** Distinct logical stream serial numbers. */
val OpusRaw.streamSerialNumbers: List<OggSerialNumber>
    get() = pages.map { it.serialNumber }.distinct()

/** Parsed OpusHead identification header. */
val OpusRaw.opusIdentification: OpusIdentification?
    get() {
        val bos = pages.firstOrNull { (it.headerType.toInt() and 0x02) != 0 } ?: return null
        val d = bos.data.data
        if (d.size < 19) return null
        val magic = d.decodeToString(0, 8)
        if (magic != "OpusHead") return null
        fun u16(off: Int) = ((d[off].toUInt() and 0xFFu) or ((d[off+1].toUInt() and 0xFFu) shl 8)).toUShort()
        fun u32(off: Int) = (d[off].toUInt() and 0xFFu) or ((d[off+1].toUInt() and 0xFFu) shl 8) or
                ((d[off+2].toUInt() and 0xFFu) shl 16) or ((d[off+3].toUInt() and 0xFFu) shl 24)
        return OpusIdentification(
            version = d[8].toInt() and 0xFF,
            channels = d[9].toUByte(),
            preSkipSamples = u16(10),
            inputSampleRate = u32(12),
            outputGain = u16(16).toShort(),
            channelMappingFamily = d[18].toUByte(),
        )
    }

/** Sample rate from OpusHead (input sample rate, or 48000 as Opus default). */
val OpusRaw.sampleRate: Hertz
    get() {
        val rate = opusIdentification?.inputSampleRate?.toInt() ?: 48000
        return Hertz(if (rate == 0) 48000 else rate)
    }

/** Channel count from OpusHead. */
val OpusRaw.channels: Channels?
    get() = opusIdentification?.channels?.toInt()?.let { Channels(it) }

/** Pre-skip sample count. */
val OpusRaw.preSkipSamples: Int
    get() = opusIdentification?.preSkipSamples?.toInt() ?: 0

/** Output gain in Q7.8 dB. */
val OpusRaw.outputGain: Short
    get() = opusIdentification?.outputGain ?: 0
