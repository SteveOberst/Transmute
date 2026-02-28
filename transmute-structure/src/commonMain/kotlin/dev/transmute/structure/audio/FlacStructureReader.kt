@file:Suppress("unused")

package dev.transmute.structure.audio

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes
import dev.transmute.model.structure.StructureReadException
import dev.transmute.model.structure.StructureReader
import dev.transmute.model.structure.audio.FlacRaw
import dev.transmute.model.structure.audio.FlacMetadataBlock
import dev.transmute.model.structure.audio.FlacMetadataBlockType

/**
 * Parses raw FlacRaw file bytes into a [FlacRaw] structure.
 *
 * FlacRaw layout:
 * ```
 * | "FlacRaw" (4 B) | metadata block₁ (STREAMINFO) | … | audio frames |
 * ```
 *
 * Each metadata block header:
 * ```
 * | isLast (1 bit) | type (7 bits) | length (24 bits BE) |
 * ```
 */
class FlacStructureReader : StructureReader<FlacRaw> {

    override fun canRead(source: Bytes): Boolean {
        val d = source.data
        return d.size >= 4 &&
            d[0] == 0x66.toByte() && d[1] == 0x4C.toByte() && // "fL"
            d[2] == 0x61.toByte() && d[3] == 0x43.toByte()    // "aC"
    }

    override fun read(source: Bytes): FlacRaw {
        val d = source.data
        if (!canRead(source)) throw StructureReadException("Not a FlacRaw file (bad magic)")

        val blocks = mutableListOf<FlacMetadataBlock>()
        var pos = 4 // skip "FlacRaw"

        while (pos + 4 <= d.size) {
            val headerByte = d[pos].toInt() and 0xFF
            val isLast = (headerByte and 0x80) != 0
            val typeCode = headerByte and 0x7F
            val length = ((d[pos + 1].toInt() and 0xFF) shl 16) or
                ((d[pos + 2].toInt() and 0xFF) shl 8) or
                (d[pos + 3].toInt() and 0xFF)
            pos += 4

            val blockEnd = pos + length
            if (blockEnd > d.size) throw StructureReadException(
                "FlacRaw metadata block (type=$typeCode) at offset ${pos - 4} overflows file"
            )

            val blockData = if (length > 0) d.copyOfRange(pos, blockEnd) else ByteArray(0)
            blocks += FlacMetadataBlock(
                type = FlacMetadataBlockType.fromCode(typeCode),
                isLast = isLast,
                data = blockData.asBytes(),
            )

            pos = blockEnd
            if (isLast) break
        }

        val audioData = if (pos < d.size) {
            d.copyOfRange(pos, d.size).asBytes()
        } else {
            Bytes(ByteArray(0))
        }

        return FlacRaw(
            metadataBlocks = blocks,
            audioData = audioData,
        )
    }
}
