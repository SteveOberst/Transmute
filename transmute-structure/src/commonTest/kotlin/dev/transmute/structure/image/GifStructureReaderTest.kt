package dev.transmute.structure.image

import dev.transmute.model.core.asBytes
import dev.transmute.model.structure.image.GifVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GifStructureReaderTest {

    private val reader = GifStructureReader()

    /** Minimal valid GIF89a: header + LSD + trailer. */
    private fun minimalGif(): ByteArray {
        val sig = "GIF89a".encodeToByteArray()     // 6 bytes
        val lsd = byteArrayOf(
            0x01, 0x00, // width = 1
            0x01, 0x00, // height = 1
            0x00,       // packed (no GCT)
            0x00,       // bgColorIndex
            0x00,       // pixelAspectRatio
        )
        val trailer = byteArrayOf(0x3B) // trailer
        return sig + lsd + trailer
    }

    @Test
    fun canReadAcceptsGif89a() {
        assertTrue(reader.canRead(minimalGif().asBytes()))
    }

    @Test
    fun canReadAcceptsGif87a() {
        val gif87a = "GIF87a".encodeToByteArray() +
            byteArrayOf(0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x3B)
        assertTrue(reader.canRead(gif87a.asBytes()))
    }

    @Test
    fun canReadRejectsGarbage() {
        assertFalse(reader.canRead(ByteArray(16).asBytes()))
    }

    @Test
    fun canReadRejectsTooShort() {
        assertFalse(reader.canRead(ByteArray(4).asBytes()))
    }

    @Test
    fun readParsesVersion() {
        val gif = reader.read(minimalGif().asBytes())
        assertEquals(GifVersion.Gif89a, gif.version)
    }

    @Test
    fun readParsesScreenDescriptor() {
        val gif = reader.read(minimalGif().asBytes())
        assertEquals(1.toUShort(), gif.screenDescriptor.width)
        assertEquals(1.toUShort(), gif.screenDescriptor.height)
    }

    @Test
    fun readWithGlobalColorTable() {
        val sig = "GIF89a".encodeToByteArray()
        // packed = 0x80 means GCT present, size = 2^(0+1) = 2 entries
        val lsd = byteArrayOf(0x01, 0x00, 0x01, 0x00, 0x80.toByte(), 0x00, 0x00)
        // GCT: 2 entries x 3 bytes
        val gct = byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0x00, 0xFF.toByte(), 0x00)
        val trailer = byteArrayOf(0x3B)
        val data = sig + lsd + gct + trailer
        val gif = reader.read(data.asBytes())
        assertEquals(2, gif.globalColorTable.size)
    }

    @Test
    fun roundTripPreservesSize() {
        val bytes = minimalGif().asBytes()
        val gif = reader.read(bytes)
        val written = gif.toBytes()
        assertEquals(bytes.size, written.size)
    }
}
