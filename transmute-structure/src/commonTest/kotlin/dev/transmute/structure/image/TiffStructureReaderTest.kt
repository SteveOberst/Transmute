package dev.transmute.structure.image

import dev.transmute.model.core.asBytes
import dev.transmute.model.identify.Endianness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TiffStructureReaderTest {

    private val reader = TiffStructureReader()

    /** Minimal little-endian TIFF: header (8 bytes) + IFD with 0 entries + nextIfdOffset=0. */
    private fun minimalTiffLE(): ByteArray {
        val header = byteArrayOf(
            0x49, 0x49,             // "II" = little-endian
            0x2A, 0x00,             // magic 42 LE
            0x08, 0x00, 0x00, 0x00, // firstIfdOffset = 8 (LE)
        )
        val ifd = byteArrayOf(
            0x00, 0x00,             // entry count = 0 (LE)
            0x00, 0x00, 0x00, 0x00, // nextIfdOffset = 0 (LE)
        )
        return header + ifd
    }

    /** Minimal big-endian TIFF. */
    private fun minimalTiffBE(): ByteArray {
        val header = byteArrayOf(
            0x4D, 0x4D,             // "MM" = big-endian
            0x00, 0x2A,             // magic 42 BE
            0x00, 0x00, 0x00, 0x08, // firstIfdOffset = 8 (BE)
        )
        val ifd = byteArrayOf(
            0x00, 0x00,             // entry count = 0 (BE)
            0x00, 0x00, 0x00, 0x00, // nextIfdOffset = 0 (BE)
        )
        return header + ifd
    }

    @Test
    fun canReadAcceptsLittleEndian() {
        assertTrue(reader.canRead(minimalTiffLE().asBytes()))
    }

    @Test
    fun canReadAcceptsBigEndian() {
        assertTrue(reader.canRead(minimalTiffBE().asBytes()))
    }

    @Test
    fun canReadRejectsGarbage() {
        assertFalse(reader.canRead(ByteArray(16).asBytes()))
    }

    @Test
    fun canReadRejectsTooShort() {
        assertFalse(reader.canRead(ByteArray(3).asBytes()))
    }

    @Test
    fun readParsesLittleEndianHeader() {
        val tiff = reader.read(minimalTiffLE().asBytes())
        assertEquals(Endianness.Little, tiff.byteOrder)
        assertEquals(8u, tiff.firstIfdOffset)
    }

    @Test
    fun readParsesBigEndianHeader() {
        val tiff = reader.read(minimalTiffBE().asBytes())
        assertEquals(Endianness.Big, tiff.byteOrder)
        assertEquals(8u, tiff.firstIfdOffset)
    }

    @Test
    fun readParsesEmptyIfd() {
        val tiff = reader.read(minimalTiffLE().asBytes())
        assertEquals(1, tiff.ifds.size)
        assertEquals(0, tiff.ifds[0].entries.size)
    }

    @Test
    fun roundTripPreservesSize() {
        val bytes = minimalTiffLE().asBytes()
        val tiff = reader.read(bytes)
        val written = tiff.toBytes()
        assertEquals(bytes.size, written.size)
    }
}
