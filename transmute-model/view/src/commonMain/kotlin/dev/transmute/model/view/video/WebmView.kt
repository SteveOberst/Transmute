@file:Suppress("unused")

package dev.transmute.model.view.video

import dev.transmute.model.structure.common.EbmlElement
import dev.transmute.model.structure.video.*
import dev.transmute.model.view.StructureView

// ---------------------------------------------------------------------------
// WebmView — read-only view contract for WebM files
// ---------------------------------------------------------------------------

/**
 * Read-only view over a [Webm].
 *
 * | Tier | Class | Use case |
 * |------|-------|----------|
 * | **Immutable** | via [Webm.inspect] | Read-only inspection |
 * | **Mutable** | [MutableWebmView] | In-memory rebuild |
 */
interface WebmView : StructureView<Webm> {

    /** All top-level EBML elements. */
    val elements: List<EbmlElement>

    // --- Computed accessors ---

    /** The EBML header element. */
    val ebmlHeader: EbmlElement?

    /** The Segment element. */
    val segment: EbmlElement?

    /** Parsed EBML header data (DocType, versions). */
    val headerData: EbmlHeaderData?

    /** The Info element inside Segment. */
    val infoElement: EbmlElement?

    /** The Tracks element inside Segment. */
    val tracksElement: EbmlElement?
}

// ---------------------------------------------------------------------------
// ImmutableWebmView
// ---------------------------------------------------------------------------

private class ImmutableWebmView(private val file: Webm) : WebmView {
    override val elements get() = file.elements
    override val ebmlHeader get() = file.ebmlHeader
    override val segment get() = file.segment
    override val headerData get() = file.headerData
    override val infoElement get() = file.infoElement
    override val tracksElement get() = file.tracksElement
}

/**
 * Obtain a read-only [WebmView] over this file.
 */
fun Webm.inspect(): WebmView = ImmutableWebmView(this)
