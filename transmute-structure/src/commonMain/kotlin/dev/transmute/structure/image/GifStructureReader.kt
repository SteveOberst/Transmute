@file:Suppress("unused")

package dev.transmute.structure.image

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes
import dev.transmute.model.structure.StructureReadException
import dev.transmute.model.structure.StructureReader
import dev.transmute.model.structure.image.GifRaw
import dev.transmute.model.structure.image.GifBlock
import dev.transmute.model.structure.image.GifColor
import dev.transmute.model.structure.image.GifLogicalScreenDescriptor
import dev.transmute.model.structure.image.GifVersion
import dev.transmute.structure.common.decodeAscii
import dev.transmute.structure.common.readU16LE

/**
 * Parses raw GIF file bytes into a [GifRaw] structure.
 *
 * GifRaw layout:
 * ```
 * | Signature (6 B) | LSD (7 B) | [GCT] | Block* | Trailer (0x3B) |
 * ```
 */
class GifStructureReader : StructureReader<GifRaw> {

    override fun canRead(source: Bytes): Boolean {
        val d = source.data
        if (d.size < 6) return false
        // GIF87a or GIF89a
        return d[0] == 0x47.toByte() && d[1] == 0x49.toByte() && // "GI"
            d[2] == 0x46.toByte() && d[3] == 0x38.toByte() &&    // "F8"
            (d[4] == 0x37.toByte() || d[4] == 0x39.toByte()) &&  // "7" or "9"
            d[5] == 0x61.toByte()                                  // "a"
    }

    override fun read(source: Bytes): GifRaw {
        val d = source.data
        if (!canRead(source)) throw StructureReadException("Not a GIF file (bad signature)")
        if (d.size < 13) throw StructureReadException("GIF file too small (${d.size} bytes)")

        val sigStr = d.decodeAscii(0, 6)
        val version = GifVersion.fromSignature(sigStr)
            ?: throw StructureReadException("Unknown GIF version: '$sigStr'")

        // Logical Screen Descriptor (7 bytes at offset 6)
        val lsd = GifLogicalScreenDescriptor(
            width = d.readU16LE(6).toUShort(),
            height = d.readU16LE(8).toUShort(),
            packed = d[10].toUByte(),
            backgroundColorIndex = d[11].toUByte(),
            pixelAspectRatio = d[12].toUByte(),
        )

        var pos = 13

        // Global Color Table
        val gct = mutableListOf<GifColor>()
        if (lsd.hasGlobalColorTable) {
            val count = lsd.globalColorTableSize
            for (i in 0 until count) {
                if (pos + 3 > d.size) break
                gct += GifColor(d[pos].toUByte(), d[pos + 1].toUByte(), d[pos + 2].toUByte())
                pos += 3
            }
        }

        // Blocks
        val blocks = mutableListOf<GifBlock>()
        while (pos < d.size) {
            val introducer = d[pos].toUByte()
            pos++

            when (introducer.toInt()) {
                0x3B -> break // Trailer

                0x2C -> {
                    // Image block: 9-byte descriptor + optional LCT + LZW data
                    val blockStart = pos
                    if (pos + 9 > d.size) break
                    val packed = d[pos + 8].toInt() and 0xFF
                    pos += 9

                    // Local Color Table
                    if ((packed and 0x80) != 0) {
                        val lctSize = 1 shl ((packed and 0x07) + 1)
                        pos += lctSize * 3
                    }

                    // LZW min code size + sub-blocks
                    if (pos < d.size) pos++ // LZW minimum code size byte
                    pos = skipSubBlocks(d, pos)

                    val blockData = if (pos <= d.size) d.copyOfRange(blockStart, pos) else d.copyOfRange(blockStart, d.size)
                    blocks += GifBlock(introducer = introducer, data = blockData.asBytes())
                }

                0x21 -> {
                    // Extension block: label + sub-blocks
                    val blockStart = pos
                    if (pos >= d.size) break
                    pos++ // label byte
                    pos = skipSubBlocks(d, pos)

                    val blockData = if (pos <= d.size) d.copyOfRange(blockStart, pos) else d.copyOfRange(blockStart, d.size)
                    blocks += GifBlock(introducer = introducer, data = blockData.asBytes())
                }

                else -> {
                    // Unknown block — store single byte
                    blocks += GifBlock(introducer = introducer)
                }
            }
        }

        return GifRaw(
            version = version,
            screenDescriptor = lsd,
            globalColorTable = gct,
            blocks = blocks,
        )
    }

    /** Skip GifRaw sub-block chain (each sub-block: size byte + data; terminated by 0x00). */
    private fun skipSubBlocks(d: ByteArray, start: Int): Int {
        var pos = start
        while (pos < d.size) {
            val size = d[pos].toInt() and 0xFF
            pos++ // past size byte
            if (size == 0) break // block terminator
            pos += size
        }
        return pos
    }
}
