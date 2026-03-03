@file:Suppress("unused")

package dev.transmute.model.structure.image.types

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
import dev.transmute.model.structure.common.metaBox
import dev.transmute.model.structure.common.minorVersion
import dev.transmute.model.core.RawMediaStructure
import kotlinx.serialization.Serializable

// --- HEIF / HEIC file - complete on-disk representation ---

/**
 * Canonical representation of a HEIF/HEIC file as written to disk.
 *
 * Both HEIF and HEIC share the same ISO BMFF container structure;
 * they differ only in the image codec used (H.265 for HEIC, H.264
 * for HEIF).
 *
 * The file is a sequence of top-level boxes:
 * ```
 * | ftyp | meta | mdat | ... |
 * ```
 */
@Serializable
data class HeifRaw(
    /** All top-level ISO BMFF boxes in file order. */
    val boxes: List<IsoBmffBox>,
) : RawMediaStructure {

    // --- Binary serialization ---

    override fun toBytes(): Bytes = boxes.concatToBytes()
}

// --- Typed extension accessors (delegated to shared List<IsoBmffBox> extensions) ---

val HeifRaw.ftypBox: IsoBmffBox? get() = boxes.ftypBox
val HeifRaw.ftyp: FtypData? get() = boxes.ftyp
val HeifRaw.majorBrand: Brand? get() = boxes.majorBrand
val HeifRaw.minorVersion: UInt? get() = boxes.minorVersion
val HeifRaw.compatibleBrands: List<Brand> get() = boxes.compatibleBrands
val HeifRaw.metaBox: IsoBmffBox? get() = boxes.metaBox
val HeifRaw.mdatBox: IsoBmffBox? get() = boxes.mdatBox
