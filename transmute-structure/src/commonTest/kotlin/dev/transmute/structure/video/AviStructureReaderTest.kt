package dev.transmute.structure.video

import dev.transmute.model.core.asBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AviStructureReaderTest {

  private val reader = AviStructureReader()

  /**
   * Minimal AVI: RIFF header + "AVI " + one hdrl LIST chunk (empty body).
   */
  private fun minimalAvi(): ByteArray {
    // hdrl LIST chunk
    val listBody = ByteArray(0)
    val listChunkSize = 4 + listBody.size // "hdrl" + body
    val listChunk = ByteArray(12 + listBody.size)
    "LIST".encodeToByteArray().copyInto(listChunk, 0)
    writeU32LE(listChunk, 4, listChunkSize)
    "hdrl".encodeToByteArray().copyInto(listChunk, 8)

    // fileSize = 4 ("AVI ") + listChunk.size
    val fileSize = 4 + listChunk.size
    val out = ByteArray(12 + listChunk.size)
    var p = 0
    "RIFF".encodeToByteArray().copyInto(out, p)
    p += 4
    writeU32LE(out, p, fileSize)
    p += 4
    "AVI ".encodeToByteArray().copyInto(out, p)
    p += 4
    listChunk.copyInto(out, p)
    return out
  }

  private fun writeU32LE(buf: ByteArray, offset: Int, value: Int) {
    buf[offset] = (value and 0xFF).toByte()
    buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
    buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
    buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
  }

  @Test
  fun readParsesChildren() {
    val avi = reader.read(minimalAvi().asBytes())
    assertTrue(avi.riff.children.isNotEmpty())
  }

  @Test
  fun readHasAviFormType() {
    val avi = reader.read(minimalAvi().asBytes())
    assertEquals("AVI ", avi.riff.formType?.value)
  }

  @Test
  fun roundTripPreservesSize() {
    val bytes = minimalAvi().asBytes()
    val avi = reader.read(bytes)
    val written = avi.toBytes()
    assertEquals(bytes.size, written.size)
  }
}
