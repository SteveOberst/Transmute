@file:Suppress("unused")

package dev.transmute.model.view.video

import dev.transmute.model.identify.Brand
import dev.transmute.model.structure.common.FtypData
import dev.transmute.model.structure.common.IsoBmffBox
import dev.transmute.model.structure.video.*
import dev.transmute.model.view.MutableStructureView

// ---------------------------------------------------------------------------
// MutableMovView — the mutator for Mov
// ---------------------------------------------------------------------------

/**
 * Mutable view over a [Mov].
 *
 * The [boxes] list is exposed as a `var`; computed properties
 * ([ftypBox], [ftyp], [moovBox], etc.) automatically reflect the
 * current box list.
 *
 * ```kotlin
 * val edited = movFile.edit {
 *     boxes = boxes.filter { it.type.value != "free" }
 * }
 * ```
 */
open class MutableMovView internal constructor(
    protected val source: Mov,
) : MovView, MutableStructureView<Mov> {

    // --- Mutable field ---

    override var boxes: List<IsoBmffBox> = source.boxes

    // --- Computed accessors (re-derived from current state) ---

    private fun currentFile() = Mov(boxes)

    override val ftypBox: IsoBmffBox? get() = currentFile().ftypBox
    override val ftyp: FtypData? get() = currentFile().ftyp
    override val majorBrand: Brand? get() = currentFile().majorBrand
    override val minorVersion: UInt? get() = currentFile().minorVersion
    override val compatibleBrands: List<Brand> get() = currentFile().compatibleBrands
    override val moovBox: IsoBmffBox? get() = currentFile().moovBox
    override val mdatBox: IsoBmffBox? get() = currentFile().mdatBox

    // --- Build ---

    override fun build(): Mov = Mov(boxes = boxes)
}

// ---------------------------------------------------------------------------
// edit() extension
// ---------------------------------------------------------------------------

/**
 * Create a modified copy of this [Mov] by mutating properties
 * inside the [block].
 */
fun Mov.edit(block: MutableMovView.() -> Unit): Mov =
    MutableMovView(this).apply(block).build()
