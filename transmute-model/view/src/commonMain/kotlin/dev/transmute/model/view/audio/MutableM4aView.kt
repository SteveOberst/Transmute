@file:Suppress("unused")

package dev.transmute.model.view.audio

import dev.transmute.model.identify.Brand
import dev.transmute.model.structure.common.FtypData
import dev.transmute.model.structure.common.IsoBmffBox
import dev.transmute.model.structure.audio.*
import dev.transmute.model.view.MutableStructureView

// ---------------------------------------------------------------------------
// MutableM4aView — the mutator for M4a
// ---------------------------------------------------------------------------

/**
 * Mutable view over an [M4a].
 *
 * The [boxes] list is exposed as a `var`; computed properties
 * ([ftypBox], [ftyp], [moovBox], etc.) automatically reflect the
 * current box list.
 *
 * ```kotlin
 * val edited = m4aFile.edit {
 *     boxes = boxes.filter { it.type.value != "free" }
 * }
 * ```
 */
open class MutableM4aView internal constructor(
    protected val source: M4a,
) : M4aView, MutableStructureView<M4a> {

    // --- Mutable field ---

    override var boxes: List<IsoBmffBox> = source.boxes

    // --- Computed accessors (re-derived from current state) ---

    private fun currentFile() = M4a(boxes)

    override val ftypBox: IsoBmffBox? get() = currentFile().ftypBox
    override val ftyp: FtypData? get() = currentFile().ftyp
    override val majorBrand: Brand? get() = currentFile().majorBrand
    override val minorVersion: UInt? get() = currentFile().minorVersion
    override val compatibleBrands: List<Brand> get() = currentFile().compatibleBrands
    override val moovBox: IsoBmffBox? get() = currentFile().moovBox
    override val mdatBox: IsoBmffBox? get() = currentFile().mdatBox

    // --- Build ---

    override fun build(): M4a = M4a(boxes = boxes)
}

// ---------------------------------------------------------------------------
// edit() extension
// ---------------------------------------------------------------------------

/**
 * Create a modified copy of this [M4a] by mutating properties
 * inside the [block].
 */
fun M4a.edit(block: MutableM4aView.() -> Unit): M4a =
    MutableM4aView(this).apply(block).build()
