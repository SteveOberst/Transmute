@file:Suppress("unused")

package dev.transmute.model.structure.image.types

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.RawMediaStructure
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
import kotlinx.serialization.Serializable

// --- AVIF file - complete on-disk representation ---

/**
 * Canonical representation of an AVIF file as written to disk.
 *
 * AVIF uses the ISO BMFF container with AV1 image codec.  The file
 * is a sequence of top-level boxes:
 * ```
 * | ftyp | meta | mdat | ... |
 * ```
 */
@Serializable
data class AvifRaw(
  /** All top-level ISO BMFF boxes in file order. */
  val boxes: List<IsoBmffBox>,
) : RawMediaStructure {

  // --- Binary serialization ---

  override fun toBytes(): Bytes = boxes.concatToBytes()
}

// --- Typed extension accessors (delegated to shared List<IsoBmffBox> extensions) ---

val AvifRaw.ftypBox: IsoBmffBox? get() = boxes.ftypBox
val AvifRaw.ftyp: FtypData? get() = boxes.ftyp
val AvifRaw.majorBrand: Brand? get() = boxes.majorBrand
val AvifRaw.minorVersion: UInt? get() = boxes.minorVersion
val AvifRaw.compatibleBrands: List<Brand> get() = boxes.compatibleBrands
val AvifRaw.metaBox: IsoBmffBox? get() = boxes.metaBox
val AvifRaw.mdatBox: IsoBmffBox? get() = boxes.mdatBox
