package dev.transmute.structure.image

import dev.transmute.model.core.asBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AvifStructureReaderTest {

    private val reader = AvifStructureReader()

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

    @Test
    fun canReadAcceptsAvif() {
        assertTrue(reader.canRead(ftypFile("avif").asBytes()))
    }

    @Test
    fun canReadAcceptsAvis() {
        assertTrue(reader.canRead(ftypFile("avis").asBytes()))
    }

    @Test
    fun canReadRejectsHeic() {
        assertFalse(reader.canRead(ftypFile("heic").asBytes()))
    }

    @Test
    fun canReadRejectsGarbage() {
        assertFalse(reader.canRead(ByteArray(16).asBytes()))
    }

    @Test
    fun readParsesBoxes() {
        val avif = reader.read(ftypFile("avif").asBytes())
        assertTrue(avif.boxes.isNotEmpty())
        assertEquals("ftyp", avif.boxes[0].type.value)
    }

    @Test
    fun roundTripPreservesSize() {
        val bytes = ftypFile("avif").asBytes()
        val avif = reader.read(bytes)
        val written = avif.toBytes()
        assertEquals(bytes.size, written.size)
    }
}
