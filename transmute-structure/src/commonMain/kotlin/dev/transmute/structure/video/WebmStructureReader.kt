@file:Suppress("unused")

package dev.transmute.structure.video

import dev.transmute.model.core.Bytes
import dev.transmute.model.structure.StructureReadException
import dev.transmute.model.structure.StructureReader
import dev.transmute.model.structure.video.WebmRaw
import dev.transmute.structure.common.parseEbmlElements

/**
 * Parses raw WebmRaw file bytes into a [WebmRaw] structure.
 *
 * WebmRaw uses the EBML container format with DocType `webm`.
 *
 * ```
 * | EBML Header | Segment |
 * ```
 */
class WebmStructureReader : StructureReader<WebmRaw> {

    override fun canRead(source: Bytes): Boolean {
        val d = source.data
        if (d.size < 4) return false
        // EBML header magic: 0x1A 0x45 0xDF 0xA3
        if (d[0] != 0x1A.toByte() || d[1] != 0x45.toByte() ||
            d[2] != 0xDF.toByte() || d[3] != 0xA3.toByte()
        ) return false
        // Check DocType is "webm" within first ~64 bytes
        return findDocType(d) == "webm"
    }

    override fun read(source: Bytes): WebmRaw {
        val d = source.data
        if (!canRead(source)) throw StructureReadException("Not a WebmRaw file (bad EBML header or DocType 'webm')")
        val elements = d.parseEbmlElements()
        return WebmRaw(elements = elements)
    }
}

/**
 * Scan the first ~128 bytes for the DocType string element (0x4282).
 * Returns the string value of DocType or null.
 */
private fun findDocType(d: ByteArray): String? {
    val searchEnd = minOf(d.size, 128)
    var pos = 0
    while (pos + 3 < searchEnd) {
        // DocType element ID = 0x4282 (2 bytes)
        if (d[pos] == 0x42.toByte() && d[pos + 1] == 0x82.toByte()) {
            pos += 2
            // Read VINT size
            if (pos >= searchEnd) return null
            val first = d[pos].toInt() and 0xFF
            val sizeLen = when {
                first and 0x80 != 0 -> 1
                first and 0x40 != 0 -> 2
                else -> return null
            }
            val mask = 0xFF shr sizeLen
            var size = (first and mask).toLong()
            for (i in 1 until sizeLen) {
                if (pos + i >= searchEnd) return null
                size = (size shl 8) or (d[pos + i].toLong() and 0xFF)
            }
            pos += sizeLen
            val end = minOf(pos + size.toInt(), d.size)
            if (end <= pos) return null
            return String(CharArray(end - pos) { d[pos + it].toInt().toChar() })
        }
        pos++
    }
    return null
}
