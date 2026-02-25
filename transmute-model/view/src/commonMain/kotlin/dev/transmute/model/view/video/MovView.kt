@file:Suppress("unused")

package dev.transmute.model.view.video

import dev.transmute.model.identify.Brand
import dev.transmute.model.structure.common.FtypData
import dev.transmute.model.structure.common.IsoBmffBox
import dev.transmute.model.structure.video.*
import dev.transmute.model.view.StructureView

// ---------------------------------------------------------------------------
// MovView — read-only view contract for MOV (QuickTime) files
// ---------------------------------------------------------------------------

/**
 * Read-only view over a [Mov].
 *
 * | Tier | Class | Use case |
 * |------|-------|----------|
 * | **Immutable** | via [Mov.view] | Read-only inspection |
 * | **Mutable** | [MutableMovView] | In-memory rebuild |
 */
interface MovView : StructureView<Mov> {

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
// ImmutableMovView
// ---------------------------------------------------------------------------

private class ImmutableMovView(private val file: Mov) : MovView {
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
 * Obtain a read-only [MovView] over this file.
 */
fun Mov.inspect(): MovView = ImmutableMovView(this)
