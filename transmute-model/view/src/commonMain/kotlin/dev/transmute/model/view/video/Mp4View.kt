@file:Suppress("unused")

package dev.transmute.model.view.video

import dev.transmute.model.identify.Brand
import dev.transmute.model.structure.common.FtypData
import dev.transmute.model.structure.common.IsoBmffBox
import dev.transmute.model.structure.video.*
import dev.transmute.model.view.StructureView

// ---------------------------------------------------------------------------
// Mp4View — read-only view contract for MP4 files
// ---------------------------------------------------------------------------

/**
 * Read-only view over an [Mp4].
 *
 * | Tier | Class | Use case |
 * |------|-------|----------|
 * | **Immutable** | via [Mp4.inspect] | Read-only inspection |
 * | **Mutable** | [MutableMp4View] | In-memory rebuild |
 */
interface Mp4View : StructureView<Mp4> {

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

    /** The `free` / `skip` boxes (padding). */
    val freeBoxes: List<IsoBmffBox>
}

// ---------------------------------------------------------------------------
// ImmutableMp4View
// ---------------------------------------------------------------------------

private class ImmutableMp4View(private val file: Mp4) : Mp4View {
    override val boxes get() = file.boxes
    override val ftypBox get() = file.ftypBox
    override val ftyp get() = file.ftyp
    override val majorBrand get() = file.majorBrand
    override val minorVersion get() = file.minorVersion
    override val compatibleBrands get() = file.compatibleBrands
    override val moovBox get() = file.moovBox
    override val mdatBox get() = file.mdatBox
    override val freeBoxes get() = file.freeBoxes
}

/**
 * Obtain a read-only [Mp4View] over this file.
 */
fun Mp4.inspect(): Mp4View = ImmutableMp4View(this)
