@file:Suppress("unused")

package dev.transmute.model.structure.video.types

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.concatToBytes
import dev.transmute.model.identify.EbmlId
import dev.transmute.model.structure.common.EbmlElement
import dev.transmute.model.structure.common.headerData
import dev.transmute.model.structure.common.infoElement
import dev.transmute.model.structure.common.tracksElement
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
    // Tag sub-elements
    val Tag            = EbmlId(0x7373)
    val Targets        = EbmlId(0x63C0)
    val TargetTypeValue = EbmlId(0x68CA)
    val TargetType     = EbmlId(0x63CA)
    val SimpleTag      = EbmlId(0x67C8)
    val TagName        = EbmlId(0x45A3)
    val TagLanguage    = EbmlId(0x447A)
    val TagDefault     = EbmlId(0x4484)
    val TagString      = EbmlId(0x4487)
    val TagBinary      = EbmlId(0x4485)
    // Target UID elements
    val TagTrackUID       = EbmlId(0x63C5)
    val TagEditionUID     = EbmlId(0x63C9)
    val TagChapterUID     = EbmlId(0x63C4)
    val TagAttachmentUID  = EbmlId(0x63C6)
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

// --- MKV file - complete on-disk representation ---

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

    override fun toBytes(): Bytes = elements.concatToBytes()
}

// --- Typed extension accessors (delegated to shared List<EbmlElement> extensions) ---

val MkvRaw.ebmlHeader: EbmlElement?
    get() = elements.firstOrNull { it.id == MatroskaIds.EBML }

val MkvRaw.segment: EbmlElement?
    get() = elements.firstOrNull { it.id == MatroskaIds.Segment }

val MkvRaw.headerData: EbmlHeaderData?
    get() = elements.headerData

val MkvRaw.infoElement: EbmlElement?
    get() = elements.infoElement

val MkvRaw.tracksElement: EbmlElement?
    get() = elements.tracksElement