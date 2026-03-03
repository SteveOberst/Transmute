@file:Suppress("unused")

package dev.transmute.model.structure.audio.types

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.Channels
import dev.transmute.model.core.Hertz
import dev.transmute.model.core.RawMediaStructure
import kotlinx.serialization.Serializable

// ================================================================
//  AAC enums
// ================================================================

/**
 * AAC audio object type (profile).
 */
@Serializable
enum class AacProfile(val objectType: Int) {
    AacLc(2),
    HePlusAac(5),
    HePlusAacV2(29),
    AacLd(23),
    AacEld(39);

    companion object {
        fun fromObjectType(type: Int): AacProfile? =
            entries.firstOrNull { it.objectType == type }
    }
}

// ================================================================
//  Typed model: ADTS frame header
// ================================================================

/**
 * Parsed ADTS (Audio Data Transport Stream) frame header.
 *
 * An ADTS header is 7 bytes (without CRC) or 9 bytes (with CRC):
 * ```
 * | syncword (12 b) | ID (1 b) | layer (2 b) | protection (1 b) |
 * | profile (2 b) | samplingFreqIdx (4 b) | private (1 b) | channelConfig (3 b) |
 * | ... | frameLength (13 b) | ... |
 * ```
 */
@Serializable
data class AdtsFrameHeader(
    val profile: AacProfile?,
    val sampleRate: Hertz,
    val channels: Channels,
    val hasCrc: Boolean,
    val frameLength: Int,
    /** True if MPEG-4 (ID bit == 0), false if MPEG-2 (ID bit == 1). */
    val isMpeg4: Boolean,
)

// ================================================================
//  AAC file - complete on-disk representation
// ================================================================

/**
 * Canonical representation of a raw AAC ADTS file as written to disk.
 *
 * A raw ADTS file is a stream of self-delimiting ADTS frames:
 * ```
 * | Frame 0 (header + payload) | Frame 1 | ... |
 * ```
 *
 * Storing every individual frame is impractical, so the file's
 * entire content is held as a single opaque [data] blob.  Typed
 * accessors parse the first ADTS frame header.
 */
@Serializable
data class AacRaw(
    /** Complete file content (all ADTS frames). */
    val data: Bytes,
) : RawMediaStructure {

    // --- Binary serialization ---

    override fun toBytes(): Bytes = data

    companion object {
        /** ADTS sample rate index table. */
        private val ADTS_SAMPLE_RATES = intArrayOf(
            96000, 88200, 64000, 48000, 44100, 32000,
            24000, 22050, 16000, 12000, 11025, 8000, 7350,
        )
    }
}

// --- Typed extension accessors ---

/** ADTS sample rate index table. */
private val ADTS_SAMPLE_RATES = intArrayOf(
    96000, 88200, 64000, 48000, 44100, 32000,
    24000, 22050, 16000, 12000, 11025, 8000, 7350,
)

/** Parse the first ADTS frame header. */
val AacRaw.firstFrameHeader: AdtsFrameHeader?
    get() {
        val d = data.data
        if (d.size < 7) return null
        if ((d[0].toInt() and 0xFF) != 0xFF) return null
        if ((d[1].toInt() and 0xF0) != 0xF0) return null
        val b1 = d[1].toInt() and 0xFF
        val isMpeg4 = (b1 and 0x08) == 0
        val hasCrc = (b1 and 0x01) == 0
        val b2 = d[2].toInt() and 0xFF
        val profileIdx = ((b2 shr 6) and 0x03) + 1
        val srIdx = (b2 shr 2) and 0x0F
        val chCfg = ((b2 and 0x01) shl 2) or ((d[3].toInt() and 0xFF) shr 6)
        val b3 = d[3].toInt() and 0xFF
        val b4 = d[4].toInt() and 0xFF
        val frameLen = ((b3 and 0x03) shl 11) or (b4 shl 3) or ((d[5].toInt() and 0xFF) shr 5)
        val sr = ADTS_SAMPLE_RATES.getOrNull(srIdx) ?: return null
        return AdtsFrameHeader(
            profile = AacProfile.fromObjectType(profileIdx),
            sampleRate = Hertz(sr),
            channels = Channels(chCfg),
            hasCrc = hasCrc,
            frameLength = frameLen,
            isMpeg4 = isMpeg4,
        )
    }

/** Sample rate from the first ADTS frame. */
val AacRaw.sampleRate: Hertz?
    get() = firstFrameHeader?.sampleRate

/** Channel count from the first ADTS frame. */
val AacRaw.channels: Channels?
    get() = firstFrameHeader?.channels

/** Profile from the first ADTS frame. */
val AacRaw.profile: AacProfile?
    get() = firstFrameHeader?.profile
