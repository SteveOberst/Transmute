@file:Suppress("unused")

package dev.transmute.structure.image

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes
import dev.transmute.model.identify.Endianness
import dev.transmute.model.structure.StructureReadException
import dev.transmute.model.structure.StructureReader
import dev.transmute.model.structure.image.TiffRaw
import dev.transmute.model.structure.image.TiffIfd
import dev.transmute.model.structure.image.TiffIfdEntry

/**
 * Parses raw TiffRaw file bytes into a [TiffRaw] structure.
 *
 * TiffRaw layout:
 * ```
 * | byte-order (2 B) | magic 42 (2 B) | firstIfdOffset (4 B) | IFDs & data … |
 * ```
 */
class TiffStructureReader : StructureReader<TiffRaw> {

    override fun canRead(source: Bytes): Boolean {
        val d = source.data
        if (d.size < 8) return false
        val le = d[0] == 0x49.toByte() && d[1] == 0x49.toByte() && d[2] == 0x2A.toByte() && d[3] == 0x00.toByte()
        val be = d[0] == 0x4D.toByte() && d[1] == 0x4D.toByte() && d[2] == 0x00.toByte() && d[3] == 0x2A.toByte()
        return le || be
    }

    override fun read(source: Bytes): TiffRaw {
        val d = source.data
        if (!canRead(source)) throw StructureReadException("Not a TiffRaw file (bad signature)")

        val byteOrder = if (d[0] == 0x49.toByte()) Endianness.Little else Endianness.Big
        val firstIfdOffset = readU32(d, 4, byteOrder)

        // Parse IFD chain
        val ifds = mutableListOf<TiffIfd>()
        val visitedOffsets = mutableSetOf<UInt>()
        var nextIfdOff = firstIfdOffset

        while (nextIfdOff != 0u && nextIfdOff.toInt() + 2 <= d.size && visitedOffsets.add(nextIfdOff)) {
            val off = nextIfdOff.toInt()
            val entryCount = readU16(d, off, byteOrder)
            val entriesStart = off + 2
            val entries = mutableListOf<TiffIfdEntry>()

            for (i in 0 until entryCount) {
                val eOff = entriesStart + i * 12
                if (eOff + 12 > d.size) break

                val tag = readU16(d, eOff, byteOrder).toUShort()
                val fieldType = readU16(d, eOff + 2, byteOrder).toUShort()
                val count = readU32(d, eOff + 4, byteOrder)
                val valueOrOffset = d.copyOfRange(eOff + 8, eOff + 12).asBytes()

                // Resolve value data
                val bytesPerValue = tiffFieldSize(fieldType.toInt())
                val totalValueBytes = count.toLong() * bytesPerValue
                val data = if (totalValueBytes <= 4) {
                    valueOrOffset
                } else {
                    val dataOff = readU32(d, eOff + 8, byteOrder).toInt()
                    if (dataOff >= 0 && dataOff + totalValueBytes.toInt() <= d.size) {
                        d.copyOfRange(dataOff, dataOff + totalValueBytes.toInt()).asBytes()
                    } else {
                        valueOrOffset
                    }
                }

                entries += TiffIfdEntry(
                    tag = tag,
                    fieldType = fieldType,
                    count = count,
                    valueOrOffset = valueOrOffset,
                    data = data,
                )
            }

            val nextOff = entriesStart + entryCount * 12
            val nextIfd = if (nextOff + 4 <= d.size) readU32(d, nextOff, byteOrder) else 0u
            ifds += TiffIfd(entries = entries, nextIfdOffset = nextIfd)
            nextIfdOff = nextIfd
        }

        // The remaining data after header + IFDs is image/extra data.
        // For simplicity we leave imageData and extraData empty — the IFD
        // entries contain resolved value pointers already.
        return TiffRaw(
            byteOrder = byteOrder,
            firstIfdOffset = firstIfdOffset,
            ifds = ifds,
        )
    }
}

// --- Byte helpers ---

private fun readU16(d: ByteArray, off: Int, order: Endianness): Int = when (order) {
    Endianness.Little -> (d[off].toInt() and 0xFF) or ((d[off + 1].toInt() and 0xFF) shl 8)
    Endianness.Big -> ((d[off].toInt() and 0xFF) shl 8) or (d[off + 1].toInt() and 0xFF)
}

private fun readU32(d: ByteArray, off: Int, order: Endianness): UInt = when (order) {
    Endianness.Little ->
        (d[off].toUInt() and 0xFFu) or
            ((d[off + 1].toUInt() and 0xFFu) shl 8) or
            ((d[off + 2].toUInt() and 0xFFu) shl 16) or
            ((d[off + 3].toUInt() and 0xFFu) shl 24)
    Endianness.Big ->
        ((d[off].toUInt() and 0xFFu) shl 24) or
            ((d[off + 1].toUInt() and 0xFFu) shl 16) or
            ((d[off + 2].toUInt() and 0xFFu) shl 8) or
            (d[off + 3].toUInt() and 0xFFu)
}

/** Bytes per value for the given TiffRaw field type code. */
private fun tiffFieldSize(typeCode: Int): Int = when (typeCode) {
    1, 2, 6, 7 -> 1   // BYTE, ASCII, SBYTE, UNDEFINED
    3, 8       -> 2   // SHORT, SSHORT
    4, 9, 11   -> 4   // LONG, SLONG, FLOAT
    5, 10, 12  -> 8   // RATIONAL, SRATIONAL, DOUBLE
    else       -> 1
}
