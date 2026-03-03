package dev.transmute.structure.video

import dev.transmute.model.core.asBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Mp4StructureReaderTest {

    private val reader = Mp4StructureReader()

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
    fun readParsesBoxes() {
        val mp4 = reader.read(ftypFile("isom").asBytes())
        assertTrue(mp4.boxes.isNotEmpty())
        assertEquals("ftyp", mp4.boxes[0].type.value)
    }

    @Test
    fun roundTripPreservesSize() {
        val bytes = ftypFile("isom").asBytes()
        val mp4 = reader.read(bytes)
        val written = mp4.toBytes()
        assertEquals(bytes.size, written.size)
    }
}
