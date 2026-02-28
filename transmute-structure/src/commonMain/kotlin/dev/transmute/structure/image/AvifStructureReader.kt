@file:Suppress("unused")

package dev.transmute.structure.image

import dev.transmute.model.core.Bytes
import dev.transmute.model.structure.StructureReadException
import dev.transmute.model.structure.StructureReader
import dev.transmute.model.structure.image.Avif
import dev.transmute.structure.common.parseIsoBmffBoxes

/**
 * Parses raw AVIF file bytes into an [Avif] structure.
 *
 * AVIF uses ISO BMFF with major brands `avif` or `avis`.
 *
 * ```
 * | ftyp box | meta box | mdat box | … |
 * ```
 */
class AvifStructureReader : StructureReader<Avif> {

    override fun canRead(source: Bytes): Boolean {
        val d = source.data
        if (d.size < 12) return false
        // ftyp at bytes 4-7
        if (d[4] != 0x66.toByte() || d[5] != 0x74.toByte() ||
            d[6] != 0x79.toByte() || d[7] != 0x70.toByte()
        ) return false
        // Major brand at bytes 8-11
        val brand = String(CharArray(4) { d[8 + it].toInt().toChar() })
        return brand in AVIF_BRANDS
    }

    override fun read(source: Bytes): Avif {
        val d = source.data
        if (!canRead(source)) throw StructureReadException("Not an AVIF file (bad ftyp)")
        val boxes = d.parseIsoBmffBoxes()
        return Avif(boxes = boxes)
    }

    companion object {
        private val AVIF_BRANDS = setOf("avif", "avis", "avio")
    }
}
