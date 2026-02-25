@file:Suppress("unused")

package dev.transmute.model.view.image

import dev.transmute.model.structure.image.*
import dev.transmute.model.view.MutableStructureView

// ---------------------------------------------------------------------------
// MutableJpegView — the mutator for Jpeg
// ---------------------------------------------------------------------------

/**
 * Mutable view over a [Jpeg].
 *
 * The [segments] list is exposed as a `var`; computed properties
 * ([sofData], [jfifHeader], [comments]) automatically reflect the
 * current segment list.
 *
 * ```kotlin
 * val edited = jpegFile.edit {
 *     segments = segments.filter { it.marker != 0xFEu.toUShort() } // strip comments
 * }
 * ```
 */
open class MutableJpegView internal constructor(
    protected val source: Jpeg,
) : JpegView, MutableStructureView<Jpeg> {

    // --- Mutable field ---

    override var segments: List<JpegSegment> = source.segments

    // --- Computed accessors (re-derived from current segments) ---

    private fun currentFile() = Jpeg(segments)

    override val sofData: JpegSofData? get() = currentFile().sofData
    override val jfifHeader: JpegJfifHeader? get() = currentFile().jfifHeader
    override val comments: List<String> get() = currentFile().comments

    // --- Build ---

    override fun build(): Jpeg = Jpeg(segments = segments)
}

// ---------------------------------------------------------------------------
// edit() extension
// ---------------------------------------------------------------------------

/**
 * Create a modified copy of this [Jpeg] by mutating properties
 * inside the [block].
 */
fun Jpeg.edit(block: MutableJpegView.() -> Unit): Jpeg =
    MutableJpegView(this).apply(block).build()
