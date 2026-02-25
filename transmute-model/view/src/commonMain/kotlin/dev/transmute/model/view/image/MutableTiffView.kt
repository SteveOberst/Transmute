@file:Suppress("unused")

package dev.transmute.model.view.image

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.Pixels
import dev.transmute.model.identify.Endianness
import dev.transmute.model.structure.image.*
import dev.transmute.model.view.MutableStructureView

// ---------------------------------------------------------------------------
// MutableTiffView — the mutator for Tiff
// ---------------------------------------------------------------------------

/**
 * Mutable view over a [Tiff].
 *
 * Constructor fields ([byteOrder], [firstIfdOffset], [ifds], [imageData],
 * [extraData]) are exposed as `var` properties.  Computed accessors
 * automatically reflect the current mutable state.
 *
 * ```kotlin
 * val edited = tiffFile.edit {
 *     byteOrder = Endianness.BigEndian
 * }
 * ```
 */
open class MutableTiffView internal constructor(
    protected val source: Tiff,
) : TiffView, MutableStructureView<Tiff> {

    // --- Mutable fields ---

    override var byteOrder: Endianness = source.byteOrder
    override var firstIfdOffset: UInt = source.firstIfdOffset
    override var ifds: List<TiffIfd> = source.ifds
    override var imageData: Bytes = source.imageData
    override var extraData: Bytes = source.extraData

    // --- Computed accessors (re-derived from current state) ---

    private fun currentFile() = Tiff(byteOrder, firstIfdOffset, ifds, imageData, extraData)

    override val width: Pixels? get() = currentFile().width
    override val height: Pixels? get() = currentFile().height
    override val bitsPerSample: List<Int> get() = currentFile().bitsPerSample
    override val compression: Int? get() = currentFile().compression

    // --- Build ---

    override fun build(): Tiff = Tiff(
        byteOrder = byteOrder,
        firstIfdOffset = firstIfdOffset,
        ifds = ifds,
        imageData = imageData,
        extraData = extraData,
    )
}

// ---------------------------------------------------------------------------
// edit() extension
// ---------------------------------------------------------------------------

/**
 * Create a modified copy of this [Tiff] by mutating properties
 * inside the [block].
 */
fun Tiff.edit(block: MutableTiffView.() -> Unit): Tiff =
    MutableTiffView(this).apply(block).build()
