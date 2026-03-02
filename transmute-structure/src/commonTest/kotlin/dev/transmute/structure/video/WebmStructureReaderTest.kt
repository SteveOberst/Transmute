package dev.transmute.structure.video

import dev.transmute.model.core.asBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebmStructureReaderTest {

    private val reader = WebmStructureReader()

    /**
     * Build a minimal EBML file with a given DocType.
     *
     * EBML header element (id=0x1A45DFA3):
     * ```
     * 1A 45 DF A3       - EBML header element ID
     * 80 | size (VINT)   - element size (in 1-byte VINT form)
     *   42 82            - DocType element ID
     *   80 | size         - DocType string length (VINT)
     *   ...doctype...     - ASCII string
     * ```
     */
    private fun ebmlFile(docType: String): ByteArray {
        val docBytes = docType.encodeToByteArray()
        // DocType element: id(2) + sizeVint(1) + body
        val docTypeElement = byteArrayOf(0x42, 0x82.toByte(), (0x80 or docBytes.size).toByte()) + docBytes
        // EBML header element: id(4) + sizeVint(1) + body
        val ebmlHeaderBody = docTypeElement
        val ebmlHeader = byteArrayOf(
            0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), // EBML id
            (0x80 or ebmlHeaderBody.size).toByte(),     // size as 1-byte VINT
        ) + ebmlHeaderBody
        return ebmlHeader
    }

    @Test
    fun canReadAcceptsWebm() {
        assertTrue(reader.canRead(ebmlFile("webm").asBytes()))
    }

    @Test
    fun canReadRejectsMatroska() {
        assertFalse(reader.canRead(ebmlFile("matroska").asBytes()))
    }

    @Test
    fun canReadRejectsGarbage() {
        assertFalse(reader.canRead(ByteArray(16).asBytes()))
    }

    @Test
    fun canReadRejectsTooShort() {
        assertFalse(reader.canRead(ByteArray(2).asBytes()))
    }

    @Test
    fun readParsesElements() {
        val webm = reader.read(ebmlFile("webm").asBytes())
        assertTrue(webm.elements.isNotEmpty())
    }

    @Test
    fun roundTripPreservesSize() {
        val bytes = ebmlFile("webm").asBytes()
        val webm = reader.read(bytes)
        val written = webm.toBytes()
        assertEquals(bytes.size, written.size)
    }
}
