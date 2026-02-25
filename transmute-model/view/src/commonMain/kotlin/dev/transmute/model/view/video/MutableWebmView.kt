@file:Suppress("unused")

package dev.transmute.model.view.video

import dev.transmute.model.structure.common.EbmlElement
import dev.transmute.model.structure.video.*
import dev.transmute.model.view.MutableStructureView

// ---------------------------------------------------------------------------
// MutableWebmView — the mutator for Webm
// ---------------------------------------------------------------------------

/**
 * Mutable view over a [Webm].
 *
 * The [elements] list is exposed as a `var`; computed properties
 * ([ebmlHeader], [segment], [headerData], etc.) automatically reflect
 * the current element list.
 *
 * ```kotlin
 * val edited = webmFile.edit {
 *     elements = elements.filter { it.id != MatroskaIds.Cues }
 * }
 * ```
 */
open class MutableWebmView internal constructor(
    protected val source: Webm,
) : WebmView, MutableStructureView<Webm> {

    // --- Mutable field ---

    override var elements: List<EbmlElement> = source.elements

    // --- Computed accessors (re-derived from current state) ---

    private fun currentFile() = Webm(elements)

    override val ebmlHeader: EbmlElement? get() = currentFile().ebmlHeader
    override val segment: EbmlElement? get() = currentFile().segment
    override val headerData: EbmlHeaderData? get() = currentFile().headerData
    override val infoElement: EbmlElement? get() = currentFile().infoElement
    override val tracksElement: EbmlElement? get() = currentFile().tracksElement

    // --- Build ---

    override fun build(): Webm = Webm(elements = elements)
}

// ---------------------------------------------------------------------------
// edit() extension
// ---------------------------------------------------------------------------

/**
 * Create a modified copy of this [Webm] by mutating properties
 * inside the [block].
 */
fun Webm.edit(block: MutableWebmView.() -> Unit): Webm =
    MutableWebmView(this).apply(block).build()
