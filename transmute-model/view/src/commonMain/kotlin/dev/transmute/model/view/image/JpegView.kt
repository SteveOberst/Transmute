@file:Suppress("unused")

package dev.transmute.model.view.image

import dev.transmute.model.structure.image.*
import dev.transmute.model.view.StructureView

// ---------------------------------------------------------------------------
// JpegView — read-only view contract for JPEG files
// ---------------------------------------------------------------------------

/**
 * Read-only view over a [Jpeg].
 *
 * | Tier | Class | Use case |
 * |------|-------|----------|
 * | **Immutable** | via [Jpeg.view] | Read-only inspection |
 * | **Mutable** | [MutableJpegView] | In-memory rebuild |
 */
interface JpegView : StructureView<Jpeg> {

    /** All segments in file order (SOI first, EOI last). */
    val segments: List<JpegSegment>

    /** Parsed SOF data from the first Start-of-Frame segment. */
    val sofData: JpegSofData?

    /** Parsed JFIF APP0 header. */
    val jfifHeader: JpegJfifHeader?

    /** All comment (COM) segment payloads decoded as UTF-8. */
    val comments: List<String>
}

// ---------------------------------------------------------------------------
// ImmutableJpegView
// ---------------------------------------------------------------------------

private class ImmutableJpegView(private val file: Jpeg) : JpegView {
    override val segments get() = file.segments
    override val sofData get() = file.sofData
    override val jfifHeader get() = file.jfifHeader
    override val comments get() = file.comments
}

/**
 * Obtain a read-only [JpegView] over this file.
 */
fun Jpeg.view(): JpegView = ImmutableJpegView(this)
