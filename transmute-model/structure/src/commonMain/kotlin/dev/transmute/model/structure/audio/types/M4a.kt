@file:Suppress("unused")

package dev.transmute.model.structure.audio.types

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

// --- M4A file - complete on-disk representation ---

/**
 * Canonical representation of an M4A (audio-only ISO BMFF) file as
 * written to disk.
 *
 * M4A uses the same ISO BMFF container as MP4 / MOV but is
 * restricted to audio-only content.  The file is a sequence of
 * top-level boxes:
 * ```
 * | ftyp | moov | mdat | ... |
 * ```
 */
@Serializable
data class M4aRaw(
  /** All top-level ISO BMFF boxes in file order. */
  val boxes: List<IsoBmffBox>,
) : RawMediaStructure {

  // --- Binary serialization ---

  override fun toBytes(): Bytes = boxes.concatToBytes()
}

// --- Typed extension accessors (delegated to shared List<IsoBmffBox> extensions) ---

val M4aRaw.ftypBox: IsoBmffBox? get() = boxes.ftypBox
val M4aRaw.ftyp: FtypData? get() = boxes.ftyp
val M4aRaw.majorBrand: Brand? get() = boxes.majorBrand
val M4aRaw.minorVersion: UInt? get() = boxes.minorVersion
val M4aRaw.compatibleBrands: List<Brand> get() = boxes.compatibleBrands
val M4aRaw.moovBox: IsoBmffBox? get() = boxes.moovBox
val M4aRaw.mdatBox: IsoBmffBox? get() = boxes.mdatBox
