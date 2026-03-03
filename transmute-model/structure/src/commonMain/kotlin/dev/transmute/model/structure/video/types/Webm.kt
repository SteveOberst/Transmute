@file:Suppress("unused")

package dev.transmute.model.structure.video.types

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.concatToBytes
import dev.transmute.model.structure.common.EbmlElement
import dev.transmute.model.structure.common.headerData
import dev.transmute.model.structure.common.infoElement
import dev.transmute.model.structure.common.tracksElement
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

    override fun toBytes(): Bytes = elements.concatToBytes()
}

// --- Typed extension accessors (delegated to shared List<EbmlElement> extensions) ---

val WebmRaw.ebmlHeader: EbmlElement?
    get() = elements.firstOrNull { it.id == MatroskaIds.EBML }

val WebmRaw.segment: EbmlElement?
    get() = elements.firstOrNull { it.id == MatroskaIds.Segment }

val WebmRaw.headerData: EbmlHeaderData?
    get() = elements.headerData

val WebmRaw.infoElement: EbmlElement?
    get() = elements.infoElement

val WebmRaw.tracksElement: EbmlElement?
    get() = elements.tracksElement
