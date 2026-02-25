@file:Suppress("unused")

package dev.transmute.model.structure.common

import dev.transmute.model.identify.Brand
import dev.transmute.model.identify.FourCC
import kotlinx.serialization.Serializable

// ════════════════════════════════════════════════════════════════
//  Typed model: ISO BMFF `ftyp` box data
// ════════════════════════════════════════════════════════════════

/**
 * Parsed contents of an ISO BMFF `ftyp` (file type) box.
 *
 * The `ftyp` box appears at (or near) the start of every ISO
 * base media format file (MP4, MOV, M4A, HEIF, AVIF, …):
 * ```
 * | majorBrand (4 B) | minorVersion (4 B) | compatibleBrands (4 B each) … |
 * ```
 */
@Serializable
data class FtypData(
    /** Primary brand identifier. */
    val majorBrand: Brand,
    /** Minor version / revision number. */
    val minorVersion: UInt,
    /** List of compatible brands. */
    val compatibleBrands: List<Brand>,
) {
    companion object {
        /**
         * Parse [FtypData] from the body bytes of an `ftyp` box.
         *
         * Returns `null` if the data is too short to contain a valid
         * major brand + minor version (8 bytes minimum).
         */
        fun fromBytes(data: ByteArray): FtypData? {
            if (data.size < 8) return null
            val major = Brand(FourCC(data.decodeToString(0, 4)))
            val minor = ((data[4].toUInt() and 0xFFu) shl 24) or
                    ((data[5].toUInt() and 0xFFu) shl 16) or
                    ((data[6].toUInt() and 0xFFu) shl 8) or
                    (data[7].toUInt() and 0xFFu)
            val compat = (8 until data.size step 4).mapNotNull { off ->
                if (off + 4 <= data.size) Brand(FourCC(data.decodeToString(off, off + 4))) else null
            }
            return FtypData(major, minor, compat)
        }
    }
}

// ════════════════════════════════════════════════════════════════
//  Extension helpers for ISO BMFF box lists
// ════════════════════════════════════════════════════════════════

/** Find the first box with the given type code. */
fun List<IsoBmffBox>.findBox(type: String): IsoBmffBox? =
    firstOrNull { it.type.value == type }

/** Parse the `ftyp` box data, or `null` if no ftyp box is present. */
fun List<IsoBmffBox>.parseFtyp(): FtypData? =
    findBox("ftyp")?.data?.data?.let { FtypData.fromBytes(it) }
