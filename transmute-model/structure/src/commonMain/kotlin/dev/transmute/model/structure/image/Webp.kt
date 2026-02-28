@file:Suppress("unused")

package dev.transmute.model.structure.image

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.Pixels
import dev.transmute.model.identify.RiffChunkId
import dev.transmute.model.core.RawMediaStructure
import dev.transmute.model.structure.common.RiffChunk
import kotlinx.serialization.Serializable

// --- WebP encoding format ---

/**
 * WebP encoding format.
 */
@Serializable
enum class WebpFormat {
    /** Lossy compression (VP8). */
    Lossy,
    /** Lossless compression (VP8L). */
    Lossless,
    /** Extended format (VP8X — may combine lossy/lossless with alpha, animation, etc.). */
    Extended;

    companion object {
        /**
         * Infer the format from the first sub-chunk id.
         */
        fun fromChunkId(id: RiffChunkId): WebpFormat = when (id.value) {
            "VP8 " -> Lossy
            "VP8L" -> Lossless
            "VP8X" -> Extended
            else   -> Extended
        }
    }
}

// --- WebP file — complete on-disk representation ---

/**
 * Canonical representation of a WebP file as written to disk.
 *
 * WebP uses a RIFF container with form type `WEBP`.  The top-level
 * RIFF chunk wraps one or more sub-chunks (VP8, VP8L, VP8X, ALPH,
 * ANIM, ANMF, EXIF, XMP, ICCP…).
 *
 * ```
 * | RIFF (4 B) | fileSize (4 B LE) | WEBP (4 B) | sub-chunks… |
 * ```
 */
@Serializable
data class WebpRaw(
    /** The top-level RIFF container chunk (id = `RIFF`, formType = `WEBP`). */
    val riff: RiffChunk,
) : RawMediaStructure {

    // --- Binary serialization ---

    override fun toBytes(): Bytes = riff.toBytes()

    companion object {
        /** RIFF form type for WebP files. */
        val FORM_TYPE = RiffChunkId("WEBP")
    }
}

// --- Typed extension accessors ---

/** Sub-chunks inside the RIFF container. */
val WebpRaw.chunks: List<RiffChunk> get() = riff.children

/** Inferred encoding format from the first sub-chunk. */
val WebpRaw.format: WebpFormat
    get() = chunks.firstOrNull()?.let { WebpFormat.fromChunkId(it.id) } ?: WebpFormat.Lossy

/** `true` when an ALPH or VP8X chunk indicates alpha is present. */
val WebpRaw.hasAlpha: Boolean
    get() {
        val vp8x = chunks.firstOrNull { it.id.value == "VP8X" } ?: return chunks.any { it.id.value == "ALPH" }
        return vp8x.data.size >= 4 && (vp8x.data[0].toInt() and 0x10) != 0
    }

/** `true` when a VP8X chunk indicates animation is present. */
val WebpRaw.hasAnimation: Boolean
    get() {
        val vp8x = chunks.firstOrNull { it.id.value == "VP8X" } ?: return false
        return vp8x.data.size >= 4 && (vp8x.data[0].toInt() and 0x02) != 0
    }

/** Image width parsed from VP8X, VP8L, or VP8 bitstream. */
val WebpRaw.width: Pixels?
    get() {
        val vp8x = chunks.firstOrNull { it.id.value == "VP8X" }
        if (vp8x != null && vp8x.data.size >= 10) {
            val d = vp8x.data.data
            val w = ((d[4].toInt() and 0xFF) or ((d[5].toInt() and 0xFF) shl 8) or ((d[6].toInt() and 0xFF) shl 16)) + 1
            return Pixels(w)
        }
        val vp8l = chunks.firstOrNull { it.id.value == "VP8L" }
        if (vp8l != null && vp8l.data.size >= 5) {
            val d = vp8l.data.data
            val bits = (d[1].toInt() and 0xFF) or ((d[2].toInt() and 0xFF) shl 8) or
                    ((d[3].toInt() and 0xFF) shl 16) or ((d[4].toInt() and 0xFF) shl 24)
            return Pixels((bits and 0x3FFF) + 1)
        }
        val vp8 = chunks.firstOrNull { it.id.value == "VP8 " }
        if (vp8 != null && vp8.data.size >= 10) {
            val d = vp8.data.data
            val w = (d[6].toInt() and 0xFF) or ((d[7].toInt() and 0xFF) shl 8)
            return Pixels(w and 0x3FFF)
        }
        return null
    }

/** Image height parsed from VP8X, VP8L, or VP8 bitstream. */
val WebpRaw.height: Pixels?
    get() {
        val vp8x = chunks.firstOrNull { it.id.value == "VP8X" }
        if (vp8x != null && vp8x.data.size >= 10) {
            val d = vp8x.data.data
            val h = ((d[7].toInt() and 0xFF) or ((d[8].toInt() and 0xFF) shl 8) or ((d[9].toInt() and 0xFF) shl 16)) + 1
            return Pixels(h)
        }
        val vp8l = chunks.firstOrNull { it.id.value == "VP8L" }
        if (vp8l != null && vp8l.data.size >= 5) {
            val d = vp8l.data.data
            val bits = (d[1].toInt() and 0xFF) or ((d[2].toInt() and 0xFF) shl 8) or
                    ((d[3].toInt() and 0xFF) shl 16) or ((d[4].toInt() and 0xFF) shl 24)
            return Pixels(((bits shr 14) and 0x3FFF) + 1)
        }
        val vp8 = chunks.firstOrNull { it.id.value == "VP8 " }
        if (vp8 != null && vp8.data.size >= 10) {
            val d = vp8.data.data
            val h = (d[8].toInt() and 0xFF) or ((d[9].toInt() and 0xFF) shl 8)
            return Pixels(h and 0x3FFF)
        }
        return null
    }
