@file:Suppress("unused")

package dev.transmute.model.structure.image

import dev.transmute.model.core.Bytes
import dev.transmute.model.identify.Brand
import dev.transmute.model.structure.common.FtypData
import dev.transmute.model.structure.common.IsoBmffBox
import dev.transmute.model.structure.common.findBox
import dev.transmute.model.structure.common.parseFtyp
import dev.transmute.model.core.RawMediaStructure
import kotlinx.serialization.Serializable

// --- HEIF / HEIC file — complete on-disk representation ---

/**
 * Canonical representation of a HEIF/HEIC file as written to disk.
 *
 * Both HEIF and HEIC share the same ISO BMFF container structure;
 * they differ only in the image codec used (H.265 for HEIC, H.264
 * for HEIF).
 *
 * The file is a sequence of top-level boxes:
 * ```
 * | ftyp | meta | mdat | … |
 * ```
 */
@Serializable
data class HeifRaw(
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
val HeifRaw.ftypBox: IsoBmffBox? get() = boxes.findBox("ftyp")

/** Parsed `ftyp` data. */
val HeifRaw.ftyp: FtypData? get() = boxes.parseFtyp()

/** Major brand from the `ftyp` box. */
val HeifRaw.majorBrand: Brand? get() = ftyp?.majorBrand

/** Minor version from the `ftyp` box. */
val HeifRaw.minorVersion: UInt? get() = ftyp?.minorVersion

/** Compatible brands from the `ftyp` box. */
val HeifRaw.compatibleBrands: List<Brand> get() = ftyp?.compatibleBrands ?: emptyList()

/** The `meta` box (required by HEIF spec). */
val HeifRaw.metaBox: IsoBmffBox? get() = boxes.findBox("meta")

/** The `mdat` (media data) box, or `null`. */
val HeifRaw.mdatBox: IsoBmffBox? get() = boxes.findBox("mdat")
