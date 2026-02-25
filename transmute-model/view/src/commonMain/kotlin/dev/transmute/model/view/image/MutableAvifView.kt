@file:Suppress("unused")

package dev.transmute.model.view.image

import dev.transmute.model.identify.Brand
import dev.transmute.model.structure.common.FtypData
import dev.transmute.model.structure.common.IsoBmffBox
import dev.transmute.model.structure.image.*
import dev.transmute.model.view.MutableStructureView

// ---------------------------------------------------------------------------
// MutableAvifView — the mutator for Avif
// ---------------------------------------------------------------------------

/**
 * Mutable view over an [Avif].
 *
 * The [boxes] list is exposed as a `var`; computed properties
 * ([ftypBox], [ftyp], [majorBrand], etc.) automatically reflect the
 * current box list.
 *
 * ```kotlin
 * val edited = avifFile.edit {
 *     boxes = boxes.filter { it.type.value != "free" }
 * }
 * ```
 */
open class MutableAvifView internal constructor(
    protected val source: Avif,
) : AvifView, MutableStructureView<Avif> {

    // --- Mutable field ---

    override var boxes: List<IsoBmffBox> = source.boxes

    // --- Computed accessors (re-derived from current state) ---

    private fun currentFile() = Avif(boxes)

    override val ftypBox: IsoBmffBox? get() = currentFile().ftypBox
    override val ftyp: FtypData? get() = currentFile().ftyp
    override val majorBrand: Brand? get() = currentFile().majorBrand
    override val minorVersion: UInt? get() = currentFile().minorVersion
    override val compatibleBrands: List<Brand> get() = currentFile().compatibleBrands
    override val metaBox: IsoBmffBox? get() = currentFile().metaBox
    override val mdatBox: IsoBmffBox? get() = currentFile().mdatBox

    // --- Build ---

    override fun build(): Avif = Avif(boxes = boxes)
}

// ---------------------------------------------------------------------------
// edit() extension
// ---------------------------------------------------------------------------

/**
 * Create a modified copy of this [Avif] by mutating properties
 * inside the [block].
 */
fun Avif.edit(block: MutableAvifView.() -> Unit): Avif =
    MutableAvifView(this).apply(block).build()
