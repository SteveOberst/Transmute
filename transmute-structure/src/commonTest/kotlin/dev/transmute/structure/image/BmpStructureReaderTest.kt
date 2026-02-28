package dev.transmute.structure.image

import dev.transmute.model.core.asBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BmpStructureReaderTest {

    private val reader = BmpStructureReader()

    /** Minimal valid 1x1 24-bit BMP (no color table). */
    private fun minimalBmp(): ByteArray {
        // File header (14 bytes)
        val header = byteArrayOf(
            0x42, 0x4D, // "BM"
            0x3A, 0x00, 0x00, 0x00, // fileSize = 58
            0x00, 0x00, 0x00, 0x00, // reserved
            0x36, 0x00, 0x00, 0x00, // dataOffset = 54
        )
        // DIB header (40 bytes BITMAPINFOHEADER)
        val dib = byteArrayOf(
            0x28, 0x00, 0x00, 0x00, // headerSize = 40
            0x01, 0x00, 0x00, 0x00, // width = 1
            0x01, 0x00, 0x00, 0x00, // height = 1
            0x01, 0x00,             // planes = 1
            0x18, 0x00,             // bitsPerPixel = 24
            0x00, 0x00, 0x00, 0x00, // compression = 0
            0x04, 0x00, 0x00, 0x00, // imageSize = 4 (padded row)
            0x00, 0x00, 0x00, 0x00, // xPPM
            0x00, 0x00, 0x00, 0x00, // yPPM
            0x00, 0x00, 0x00, 0x00, // colorsUsed
            0x00, 0x00, 0x00, 0x00, // importantColors
        )
        // Pixel data: 1 pixel (3 bytes) + 1 pad byte = 4 bytes
        val pixel = byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0x00)
        return header + dib + pixel
    }

    @Test
    fun canReadAcceptsValidBmp() {
        assertTrue(reader.canRead(minimalBmp().asBytes()))
    }

    @Test
    fun canReadRejectsGarbage() {
        assertFalse(reader.canRead(ByteArray(16).asBytes()))
    }

    @Test
    fun readParsesHeaders() {
        val bmp = reader.read(minimalBmp().asBytes())
        assertNotNull(bmp.fileHeader)
        assertNotNull(bmp.dibHeader)
    }

    @Test
    fun roundTripPreservesSize() {
        val bytes = minimalBmp().asBytes()
        val bmp = reader.read(bytes)
        val written = bmp.toBytes()
        assertEquals(bytes.size, written.size)
    }
}
