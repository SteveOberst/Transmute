@file:Suppress("unused")

package dev.transmute.model.view.video

import dev.transmute.model.structure.common.EbmlElement
import dev.transmute.model.structure.video.*
import dev.transmute.model.view.MutableStructureView

// ---------------------------------------------------------------------------
// MutableMkvView — the mutator for Mkv
// ---------------------------------------------------------------------------

/**
 * Mutable view over an [Mkv].
 *
 * The [elements] list is exposed as a `var`; computed properties
 * ([ebmlHeader], [segment], [headerData], etc.) automatically reflect
 * the current element list.
 *
 * ```kotlin
 * val edited = mkvFile.edit {
 *     elements = elements.filter { it.id != MatroskaIds.Cues }
 * }
 * ```
 */
open class MutableMkvView internal constructor(
    protected val source: Mkv,
) : MkvView, MutableStructureView<Mkv> {

    // --- Mutable field ---

    override var elements: List<EbmlElement> = source.elements

    // --- Computed accessors (re-derived from current state) ---

    private fun currentFile() = Mkv(elements)

    override val ebmlHeader: EbmlElement? get() = currentFile().ebmlHeader
    override val segment: EbmlElement? get() = currentFile().segment
    override val headerData: EbmlHeaderData? get() = currentFile().headerData
    override val infoElement: EbmlElement? get() = currentFile().infoElement
    override val tracksElement: EbmlElement? get() = currentFile().tracksElement

    // --- Build ---

    override fun build(): Mkv = Mkv(elements = elements)
}

// ---------------------------------------------------------------------------
// edit() extension
// ---------------------------------------------------------------------------

/**
 * Create a modified copy of this [Mkv] by mutating properties
 * inside the [block].
 */
fun Mkv.edit(block: MutableMkvView.() -> Unit): Mkv =
    MutableMkvView(this).apply(block).build()
