@file:Suppress("unused")

package dev.transmute.structure.image

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes
import dev.transmute.model.identify.FourCC
import dev.transmute.model.structure.StructureReadException
import dev.transmute.model.structure.StructureReader
import dev.transmute.model.structure.image.PngRaw
import dev.transmute.model.structure.image.PngChunk
import dev.transmute.structure.common.decodeAscii
import dev.transmute.structure.common.readU32BE

/**
 * Parses raw PNG file bytes into a [PngRaw] structure.
 *
 * PngRaw layout:
 * ```
 * | signature (8 B) | chunk₁ | chunk₂ | … | IEND chunk |
 * ```
 *
 * Each chunk:
 * ```
 * | length (4 B BE) | type (4 B ASCII) | data (length B) | crc (4 B) |
 * ```
 */
class PngStructureReader : StructureReader<PngRaw> {

    override fun canRead(source: Bytes): Boolean {
        val d = source.data
        return d.size >= 8 &&
            d[0] == 0x89.toByte() && d[1] == 0x50.toByte() && // \x89P
            d[2] == 0x4E.toByte() && d[3] == 0x47.toByte() && // NG
            d[4] == 0x0D.toByte() && d[5] == 0x0A.toByte() && // \r\n
            d[6] == 0x1A.toByte() && d[7] == 0x0A.toByte()    // \x1a\n
    }

    override fun read(source: Bytes): PngRaw {
        val d = source.data
        if (!canRead(source)) throw StructureReadException("Not a PNG file (bad signature)")

        val signature = Bytes(d.copyOfRange(0, 8))
        val chunks = mutableListOf<PngChunk>()
        var pos = 8

        while (pos + 12 <= d.size) { // minimum chunk: 4 (len) + 4 (type) + 0 (data) + 4 (crc) = 12
            val length = d.readU32BE(pos)
            val type = d.decodeAscii(pos + 4, 4)
            val dataStart = pos + 8
            val dataEnd = dataStart + length.toInt()
            if (dataEnd + 4 > d.size) {
                throw StructureReadException(
                    "PngRaw chunk '$type' at offset $pos overflows file " +
                        "(need ${dataEnd + 4}, have ${d.size})"
                )
            }
            val crc = d.readU32BE(dataEnd)
            val chunkData = if (length > 0u) d.copyOfRange(dataStart, dataEnd) else ByteArray(0)

            chunks += PngChunk(
                length = length,
                type = FourCC(type),
                data = chunkData.asBytes(),
                crc = crc,
            )

            pos = dataEnd + 4 // past CRC
            if (type == "IEND") break
        }

        return PngRaw(signature = signature, chunks = chunks)
    }
}
