@file:Suppress("unused")

package dev.transmute.model.view.image

import dev.transmute.model.identify.Brand
import dev.transmute.model.structure.common.FtypData
import dev.transmute.model.structure.common.IsoBmffBox
import dev.transmute.model.structure.image.*
import dev.transmute.model.view.StructureView

// ---------------------------------------------------------------------------
// AvifView — read-only view contract for AVIF files
// ---------------------------------------------------------------------------

/**
 * Read-only view over an [Avif].
 *
 * | Tier | Class | Use case |
 * |------|-------|----------|
 * | **Immutable** | via [Avif.view] | Read-only inspection |
 * | **Mutable** | [MutableAvifView] | In-memory rebuild |
 */
interface AvifView : StructureView<Avif> {

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

    /** The `meta` box. */
    val metaBox: IsoBmffBox?

    /** The `mdat` (media data) box. */
    val mdatBox: IsoBmffBox?
}

// ---------------------------------------------------------------------------
// ImmutableAvifView
// ---------------------------------------------------------------------------

private class ImmutableAvifView(private val file: Avif) : AvifView {
    override val boxes get() = file.boxes
    override val ftypBox get() = file.ftypBox
    override val ftyp get() = file.ftyp
    override val majorBrand get() = file.majorBrand
    override val minorVersion get() = file.minorVersion
    override val compatibleBrands get() = file.compatibleBrands
    override val metaBox get() = file.metaBox
    override val mdatBox get() = file.mdatBox
}

/**
 * Obtain a read-only [AvifView] over this file.
 */
fun Avif.view(): AvifView = ImmutableAvifView(this)
