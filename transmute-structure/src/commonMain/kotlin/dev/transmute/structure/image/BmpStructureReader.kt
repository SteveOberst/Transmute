@file:Suppress("unused")

package dev.transmute.structure.image

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes
import dev.transmute.model.structure.StructureReadException
import dev.transmute.model.structure.StructureReader
import dev.transmute.model.structure.image.BmpRaw
import dev.transmute.model.structure.image.BmpColorEntry
import dev.transmute.model.structure.image.BmpDibHeader
import dev.transmute.model.structure.image.BmpFileHeader
import dev.transmute.structure.common.readI32LE
import dev.transmute.structure.common.readU16LE
import dev.transmute.structure.common.readU32LE

/**
 * Parses raw BmpRaw file bytes into a [BmpRaw] structure.
 *
 * BmpRaw layout:
 * ```
 * | BmpFileHeader (14 B) | BmpDibHeader (40+ B) | Colour Table | Gap | Pixel Data |
 * ```
 */
class BmpStructureReader : StructureReader<BmpRaw> {

    override fun canRead(source: Bytes): Boolean {
        val d = source.data
        return d.size >= 14 && d[0] == 0x42.toByte() && d[1] == 0x4D.toByte() // "BM"
    }

    override fun read(source: Bytes): BmpRaw {
        val d = source.data
        if (!canRead(source)) throw StructureReadException("Not a BmpRaw file (bad signature)")
        if (d.size < 54) throw StructureReadException("BmpRaw file too small (${d.size} bytes)")

        // --- File header (14 bytes) ---
        val signature = d.readU16LE(0).toUShort()
        val fileSize = d.readU32LE(2)
        val reserved1 = d.readU16LE(6).toUShort()
        val reserved2 = d.readU16LE(8).toUShort()
        val dataOffset = d.readU32LE(10)

        val fileHeader = BmpFileHeader(
            signature = signature,
            fileSize = fileSize,
            reserved1 = reserved1,
            reserved2 = reserved2,
            dataOffset = dataOffset,
        )

        // --- DIB header (40+ bytes) ---
        val dibStart = 14
        val headerSize = d.readU32LE(dibStart)
        if (headerSize < 40u || dibStart + headerSize.toInt() > d.size) {
            throw StructureReadException("Invalid DIB header size: $headerSize")
        }

        val width = d.readI32LE(dibStart + 4)
        val height = d.readI32LE(dibStart + 8)
        val planes = d.readU16LE(dibStart + 12).toUShort()
        val bitsPerPixel = d.readU16LE(dibStart + 14).toUShort()
        val compression = d.readU32LE(dibStart + 16)
        val imageSize = d.readU32LE(dibStart + 20)
        val xPpm = d.readI32LE(dibStart + 24)
        val yPpm = d.readI32LE(dibStart + 28)
        val colorsUsed = d.readU32LE(dibStart + 32)
        val colorsImportant = d.readU32LE(dibStart + 36)

        val extraSize = headerSize.toInt() - 40
        val extraData = if (extraSize > 0 && dibStart + 40 + extraSize <= d.size) {
            d.copyOfRange(dibStart + 40, dibStart + 40 + extraSize).asBytes()
        } else {
            Bytes(ByteArray(0))
        }

        val dibHeader = BmpDibHeader(
            headerSize = headerSize,
            width = width,
            height = height,
            planes = planes,
            bitsPerPixel = bitsPerPixel,
            compression = compression,
            imageSize = imageSize,
            xPixelsPerMeter = xPpm,
            yPixelsPerMeter = yPpm,
            colorsUsed = colorsUsed,
            colorsImportant = colorsImportant,
            extraHeaderData = extraData,
        )

        // --- Colour table ---
        val ctStart = dibStart + headerSize.toInt()
        val numColors = if (colorsUsed > 0u) {
            colorsUsed.toInt()
        } else if (bitsPerPixel.toInt() <= 8) {
            1 shl bitsPerPixel.toInt()
        } else {
            0
        }

        val colorTable = (0 until numColors).mapNotNull { i ->
            val off = ctStart + i * 4
            if (off + 4 <= d.size) {
                BmpColorEntry(
                    blue = d[off].toUByte(),
                    green = d[off + 1].toUByte(),
                    red = d[off + 2].toUByte(),
                    reserved = d[off + 3].toUByte(),
                )
            } else null
        }

        // --- Gap data (between colour table and pixel data) ---
        val ctEnd = ctStart + numColors * 4
        val pixelStart = dataOffset.toInt()
        val gapData = if (pixelStart > ctEnd && pixelStart <= d.size) {
            d.copyOfRange(ctEnd, pixelStart).asBytes()
        } else {
            Bytes(ByteArray(0))
        }

        // --- Pixel data ---
        val pixelData = if (pixelStart < d.size) {
            d.copyOfRange(pixelStart, d.size).asBytes()
        } else {
            Bytes(ByteArray(0))
        }

        return BmpRaw(
            fileHeader = fileHeader,
            dibHeader = dibHeader,
            colorTable = colorTable,
            gapData = gapData,
            pixelData = pixelData,
        )
    }
}
