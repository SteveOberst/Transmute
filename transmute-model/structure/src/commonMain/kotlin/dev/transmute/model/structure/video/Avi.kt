@file:Suppress("unused")

package dev.transmute.model.structure.video

import dev.transmute.model.core.Bytes
import dev.transmute.model.identify.RiffChunkId
import dev.transmute.model.core.RawMediaStructure
import dev.transmute.model.structure.common.RiffChunk
import kotlinx.serialization.Serializable

// --- Typed model for the AVI main header (avih) ---

/**
 * Parsed AVI main header chunk (`avih`, 56 bytes).
 */
@Serializable
data class AviMainHeader(
    /** Microseconds per frame. */
    val microSecPerFrame: UInt,
    /** Maximum bytes per second. */
    val maxBytesPerSec: UInt,
    /** Padding granularity. */
    val paddingGranularity: UInt,
    /** Flags (e.g. AVIF_HASINDEX = 0x10). */
    val flags: UInt,
    /** Total number of video frames. */
    val totalFrames: UInt,
    /** Initial frame count for interleaved files. */
    val initialFrames: UInt,
    /** Number of streams (video + audio + …). */
    val streams: UInt,
    /** Suggested playback buffer size. */
    val suggestedBufferSize: UInt,
    /** Video width in pixels. */
    val width: UInt,
    /** Video height in pixels. */
    val height: UInt,
)

// --- AVI file — complete on-disk representation ---

/**
 * Canonical representation of an AVI file as written to disk.
 *
 * AVI uses a RIFF container with form type `AVI `.  Key sub-structures:
 *
 * ```
 * RIFF 'AVI '
 *   LIST 'hdrl'
 *     avih (main header)
 *     LIST 'strl' (per stream)
 *       strh (stream header)
 *       strf (stream format)
 *   LIST 'movi'
 *     00dc / 01wb … (interleaved data chunks)
 *   idx1 (optional index)
 * ```
 */
@Serializable
data class AviRaw(
    /** The top-level RIFF container (id = `RIFF`, formType = `AVI `). */
    val riff: RiffChunk,
) : RawMediaStructure {

    // --- Binary serialization ---

    override fun toBytes(): Bytes = riff.toBytes()

    companion object {
        /** RIFF form type for AVI files. */
        val FORM_TYPE = RiffChunkId("AVI ")
    }
}

// --- Typed extension accessors ---

/** Sub-chunks inside the RIFF container. */
val AviRaw.chunks: List<RiffChunk> get() = riff.children

/** The `hdrl` LIST chunk, or `null`. */
val AviRaw.headerList: RiffChunk?
    get() = chunks.firstOrNull { it.id.value == "LIST" && it.formType?.value == "hdrl" }

/** The `movi` LIST chunk, or `null`. */
val AviRaw.movieList: RiffChunk?
    get() = chunks.firstOrNull { it.id.value == "LIST" && it.formType?.value == "movi" }

/** The `idx1` index chunk, or `null`. */
val AviRaw.indexChunk: RiffChunk?
    get() = chunks.firstOrNull { it.id.value == "idx1" }

/** Parsed AVI main header from the `avih` chunk. */
val AviRaw.mainHeader: AviMainHeader?
    get() {
        val avih = headerList?.children?.firstOrNull { it.id.value == "avih" } ?: return null
        val d = avih.data.data
        if (d.size < 40) return null
        fun u32(off: Int) = (d[off].toUInt() and 0xFFu) or
                ((d[off+1].toUInt() and 0xFFu) shl 8) or
                ((d[off+2].toUInt() and 0xFFu) shl 16) or
                ((d[off+3].toUInt() and 0xFFu) shl 24)
        return AviMainHeader(
            microSecPerFrame = u32(0), maxBytesPerSec = u32(4),
            paddingGranularity = u32(8), flags = u32(12),
            totalFrames = u32(16), initialFrames = u32(20),
            streams = u32(24), suggestedBufferSize = u32(28),
            width = u32(32), height = u32(36),
        )
    }

/** Number of stream LIST chunks (`strl`). */
val AviRaw.streamCount: Int
    get() = headerList?.children?.count { it.id.value == "LIST" && it.formType?.value == "strl" } ?: 0
