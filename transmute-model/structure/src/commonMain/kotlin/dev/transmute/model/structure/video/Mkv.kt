@file:Suppress("unused")

package dev.transmute.model.structure.video

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes
import dev.transmute.model.identify.EbmlId
import dev.transmute.model.structure.common.EbmlElement
import dev.transmute.model.core.RawMediaStructure
import kotlinx.serialization.Serializable

// --- Well-known Matroska EBML element IDs ---

/**
 * Well-known Matroska / MKV element identifiers.
 */
object MatroskaIds {
    val EBML           = EbmlId(0x1A45DFA3)
    val Segment        = EbmlId(0x18538067)
    val SeekHead       = EbmlId(0x114D9B74)
    val Info           = EbmlId(0x1549A966)
    val Tracks         = EbmlId(0x1654AE6B)
    val Clusters       = EbmlId(0x1F43B675)
    val Cues           = EbmlId(0x1C53BB6B)
    val Attachments    = EbmlId(0x1941A469)
    val Chapters       = EbmlId(0x1043A770)
    val Tags           = EbmlId(0x1254C367)
    // EBML header children
    val DocType              = EbmlId(0x4282)
    val DocTypeVersion       = EbmlId(0x4287)
    val DocTypeReadVersion   = EbmlId(0x4285)
}

// --- Typed model for EBML header ---

/**
 * Parsed EBML header metadata.
 */
@Serializable
data class EbmlHeaderData(
    val docType: String,
    val docTypeVersion: Int,
    val docTypeReadVersion: Int,
)

// --- MKV file — complete on-disk representation ---

/**
 * Canonical representation of an MKV (Matroska) file as written to disk.
 *
 * An MKV file is an EBML document consisting of an EBML header element
 * followed by a Segment master element:
 * ```
 * | EBML Header | Segment |
 * ```
 *
 * Inside the Segment are SeekHead, Info, Tracks, Clusters, Cues, etc.
 */
@Serializable
data class MkvRaw(
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
val MkvRaw.ebmlHeader: EbmlElement?
    get() = elements.firstOrNull { it.id == MatroskaIds.EBML }

/** The Segment element (contains all media data). */
val MkvRaw.segment: EbmlElement?
    get() = elements.firstOrNull { it.id == MatroskaIds.Segment }

/** Parsed EBML header metadata. */
val MkvRaw.headerData: EbmlHeaderData?
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
val MkvRaw.infoElement: EbmlElement?
    get() = segment?.children?.firstOrNull { it.id == MatroskaIds.Info }

/** Tracks element inside the Segment. */
val MkvRaw.tracksElement: EbmlElement?
    get() = segment?.children?.firstOrNull { it.id == MatroskaIds.Tracks }
internal fun readEbmlUInt(bytes: ByteArray): Long {
    var v = 0L
    for (b in bytes) v = (v shl 8) or (b.toLong() and 0xFF)
    return v
}
