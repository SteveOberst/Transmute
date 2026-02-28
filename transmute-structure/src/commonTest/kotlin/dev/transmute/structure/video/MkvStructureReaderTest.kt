package dev.transmute.structure.video

import dev.transmute.model.core.asBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MkvStructureReaderTest {

    private val reader = MkvStructureReader()

    /** Build a minimal EBML file with a given DocType. */
    private fun ebmlFile(docType: String): ByteArray {
        val docBytes = docType.encodeToByteArray()
        // DocType element: id(2) + sizeVint(1) + body
        val docTypeElement = byteArrayOf(0x42, 0x82.toByte(), (0x80 or docBytes.size).toByte()) + docBytes
        // EBML header element: id(4) + sizeVint(1) + body
        val ebmlHeaderBody = docTypeElement
        val ebmlHeader = byteArrayOf(
            0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(),
            (0x80 or ebmlHeaderBody.size).toByte(),
        ) + ebmlHeaderBody
        return ebmlHeader
    }

    @Test
    fun canReadAcceptsMatroska() {
        assertTrue(reader.canRead(ebmlFile("matroska").asBytes()))
    }

    @Test
    fun canReadRejectsWebm() {
        assertFalse(reader.canRead(ebmlFile("webm").asBytes()))
    }

    @Test
    fun canReadRejectsGarbage() {
        assertFalse(reader.canRead(ByteArray(16).asBytes()))
    }

    @Test
    fun readParsesElements() {
        val mkv = reader.read(ebmlFile("matroska").asBytes())
        assertTrue(mkv.elements.isNotEmpty())
    }

    @Test
    fun roundTripPreservesSize() {
        val bytes = ebmlFile("matroska").asBytes()
        val mkv = reader.read(bytes)
        val written = mkv.toBytes()
        assertEquals(bytes.size, written.size)
    }
}
