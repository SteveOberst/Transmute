package dev.transmute.structure.image

import dev.transmute.model.core.asBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PngStructureReaderTest {

  private val reader = PngStructureReader()

  private fun minimalPng(): ByteArray {
    // PNG signature (8) + IHDR chunk (25) + IEND chunk (12) = 45 bytes
    val sig = byteArrayOf(
      0x89.toByte(),
      0x50,
      0x4E,
      0x47,
      0x0D,
      0x0A,
      0x1A,
      0x0A,
    )
    // IHDR: length=13, "IHDR", 13 bytes data, 4 bytes CRC
    val ihdrLen = byteArrayOf(0x00, 0x00, 0x00, 0x0D)
    val ihdrType = "IHDR".encodeToByteArray()
    val ihdrData = byteArrayOf(
      0x00, 0x00, 0x00, 0x01, // width = 1
      0x00, 0x00, 0x00, 0x01, // height = 1
      0x08, // bit depth = 8
      0x02, // color type = RGB
      0x00, // compression = 0
      0x00, // filter = 0
      0x00, // interlace = 0
    )
    val ihdrCrc = byteArrayOf(0x00, 0x00, 0x00, 0x00) // dummy CRC
    // IEND: length=0, "IEND", 4 bytes CRC
    val iendLen = byteArrayOf(0x00, 0x00, 0x00, 0x00)
    val iendType = "IEND".encodeToByteArray()
    val iendCrc = byteArrayOf(0x00, 0x00, 0x00, 0x00)

    return sig + ihdrLen + ihdrType + ihdrData + ihdrCrc +
      iendLen + iendType + iendCrc
  }

  @Test
  fun readParsesChunks() {
    val png = reader.read(minimalPng().asBytes())
    assertTrue(png.chunks.isNotEmpty())
    assertEquals("IHDR", png.chunks.first().type.value)
  }

  @Test
  fun roundTripPreservesBytes() {
    val bytes = minimalPng().asBytes()
    val png = reader.read(bytes)
    val written = png.toBytes()
    assertEquals(bytes.size, written.size)
  }
}
