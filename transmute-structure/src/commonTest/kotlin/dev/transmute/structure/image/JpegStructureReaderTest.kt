package dev.transmute.structure.image

import dev.transmute.model.core.asBytes
import dev.transmute.model.structure.image.types.segments
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JpegStructureReaderTest {

    private val reader = JpegStructureReader()

    private fun minimalJpeg(): ByteArray {
        // SOI + APP0 (minimal) + EOI
        val soi = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        val app0 = byteArrayOf(
            0xFF.toByte(), 0xE0.toByte(),
            0x00, 0x10,  // length = 16
            0x4A, 0x46, 0x49, 0x46, 0x00, // "JFIF\0"
            0x01, 0x01,  // version 1.1
            0x00,        // density units
            0x00, 0x01,  // X density
            0x00, 0x01,  // Y density
            0x00, 0x00,  // thumbnail
        )
        val eoi = byteArrayOf(0xFF.toByte(), 0xD9.toByte())
        return soi + app0 + eoi
    }

    @Test
    fun readParsesSegments() {
        val jpeg = reader.read(minimalJpeg().asBytes())
        assertTrue(jpeg.segments.isNotEmpty())
    }

    @Test
    fun roundTripPreservesBytes() {
        val bytes = minimalJpeg().asBytes()
        val jpeg = reader.read(bytes)
        val written = jpeg.toBytes()
        assertEquals(bytes.size, written.size)
    }
}
