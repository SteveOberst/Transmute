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

/** The `ftyp` box, or `null` if not present (classic QuickTime may omit it). */
val MovRaw.ftypBox: IsoBmffBox? get() = boxes.findBox("ftyp")

/** Parsed `ftyp` data. */
val MovRaw.ftyp: FtypData? get() = boxes.parseFtyp()

/** Major brand from the `ftyp` box. */
val MovRaw.majorBrand: Brand? get() = ftyp?.majorBrand

/** Minor version from the `ftyp` box. */
val MovRaw.minorVersion: UInt? get() = ftyp?.minorVersion

/** Compatible brands from the `ftyp` box. */
val MovRaw.compatibleBrands: List<Brand> get() = ftyp?.compatibleBrands ?: emptyList()

/** The `moov` (movie metadata) box (required by spec). */
val MovRaw.moovBox: IsoBmffBox? get() = boxes.findBox("moov")

/** The `mdat` (media data) box, or `null`. */
val MovRaw.mdatBox: IsoBmffBox? get() = boxes.findBox("mdat")
