@file:Suppress("unused")

package dev.transmute.model.view.image

import dev.transmute.model.identify.Brand
import dev.transmute.model.structure.common.FtypData
import dev.transmute.model.structure.common.IsoBmffBox
import dev.transmute.model.structure.image.*
import dev.transmute.model.view.StructureView

// ---------------------------------------------------------------------------
// HeifView — read-only view contract for HEIF/HEIC files
// ---------------------------------------------------------------------------

/**
 * Read-only view over a [Heif].
 *
 * | Tier | Class | Use case |
 * |------|-------|----------|
 * | **Immutable** | via [Heif.view] | Read-only inspection |
 * | **Mutable** | [MutableHeifView] | In-memory rebuild |
 */
interface HeifView : StructureView<Heif> {

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
// ImmutableHeifView
// ---------------------------------------------------------------------------

private class ImmutableHeifView(private val file: Heif) : HeifView {
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
 * Obtain a read-only [HeifView] over this file.
 */
fun Heif.view(): HeifView = ImmutableHeifView(this)
