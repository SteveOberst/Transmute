@file:Suppress("unused")

package dev.transmute.model.structure.video

import dev.transmute.model.core.Bytes
import dev.transmute.model.identify.Brand
import dev.transmute.model.structure.common.FtypData
import dev.transmute.model.structure.common.IsoBmffBox
import dev.transmute.model.structure.common.findBox
import dev.transmute.model.structure.common.parseFtyp
import dev.transmute.model.core.RawMediaStructure
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

    override fun toBytes(): Bytes {
        val parts = boxes.map { it.toBytes().data }
        val total = parts.sumOf { it.size }
        val out = ByteArray(total)
        var pos = 0
        for (part in parts) { part.copyInto(out, pos); pos += part.size }
        return Bytes(out)
    }
}

// --- Typed extension accessors ---

/** The `ftyp` box (required by ISO BMFF spec). */
val Mp4Raw.ftypBox: IsoBmffBox? get() = boxes.findBox("ftyp")

/** Parsed `ftyp` data. */
val Mp4Raw.ftyp: FtypData? get() = boxes.parseFtyp()

/** Major brand from the `ftyp` box. */
val Mp4Raw.majorBrand: Brand? get() = ftyp?.majorBrand

/** Minor version from the `ftyp` box. */
val Mp4Raw.minorVersion: UInt? get() = ftyp?.minorVersion

/** Compatible brands from the `ftyp` box. */
val Mp4Raw.compatibleBrands: List<Brand> get() = ftyp?.compatibleBrands ?: emptyList()

/** The `moov` (movie metadata) box (required by spec). */
val Mp4Raw.moovBox: IsoBmffBox? get() = boxes.findBox("moov")

/** The `mdat` (media data) box, or `null`. */
val Mp4Raw.mdatBox: IsoBmffBox? get() = boxes.findBox("mdat")

/** The `free` / `skip` boxes (padding). */
val Mp4Raw.freeBoxes: List<IsoBmffBox>
    get() = boxes.filter { it.type.value == "free" || it.type.value == "skip" }
