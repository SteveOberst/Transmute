package dev.transmute.image.codecs.jvm

import dev.transmute.model.core.asBytes
import dev.transmute.image.ImageFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Direct sniff() tests for [JvmImageIoDecoder]. */
class JvmImageIoDecoderSniffTest {

  private val decoder = JvmImageIoDecoder()

  // --- JPEG ---

  @Test
  fun sniffJpegJfif() {
    val data = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
    assertEquals(ImageFormat.Jpeg, decoder.sniff(data.asBytes()))
  }

  @Test
  fun sniffJpegExif() {
    val data = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte())
    assertEquals(ImageFormat.Jpeg, decoder.sniff(data.asBytes()))
  }

  // --- PNG ---

  @Test
  fun sniffPng() {
    val data = byteArrayOf(
      0x89.toByte(), 0x50, 0x4E, 0x47,
      0x0D, 0x0A, 0x1A, 0x0A,
    )
    assertEquals(ImageFormat.Png, decoder.sniff(data.asBytes()))
  }

  // --- GIF ---

  @Test
  fun sniffGif89a() {
    val data = "GIF89a".encodeToByteArray() + ByteArray(10)
    assertEquals(ImageFormat.Gif, decoder.sniff(data.asBytes()))
  }

  @Test
  fun sniffGif87a() {
    val data = "GIF87a".encodeToByteArray() + ByteArray(10)
    assertEquals(ImageFormat.Gif, decoder.sniff(data.asBytes()))
  }

  // --- BMP ---

  @Test
  fun sniffBmp() {
    val data = byteArrayOf(0x42, 0x4D, 0x00, 0x00, 0x00, 0x00)
    assertEquals(ImageFormat.Bmp, decoder.sniff(data.asBytes()))
  }

  // --- TIFF ---

  @Test
  fun sniffTiffLittleEndian() {
    val data = byteArrayOf(0x49, 0x49, 0x2A, 0x00, 0x08, 0x00)
    assertEquals(ImageFormat.Tiff, decoder.sniff(data.asBytes()))
  }

  @Test
  fun sniffTiffBigEndian() {
    val data = byteArrayOf(0x4D, 0x4D, 0x00, 0x2A, 0x00, 0x08)
    assertEquals(ImageFormat.Tiff, decoder.sniff(data.asBytes()))
  }

  // --- WebP ---

  @Test
  fun sniffWebp() {
    val data = ByteArray(16)
    data[0] = 0x52; data[1] = 0x49; data[2] = 0x46; data[3] = 0x46 // RIFF
    data[8] = 0x57; data[9] = 0x45; data[10] = 0x42; data[11] = 0x50 // WEBP
    assertEquals(ImageFormat.Webp, decoder.sniff(data.asBytes()))
  }

  // --- Edge cases ---

  @Test
  fun sniffRejectsTooShort() {
    assertNull(decoder.sniff(byteArrayOf(0xFF.toByte()).asBytes()))
    assertNull(decoder.sniff(ByteArray(0).asBytes()))
    assertNull(decoder.sniff(byteArrayOf(0, 0, 0).asBytes()))
  }

  @Test
  fun sniffRejectsRandomData() {
    val garbage = ByteArray(64) { (it * 7 + 3).toByte() }
    assertNull(decoder.sniff(garbage.asBytes()))
  }

  @Test
  fun sniffRejectsHeicNotSupported() {
    // JvmImageIoDecoder does not support HEIC - should return null
    val data = ByteArray(16)
    data[4] = 0x66; data[5] = 0x74; data[6] = 0x79; data[7] = 0x70 // ftyp
    data[8] = 0x68; data[9] = 0x65; data[10] = 0x69; data[11] = 0x63 // heic
    assertNull(decoder.sniff(data.asBytes()))
  }
}
