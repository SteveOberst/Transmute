@file:Suppress("unused")

package dev.transmute.model.structure.video.types

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.concatToBytes
import dev.transmute.model.identify.Brand
import dev.transmute.model.structure.common.FtypData
import dev.transmute.model.structure.common.IsoBmffBox
import dev.transmute.model.structure.common.compatibleBrands
import dev.transmute.model.structure.common.ftyp
import dev.transmute.model.structure.common.ftypBox
import dev.transmute.model.structure.common.majorBrand
import dev.transmute.model.structure.common.mdatBox
import dev.transmute.model.structure.common.minorVersion
import dev.transmute.model.structure.common.moovBox
import dev.transmute.model.core.RawMediaStructure
import kotlinx.serialization.Serializable

// --- MOV file - complete on-disk representation ---

/**
 * Canonical representation of a MOV (QuickTime) file as written to disk.
 *
 * MOV uses the same ISO BMFF box structure as MP4 but with
 * QuickTime-specific brands and atom semantics.  The file is a
 * sequence of top-level boxes:
 * ```
 * | ftyp | moov | mdat | ... |
 * ```
 *
 * Classic QuickTime files may omit the `ftyp` box entirely.
 */
@Serializable
data class MovRaw(
    /** All top-level ISO BMFF boxes (atoms) in file order. */
    val boxes: List<IsoBmffBox>,
) : RawMediaStructure {

    // --- Binary serialization ---

    override fun toBytes(): Bytes = boxes.concatToBytes()
}

// --- Typed extension accessors (delegated to shared List<IsoBmffBox> extensions) ---

val MovRaw.ftypBox: IsoBmffBox? get() = boxes.ftypBox
val MovRaw.ftyp: FtypData? get() = boxes.ftyp
val MovRaw.majorBrand: Brand? get() = boxes.majorBrand
val MovRaw.minorVersion: UInt? get() = boxes.minorVersion
val MovRaw.compatibleBrands: List<Brand> get() = boxes.compatibleBrands
val MovRaw.moovBox: IsoBmffBox? get() = boxes.moovBox
val MovRaw.mdatBox: IsoBmffBox? get() = boxes.mdatBox
