@file:Suppress("unused")

package dev.transmute.model.view.image

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.Pixels
import dev.transmute.model.structure.image.*
import dev.transmute.model.view.MutableStructureView

// ---------------------------------------------------------------------------
// MutableBmpView — the mutator for Bmp
// ---------------------------------------------------------------------------

/**
 * Mutable view over a [Bmp].
 *
 * Constructor fields ([fileHeader], [dibHeader], [colorTable], [gapData],
 * [pixelData]) are exposed as `var` properties.  Computed accessors
 * automatically reflect the current mutable state.
 *
 * ```kotlin
 * val edited = bmpFile.edit {
 *     dibHeader = dibHeader.copy(width = 640)
 * }
 * ```
 */
open class MutableBmpView internal constructor(
    protected val source: Bmp,
) : BmpView, MutableStructureView<Bmp> {

    // --- Mutable fields ---

    override var fileHeader: BmpFileHeader = source.fileHeader
    override var dibHeader: BmpDibHeader = source.dibHeader
    override var colorTable: List<BmpColorEntry> = source.colorTable
    override var gapData: Bytes = source.gapData
    override var pixelData: Bytes = source.pixelData

    // --- Computed accessors (re-derived from current state) ---

    private fun currentFile() = Bmp(fileHeader, dibHeader, colorTable, gapData, pixelData)

    override val width: Pixels get() = currentFile().width
    override val height: Pixels get() = currentFile().height
    override val isTopDown: Boolean get() = currentFile().isTopDown
    override val bitsPerPixel: Int get() = currentFile().bitsPerPixel
    override val compression: BmpCompression? get() = currentFile().compression
    override val rowStride: Int get() = currentFile().rowStride

    // --- Build ---

    override fun build(): Bmp = Bmp(
        fileHeader = fileHeader,
        dibHeader = dibHeader,
        colorTable = colorTable,
        gapData = gapData,
        pixelData = pixelData,
    )
}

// ---------------------------------------------------------------------------
// edit() extension
// ---------------------------------------------------------------------------

/**
 * Create a modified copy of this [Bmp] by mutating properties
 * inside the [block].
 */
fun Bmp.edit(block: MutableBmpView.() -> Unit): Bmp =
    MutableBmpView(this).apply(block).build()
