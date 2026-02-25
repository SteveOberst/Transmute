@file:Suppress("unused")

package dev.transmute.model.view.video

import dev.transmute.model.structure.common.EbmlElement
import dev.transmute.model.structure.video.*
import dev.transmute.model.view.StructureView

// ---------------------------------------------------------------------------
// MkvView — read-only view contract for MKV (Matroska) files
// ---------------------------------------------------------------------------

/**
 * Read-only view over an [Mkv].
 *
 * | Tier | Class | Use case |
 * |------|-------|----------|
 * | **Immutable** | via [Mkv.inspect] | Read-only inspection |
 * | **Mutable** | [MutableMkvView] | In-memory rebuild |
 */
interface MkvView : StructureView<Mkv> {

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
// ImmutableMkvView
// ---------------------------------------------------------------------------

private class ImmutableMkvView(private val file: Mkv) : MkvView {
    override val elements get() = file.elements
    override val ebmlHeader get() = file.ebmlHeader
    override val segment get() = file.segment
    override val headerData get() = file.headerData
    override val infoElement get() = file.infoElement
    override val tracksElement get() = file.tracksElement
}

/**
 * Obtain a read-only [MkvView] over this file.
 */
fun Mkv.inspect(): MkvView = ImmutableMkvView(this)
