@file:Suppress("unused")

package dev.transmute.model.structure.video.types

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
import dev.transmute.model.structure.common.minorVersion
import dev.transmute.model.structure.common.moovBox
import kotlinx.serialization.Serializable

// --- MP4 file - complete on-disk representation ---

/**
 * Canonical representation of an MP4 (ISO BMFF) file as written to disk.
 *
 * The file is a sequence of top-level boxes:
 * ```
 * | ftyp | moov | mdat | ... |
 * ```
 */
@Serializable
data class Mp4Raw(
  /** All top-level ISO BMFF boxes in file order. */
  val boxes: List<IsoBmffBox>,
) : RawMediaStructure {

  // --- Binary serialization ---

  override fun toBytes(): Bytes = boxes.concatToBytes()
}

// --- Typed extension accessors (delegated to shared List<IsoBmffBox> extensions) ---

val Mp4Raw.ftypBox: IsoBmffBox? get() = boxes.ftypBox
val Mp4Raw.ftyp: FtypData? get() = boxes.ftyp
val Mp4Raw.majorBrand: Brand? get() = boxes.majorBrand
val Mp4Raw.minorVersion: UInt? get() = boxes.minorVersion
val Mp4Raw.compatibleBrands: List<Brand> get() = boxes.compatibleBrands
val Mp4Raw.moovBox: IsoBmffBox? get() = boxes.moovBox
val Mp4Raw.mdatBox: IsoBmffBox? get() = boxes.mdatBox

/** The `free` / `skip` boxes (padding). */
val Mp4Raw.freeBoxes: List<IsoBmffBox>
  get() = boxes.filter { it.type.value == "free" || it.type.value == "skip" }
