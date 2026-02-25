@file:Suppress("unused")

package dev.transmute.model.view.image

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.Pixels
import dev.transmute.model.identify.Endianness
import dev.transmute.model.structure.image.*
import dev.transmute.model.view.StructureView

// ---------------------------------------------------------------------------
// TiffView — read-only view contract for TIFF files
// ---------------------------------------------------------------------------

/**
 * Read-only view over a [Tiff].
 *
 * | Tier | Class | Use case |
 * |------|-------|----------|
 * | **Immutable** | via [Tiff.view] | Read-only inspection |
 * | **Mutable** | [MutableTiffView] | In-memory rebuild |
 */
interface TiffView : StructureView<Tiff> {

    // --- Constructor fields ---

    /** Byte order: little-endian (II) or big-endian (MM). */
    val byteOrder: Endianness

    /** Offset of the first IFD from the start of the file. */
    val firstIfdOffset: UInt

    /** All IFDs in the file. */
    val ifds: List<TiffIfd>

    /** Concatenated image strip / tile data. */
    val imageData: Bytes

    /** Extra data for round-trip fidelity. */
    val extraData: Bytes

    // --- Computed accessors ---

    /** Image width from IFD 0. */
    val width: Pixels?

    /** Image height from IFD 0. */
    val height: Pixels?

    /** Bits per sample from IFD 0. */
    val bitsPerSample: List<Int>

    /** Compression scheme from IFD 0. */
    val compression: Int?
}

// ---------------------------------------------------------------------------
// ImmutableTiffView
// ---------------------------------------------------------------------------

private class ImmutableTiffView(private val file: Tiff) : TiffView {
    override val byteOrder get() = file.byteOrder
    override val firstIfdOffset get() = file.firstIfdOffset
    override val ifds get() = file.ifds
    override val imageData get() = file.imageData
    override val extraData get() = file.extraData
    override val width get() = file.width
    override val height get() = file.height
    override val bitsPerSample get() = file.bitsPerSample
    override val compression get() = file.compression
}

/**
 * Obtain a read-only [TiffView] over this file.
 */
fun Tiff.view(): TiffView = ImmutableTiffView(this)
