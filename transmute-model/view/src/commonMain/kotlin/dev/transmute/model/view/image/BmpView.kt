@file:Suppress("unused")

package dev.transmute.model.view.image

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.Pixels
import dev.transmute.model.structure.image.*
import dev.transmute.model.view.StructureView

// ---------------------------------------------------------------------------
// BmpView — read-only view contract for BMP files
// ---------------------------------------------------------------------------

/**
 * Read-only view over a [Bmp].
 *
 * | Tier | Class | Use case |
 * |------|-------|----------|
 * | **Immutable** | via [Bmp.view] | Read-only inspection |
 * | **Mutable** | [MutableBmpView] | In-memory rebuild |
 */
interface BmpView : StructureView<Bmp> {

    // --- Constructor fields ---

    /** The 14-byte BMP file header. */
    val fileHeader: BmpFileHeader

    /** The DIB (info) header. */
    val dibHeader: BmpDibHeader

    /** Colour-table entries (RGBQUAD array). */
    val colorTable: List<BmpColorEntry>

    /** Bytes between the colour table and pixel data. */
    val gapData: Bytes

    /** Raw pixel data. */
    val pixelData: Bytes

    // --- Computed accessors ---

    /** Image width in pixels (always positive). */
    val width: Pixels

    /** Image height in pixels (always positive). */
    val height: Pixels

    /** `true` when rows are stored top-to-bottom. */
    val isTopDown: Boolean

    /** Bits per pixel (1, 4, 8, 16, 24, or 32). */
    val bitsPerPixel: Int

    /** Resolved compression method. */
    val compression: BmpCompression?

    /** Bytes per pixel row including padding. */
    val rowStride: Int
}

// ---------------------------------------------------------------------------
// ImmutableBmpView
// ---------------------------------------------------------------------------

private class ImmutableBmpView(private val file: Bmp) : BmpView {
    override val fileHeader get() = file.fileHeader
    override val dibHeader get() = file.dibHeader
    override val colorTable get() = file.colorTable
    override val gapData get() = file.gapData
    override val pixelData get() = file.pixelData
    override val width get() = file.width
    override val height get() = file.height
    override val isTopDown get() = file.isTopDown
    override val bitsPerPixel get() = file.bitsPerPixel
    override val compression get() = file.compression
    override val rowStride get() = file.rowStride
}

/**
 * Obtain a read-only [BmpView] over this file.
 */
fun Bmp.view(): BmpView = ImmutableBmpView(this)
