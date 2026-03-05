package dev.transmute.structure.image

import dev.transmute.model.core.asBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebpStructureReaderTest {

  private val reader = WebpStructureReader()

  /**
   * Minimal WebP: RIFF header + "WEBP" + VP8 chunk (10 bytes body).
   *
   * ```
   * RIFF | fileSize LE | WEBP | VP8  | chunkSize LE | ...body...
   * ```
   */
  private fun minimalWebp(): ByteArray {
    val chunkBody = ByteArray(10) // 10 bytes of VP8 data (zeroes, won't decode but structurally valid)
    val chunkSize = chunkBody.size
    // fileSize = 4 ("WEBP") + 8 (chunk header) + chunkSize
    val fileSize = 4 + 8 + chunkSize
    val out = ByteArray(12 + 8 + chunkSize)
    var p = 0
    // RIFF header
    "RIFF".encodeToByteArray().copyInto(out, p)
    p += 4
    out[p] = (fileSize and 0xFF).toByte()
    out[p + 1] = ((fileSize shr 8) and 0xFF).toByte()
    out[p + 2] = ((fileSize shr 16) and 0xFF).toByte()
    out[p + 3] = ((fileSize shr 24) and 0xFF).toByte()
    p += 4
    "WEBP".encodeToByteArray().copyInto(out, p)
    p += 4
    // VP8 chunk
    "VP8 ".encodeToByteArray().copyInto(out, p)
    p += 4
    out[p] = (chunkSize and 0xFF).toByte()
    out[p + 1] = ((chunkSize shr 8) and 0xFF).toByte()
    out[p + 2] = ((chunkSize shr 16) and 0xFF).toByte()
    out[p + 3] = ((chunkSize shr 24) and 0xFF).toByte()
    p += 4
    chunkBody.copyInto(out, p)
    return out
  }

  @Test
  fun readParsesRiffChildren() {
    val webp = reader.read(minimalWebp().asBytes())
    // The top-level RIFF should have at least one child (VP8 chunk)
    assertTrue(webp.riff.children.isNotEmpty())
    assertEquals("VP8 ", webp.riff.children[0].id.value)
  }

  @Test
  fun roundTripPreservesSize() {
    val bytes = minimalWebp().asBytes()
    val webp = reader.read(bytes)
    val written = webp.toBytes()
    assertEquals(bytes.size, written.size)
  }
}
