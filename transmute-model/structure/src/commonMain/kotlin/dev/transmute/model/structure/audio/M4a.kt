@file:Suppress("unused")

package dev.transmute.model.structure.audio

import dev.transmute.model.core.Bytes
import dev.transmute.model.identify.Brand
import dev.transmute.model.structure.common.FtypData
import dev.transmute.model.structure.common.IsoBmffBox
import dev.transmute.model.structure.common.findBox
import dev.transmute.model.structure.common.parseFtyp
import dev.transmute.model.core.RawMediaStructure
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
val M4aRaw.ftypBox: IsoBmffBox? get() = boxes.findBox("ftyp")

/** Parsed `ftyp` data. */
val M4aRaw.ftyp: FtypData? get() = boxes.parseFtyp()

/** Major brand from the `ftyp` box. */
val M4aRaw.majorBrand: Brand? get() = ftyp?.majorBrand

/** Minor version from the `ftyp` box. */
val M4aRaw.minorVersion: UInt? get() = ftyp?.minorVersion

/** Compatible brands from the `ftyp` box. */
val M4aRaw.compatibleBrands: List<Brand> get() = ftyp?.compatibleBrands ?: emptyList()

/** The `moov` (movie metadata) box (required by spec). */
val M4aRaw.moovBox: IsoBmffBox? get() = boxes.findBox("moov")

/** The `mdat` (media data) box, or `null`. */
val M4aRaw.mdatBox: IsoBmffBox? get() = boxes.findBox("mdat")
