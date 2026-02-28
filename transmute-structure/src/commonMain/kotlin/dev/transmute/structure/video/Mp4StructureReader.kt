@file:Suppress("unused")

package dev.transmute.structure.video

import dev.transmute.model.core.Bytes
import dev.transmute.model.structure.StructureReadException
import dev.transmute.model.structure.StructureReader
import dev.transmute.model.structure.video.Mp4
import dev.transmute.structure.common.parseIsoBmffBoxes

/**
 * Parses raw MP4 file bytes into an [Mp4] structure.
 *
 * MP4 uses the ISO BMFF container with brands like `isom`, `mp41`,
 * `mp42`, `iso2`, `iso5`, `iso6`, `dash`, `msdh`, `msix`.
 *
 * ```
 * | ftyp box | moov box | mdat box | … |
 * ```
 */
class Mp4StructureReader : StructureReader<Mp4> {

    override fun canRead(source: Bytes): Boolean {
        val d = source.data
        if (d.size < 12) return false
        // ftyp at bytes 4-7
        if (d[4] != 0x66.toByte() || d[5] != 0x74.toByte() ||
            d[6] != 0x79.toByte() || d[7] != 0x70.toByte()
        ) return false
        val brand = String(CharArray(4) { d[8 + it].toInt().toChar() })
        return brand in MP4_BRANDS
    }

    override fun read(source: Bytes): Mp4 {
        val d = source.data
        if (!canRead(source)) throw StructureReadException("Not an MP4 file (bad ftyp)")
        val boxes = d.parseIsoBmffBoxes()
        return Mp4(boxes = boxes)
    }

    companion object {
        private val MP4_BRANDS = setOf(
            "isom", "iso2", "iso3", "iso4", "iso5", "iso6", "iso7", "iso8", "iso9",
            "mp41", "mp42", "mp71",
            "avc1", "f4v ", "MSNV",
            "dash", "msdh", "msix",
            "3gp4", "3gp5", "3gp6", "3gp7", "3gp8", "3gp9",
            "3g2a", "3g2b", "3g2c",
        )
    }
}
