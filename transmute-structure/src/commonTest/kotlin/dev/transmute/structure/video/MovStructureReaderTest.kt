package dev.transmute.structure.video

import dev.transmute.model.core.asBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MovStructureReaderTest {

    private val reader = MovStructureReader()

    /** Build a minimal ISO BMFF file: ftyp box with given major brand. */
    private fun ftypFile(brand: String): ByteArray {
        require(brand.length == 4)
        val size = 16
        val out = ByteArray(size)
        out[0] = 0; out[1] = 0; out[2] = 0; out[3] = size.toByte()
        "ftyp".encodeToByteArray().copyInto(out, 4)
        brand.encodeToByteArray().copyInto(out, 8)
        return out
    }

    /** Build a minimal QuickTime file starting with a classic box type. */
    private fun classicMovBox(boxType: String): ByteArray {
        require(boxType.length == 4)
        val boxSize = 8 // header only, empty box body
        val out = ByteArray(boxSize)
        out[0] = 0; out[1] = 0; out[2] = 0; out[3] = boxSize.toByte()
        boxType.encodeToByteArray().copyInto(out, 4)
        return out
    }

    @Test
    fun canReadAcceptsQtBrand() {
        assertTrue(reader.canRead(ftypFile("qt  ").asBytes()))
    }

    @Test
    fun canReadAcceptsClassicMoov() {
        assertTrue(reader.canRead(classicMovBox("moov").asBytes()))
    }

    @Test
    fun canReadAcceptsClassicWide() {
        assertTrue(reader.canRead(classicMovBox("wide").asBytes()))
    }

    @Test
    fun canReadRejectsIsom() {
        assertFalse(reader.canRead(ftypFile("isom").asBytes()))
    }

    @Test
    fun canReadRejectsGarbage() {
        assertFalse(reader.canRead(ByteArray(16).asBytes()))
    }

    @Test
    fun readParsesBoxesFromFtyp() {
        val mov = reader.read(ftypFile("qt  ").asBytes())
        assertTrue(mov.boxes.isNotEmpty())
        assertEquals("ftyp", mov.boxes[0].type.value)
    }

    @Test
    fun readParsesClassicBox() {
        val mov = reader.read(classicMovBox("moov").asBytes())
        assertTrue(mov.boxes.isNotEmpty())
        assertEquals("moov", mov.boxes[0].type.value)
    }

    @Test
    fun roundTripPreservesSize() {
        val bytes = ftypFile("qt  ").asBytes()
        val mov = reader.read(bytes)
        val written = mov.toBytes()
        assertEquals(bytes.size, written.size)
    }
}
