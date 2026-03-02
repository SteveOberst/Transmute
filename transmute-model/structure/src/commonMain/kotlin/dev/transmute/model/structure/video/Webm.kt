@file:Suppress("unused")

package dev.transmute.model.structure.video

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes
import dev.transmute.model.structure.common.EbmlElement
import dev.transmute.model.core.RawMediaStructure
import kotlinx.serialization.Serializable

// --- WebM file - complete on-disk representation ---

/**
 * Canonical representation of a WebM file as written to disk.
 *
 * WebM is a constrained profile of Matroska (EBML-based) restricted
 * to VP8/VP9/AV1 video and Vorbis/Opus audio.  The on-disk layout
 * is identical to MKV:
 * ```
 * | EBML Header | Segment |
 * ```
 */
@Serializable
data class WebmRaw(
    /** All top-level EBML elements in file order (EBML header + Segment). */
    val elements: List<EbmlElement>,
) : RawMediaStructure {

    // --- Binary serialization ---

    override fun toBytes(): Bytes {
        val parts = elements.map { it.toBytes().data }
        val total = parts.sumOf { it.size }
        val out = ByteArray(total)
        var pos = 0
        for (part in parts) { part.copyInto(out, pos); pos += part.size }
        return out.asBytes()
    }
}

// --- Typed extension accessors ---

/** The EBML header element. */
val WebmRaw.ebmlHeader: EbmlElement?
    get() = elements.firstOrNull { it.id == MatroskaIds.EBML }

/** The Segment element. */
val WebmRaw.segment: EbmlElement?
    get() = elements.firstOrNull { it.id == MatroskaIds.Segment }

/** Parsed EBML header metadata. */
val WebmRaw.headerData: EbmlHeaderData?
    get() {
        val hdr = ebmlHeader ?: return null
        val docType = hdr.children.firstOrNull { it.id == MatroskaIds.DocType }
            ?.data?.data?.decodeToString() ?: return null
        val ver = hdr.children.firstOrNull { it.id == MatroskaIds.DocTypeVersion }
            ?.data?.data?.let { readEbmlUInt(it) }?.toInt() ?: 0
        val readVer = hdr.children.firstOrNull { it.id == MatroskaIds.DocTypeReadVersion }
            ?.data?.data?.let { readEbmlUInt(it) }?.toInt() ?: 0
        return EbmlHeaderData(docType, ver, readVer)
    }

/** Info element inside the Segment. */
val WebmRaw.infoElement: EbmlElement?
    get() = segment?.children?.firstOrNull { it.id == MatroskaIds.Info }

/** Tracks element inside the Segment. */
val WebmRaw.tracksElement: EbmlElement?
    get() = segment?.children?.firstOrNull { it.id == MatroskaIds.Tracks }
