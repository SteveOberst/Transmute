@file:Suppress("unused")

package dev.transmute.structure.video

import dev.transmute.model.core.Bytes
import dev.transmute.model.structure.StructureReadException
import dev.transmute.model.structure.StructureReader
import dev.transmute.model.structure.video.MovRaw
import dev.transmute.structure.common.parseIsoBmffBoxes

/**
 * Parses raw MovRaw (QuickTime) file bytes into a [MovRaw] structure.
 *
 * MovRaw uses the ISO BMFF container with brand `qt  ` or classic
 * QuickTime files that may start with a `moov` or `wide` box
 * (no `ftyp`).
 *
 * ```
 * | ftyp box | moov box | mdat box | … |
 * ```
 */
class MovStructureReader : StructureReader<MovRaw> {

    override fun canRead(source: Bytes): Boolean {
        val d = source.data
        if (d.size < 8) return false
        val type = String(CharArray(4) { d[4 + it].toInt().toChar() })

        // Modern MovRaw: ftyp with qt brand
        if (type == "ftyp" && d.size >= 12) {
            val brand = String(CharArray(4) { d[8 + it].toInt().toChar() })
            return brand in MOV_BRANDS
        }

        // Classic QuickTime without ftyp: starts with moov, wide, free, skip, mdat
        return type in CLASSIC_QT_BOXES
    }

    override fun read(source: Bytes): MovRaw {
        val d = source.data
        if (!canRead(source)) throw StructureReadException("Not a MOV file (bad signature)")
        val boxes = d.parseIsoBmffBoxes()
        return MovRaw(boxes = boxes)
    }

    companion object {
        private val MOV_BRANDS = setOf("qt  ", "MSNV", "mqt ")
        private val CLASSIC_QT_BOXES = setOf("moov", "wide", "free", "skip", "mdat", "pnot")
    }
}
