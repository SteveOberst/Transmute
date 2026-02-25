@file:Suppress("unused")

package dev.transmute.model.view.video

import dev.transmute.model.identify.Brand
import dev.transmute.model.structure.common.FtypData
import dev.transmute.model.structure.common.IsoBmffBox
import dev.transmute.model.structure.video.*
import dev.transmute.model.view.MutableStructureView

// ---------------------------------------------------------------------------
// MutableMp4View — the mutator for Mp4
// ---------------------------------------------------------------------------

/**
 * Mutable view over an [Mp4].
 *
 * The [boxes] list is exposed as a `var`; computed properties
 * ([ftypBox], [ftyp], [moovBox], etc.) automatically reflect the
 * current box list.
 *
 * ```kotlin
 * val edited = mp4File.edit {
 *     boxes = boxes.filter { it.type.value != "free" && it.type.value != "skip" }
 * }
 * ```
 */
open class MutableMp4View internal constructor(
    protected val source: Mp4,
) : Mp4View, MutableStructureView<Mp4> {

    // --- Mutable field ---

    override var boxes: List<IsoBmffBox> = source.boxes

    // --- Computed accessors (re-derived from current state) ---

    private fun currentFile() = Mp4(boxes)

    override val ftypBox: IsoBmffBox? get() = currentFile().ftypBox
    override val ftyp: FtypData? get() = currentFile().ftyp
    override val majorBrand: Brand? get() = currentFile().majorBrand
    override val minorVersion: UInt? get() = currentFile().minorVersion
    override val compatibleBrands: List<Brand> get() = currentFile().compatibleBrands
    override val moovBox: IsoBmffBox? get() = currentFile().moovBox
    override val mdatBox: IsoBmffBox? get() = currentFile().mdatBox
    override val freeBoxes: List<IsoBmffBox> get() = currentFile().freeBoxes

    // --- Build ---

    override fun build(): Mp4 = Mp4(boxes = boxes)
}

// ---------------------------------------------------------------------------
// edit() extension
// ---------------------------------------------------------------------------

/**
 * Create a modified copy of this [Mp4] by mutating properties
 * inside the [block].
 */
fun Mp4.edit(block: MutableMp4View.() -> Unit): Mp4 =
    MutableMp4View(this).apply(block).build()
