@file:Suppress("unused")

package dev.transmute.structure.audio

import dev.transmute.model.core.Bytes
import dev.transmute.model.structure.StructureReadException
import dev.transmute.model.structure.StructureReader
import dev.transmute.model.structure.audio.M4aRaw
import dev.transmute.structure.common.parseIsoBmffBoxes

/**
 * Parses raw M4A file bytes into an [M4aRaw] structure.
 *
 * M4aRaw is an audio-only ISO BMFF container with brands like `M4A `,
 * `M4B `, `mp42`, or `isom` containing only audio tracks.
 *
 * ```
 * | ftyp box | moov box | mdat box | ... |
 * ```
 */
class M4aStructureReader : StructureReader<M4aRaw> {

    override fun canRead(source: Bytes): Boolean {
        val d = source.data
        if (d.size < 12) return false
        // ftyp at bytes 4-7
        if (d[4] != 0x66.toByte() || d[5] != 0x74.toByte() ||
            d[6] != 0x79.toByte() || d[7] != 0x70.toByte()
        ) return false
        // Major brand at bytes 8-11
        val brand = String(CharArray(4) { d[8 + it].toInt().toChar() })
        return brand in M4A_BRANDS
    }

    override fun read(source: Bytes): M4aRaw {
        val d = source.data
        if (!canRead(source)) throw StructureReadException("Not an M4A file (bad ftyp)")
        val boxes = d.parseIsoBmffBoxes()
        return M4aRaw(boxes = boxes)
    }

    companion object {
        private val M4A_BRANDS = setOf("M4A ", "M4B ", "mp42")
    }
}
