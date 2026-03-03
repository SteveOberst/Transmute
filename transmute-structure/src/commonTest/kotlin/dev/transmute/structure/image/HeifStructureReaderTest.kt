package dev.transmute.structure.image

import dev.transmute.model.core.asBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeifStructureReaderTest {

    private val reader = HeifStructureReader()

    /** Build a minimal ISO BMFF file: ftyp box with given major brand. */
    private fun ftypFile(brand: String): ByteArray {
        require(brand.length == 4)
        // ftyp box: size(4) + "ftyp"(4) + majorBrand(4) + minorVersion(4) = 16 bytes
        val size = 16
        val out = ByteArray(size)
        out[0] = 0; out[1] = 0; out[2] = 0; out[3] = size.toByte()
        "ftyp".encodeToByteArray().copyInto(out, 4)
        brand.encodeToByteArray().copyInto(out, 8)
        // minor version = 0 (already zero)
        return out
    }

    @Test
    fun readParsesBoxes() {
        val heif = reader.read(ftypFile("heic").asBytes())
        assertTrue(heif.boxes.isNotEmpty())
        assertEquals("ftyp", heif.boxes[0].type.value)
    }

    @Test
    fun roundTripPreservesSize() {
        val bytes = ftypFile("heic").asBytes()
        val heif = reader.read(bytes)
        val written = heif.toBytes()
        assertEquals(bytes.size, written.size)
    }
}
