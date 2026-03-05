package dev.transmute.structure.audio

import dev.transmute.model.core.asBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class M4aStructureReaderTest {

  private val reader = M4aStructureReader()

  /** Build a minimal ISO BMFF file: ftyp box with given major brand. */
  private fun ftypFile(brand: String): ByteArray {
    require(brand.length == 4)
    val size = 16
    val out = ByteArray(size)
    out[0] = 0
    out[1] = 0
    out[2] = 0
    out[3] = size.toByte()
    "ftyp".encodeToByteArray().copyInto(out, 4)
    brand.encodeToByteArray().copyInto(out, 8)
    return out
  }

  @Test
  fun readParsesBoxes() {
    val m4a = reader.read(ftypFile("M4A ").asBytes())
    assertTrue(m4a.boxes.isNotEmpty())
    assertEquals("ftyp", m4a.boxes[0].type.value)
  }

  @Test
  fun roundTripPreservesSize() {
    val bytes = ftypFile("M4A ").asBytes()
    val m4a = reader.read(bytes)
    val written = m4a.toBytes()
    assertEquals(bytes.size, written.size)
  }
}
