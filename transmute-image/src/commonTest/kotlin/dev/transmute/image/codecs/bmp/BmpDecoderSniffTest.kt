package dev.transmute.image.codecs.bmp

import dev.transmute.core.ImageFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BmpDecoderSniffTest {

  private val decoder = BmpImageDecoder()

  @Test
  fun sniffBmpHeader() {
    val data = byteArrayOf(0x42, 0x4D, 0x00, 0x00, 0x00, 0x00) // "BM"
    assertEquals(ImageFormat.BMP, decoder.sniff(data))
  }

  @Test
  fun sniffBmpMinimal() {
    val data = byteArrayOf(0x42, 0x4D) // just "BM"
    assertEquals(ImageFormat.BMP, decoder.sniff(data))
  }

  @Test
  fun sniffRejectsJpeg() {
    val data = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
    assertNull(decoder.sniff(data))
  }

  @Test
  fun sniffRejectsPng() {
    val data = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    assertNull(decoder.sniff(data))
  }

  @Test
  fun sniffRejectsTooShort() {
    assertNull(decoder.sniff(byteArrayOf(0x42)))
    assertNull(decoder.sniff(ByteArray(0)))
  }

  @Test
  fun sniffRejectsRandomData() {
    val garbage = ByteArray(64) { (it * 7 + 3).toByte() }
    assertNull(decoder.sniff(garbage))
  }
}
