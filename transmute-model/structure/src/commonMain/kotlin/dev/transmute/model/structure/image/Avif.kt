@file:Suppress("unused")

package dev.transmute.model.structure.image

import dev.transmute.model.core.Bytes
import dev.transmute.model.identify.Brand
import dev.transmute.model.structure.common.FtypData
import dev.transmute.model.structure.common.IsoBmffBox
import dev.transmute.model.structure.common.findBox
import dev.transmute.model.structure.common.parseFtyp
import dev.transmute.model.structure.MediaStructure
import kotlinx.serialization.Serializable

// --- AVIF file — complete on-disk representation ---

/**
 * Canonical representation of an AVIF file as written to disk.
 *
 * AVIF uses the ISO BMFF container with AV1 image codec.  The file
 * is a sequence of top-level boxes:
 * ```
 * | ftyp | meta | mdat | … |
 * ```
 */
@Serializable
data class Avif(
    /** All top-level ISO BMFF boxes in file order. */
    val boxes: List<IsoBmffBox>,
) : MediaStructure {

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
val Avif.ftypBox: IsoBmffBox? get() = boxes.findBox("ftyp")

/** Parsed `ftyp` data. */
val Avif.ftyp: FtypData? get() = boxes.parseFtyp()

/** Major brand from the `ftyp` box. */
val Avif.majorBrand: Brand? get() = ftyp?.majorBrand

/** Minor version from the `ftyp` box. */
val Avif.minorVersion: UInt? get() = ftyp?.minorVersion

/** Compatible brands from the `ftyp` box. */
val Avif.compatibleBrands: List<Brand> get() = ftyp?.compatibleBrands ?: emptyList()

/** The `meta` box (required by AVIF spec). */
val Avif.metaBox: IsoBmffBox? get() = boxes.findBox("meta")

/** The `mdat` (media data) box, or `null`. */
val Avif.mdatBox: IsoBmffBox? get() = boxes.findBox("mdat")
