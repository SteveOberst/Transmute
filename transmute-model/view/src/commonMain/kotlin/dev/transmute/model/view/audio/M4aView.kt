@file:Suppress("unused")

package dev.transmute.model.view.audio

import dev.transmute.model.identify.Brand
import dev.transmute.model.structure.common.FtypData
import dev.transmute.model.structure.common.IsoBmffBox
import dev.transmute.model.structure.audio.*
import dev.transmute.model.view.StructureView

// ---------------------------------------------------------------------------
// M4aView — read-only view contract for M4A files
// ---------------------------------------------------------------------------

/**
 * Read-only view over an [M4a].
 *
 * | Tier | Class | Use case |
 * |------|-------|----------|
 * | **Immutable** | via [M4a.view] | Read-only inspection |
 * | **Mutable** | [MutableM4aView] | In-memory rebuild |
 */
interface M4aView : StructureView<M4a> {

    /** All top-level ISO BMFF boxes in file order. */
    val boxes: List<IsoBmffBox>

    /** The `ftyp` box. */
    val ftypBox: IsoBmffBox?

    /** Parsed `ftyp` data. */
    val ftyp: FtypData?

    /** Major brand from the `ftyp` box. */
    val majorBrand: Brand?

    /** Minor version from the `ftyp` box. */
    val minorVersion: UInt?

    /** Compatible brands from the `ftyp` box. */
    val compatibleBrands: List<Brand>

    /** The `moov` (movie metadata) box. */
    val moovBox: IsoBmffBox?

    /** The `mdat` (media data) box. */
    val mdatBox: IsoBmffBox?
}

// ---------------------------------------------------------------------------
// ImmutableM4aView
// ---------------------------------------------------------------------------

private class ImmutableM4aView(private val file: M4a) : M4aView {
    override val boxes get() = file.boxes
    override val ftypBox get() = file.ftypBox
    override val ftyp get() = file.ftyp
    override val majorBrand get() = file.majorBrand
    override val minorVersion get() = file.minorVersion
    override val compatibleBrands get() = file.compatibleBrands
    override val moovBox get() = file.moovBox
    override val mdatBox get() = file.mdatBox
}

/**
 * Obtain a read-only [M4aView] over this file.
 */
fun M4a.inspect(): M4aView = ImmutableM4aView(this)
