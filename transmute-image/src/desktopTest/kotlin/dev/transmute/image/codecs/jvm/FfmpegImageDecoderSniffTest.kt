package dev.transmute.image.codecs.jvm

import dev.transmute.core.asBytes
import dev.transmute.image.ImageFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Direct sniff() tests for [FfmpegImageDecoder]. */
class FfmpegImageDecoderSniffTest {

  private val decoder = FfmpegImageDecoder()

  // --- HEIC ---

  @Test
  fun sniffHeic() {
    val data = ByteArray(16)
    data[4] = 0x66; data[5] = 0x74; data[6] = 0x79; data[7] = 0x70 // ftyp
    data[8] = 0x68; data[9] = 0x65; data[10] = 0x69; data[11] = 0x63 // heic
    assertEquals(ImageFormat.Heic, decoder.sniff(data.asBytes()))
  }

  @Test
  fun sniffHeicHeix() {
    val data = ByteArray(16)
    data[4] = 0x66; data[5] = 0x74; data[6] = 0x79; data[7] = 0x70
    data[8] = 0x68; data[9] = 0x65; data[10] = 0x69; data[11] = 0x78 // heix
    assertEquals(ImageFormat.Heic, decoder.sniff(data.asBytes()))
  }

  @Test
  fun sniffHeicHevc() {
    val data = ByteArray(16)
    data[4] = 0x66; data[5] = 0x74; data[6] = 0x79; data[7] = 0x70
    data[8] = 0x68; data[9] = 0x65; data[10] = 0x76; data[11] = 0x63 // hevc
    assertEquals(ImageFormat.Heic, decoder.sniff(data.asBytes()))
  }

  // --- HEIF ---

  @Test
  fun sniffHeifMif1() {
    val data = ByteArray(16)
    data[4] = 0x66; data[5] = 0x74; data[6] = 0x79; data[7] = 0x70
    data[8] = 0x6D; data[9] = 0x69; data[10] = 0x66; data[11] = 0x31 // mif1
    assertEquals(ImageFormat.Heif, decoder.sniff(data.asBytes()))
  }

  @Test
  fun sniffHeifMsf1() {
    val data = ByteArray(16)
    data[4] = 0x66; data[5] = 0x74; data[6] = 0x79; data[7] = 0x70
    data[8] = 0x6D; data[9] = 0x73; data[10] = 0x66; data[11] = 0x31 // msf1
    assertEquals(ImageFormat.Heif, decoder.sniff(data.asBytes()))
  }

  // --- AVIF ---

  @Test
  fun sniffAvif() {
    val data = ByteArray(16)
    data[4] = 0x66; data[5] = 0x74; data[6] = 0x79; data[7] = 0x70
    data[8] = 0x61; data[9] = 0x76; data[10] = 0x69; data[11] = 0x66 // avif
    assertEquals(ImageFormat.Avif, decoder.sniff(data.asBytes()))
  }

  @Test
  fun sniffAvifSequence() {
    val data = ByteArray(16)
    data[4] = 0x66; data[5] = 0x74; data[6] = 0x79; data[7] = 0x70
    data[8] = 0x61; data[9] = 0x76; data[10] = 0x69; data[11] = 0x73 // avis
    assertEquals(ImageFormat.Avif, decoder.sniff(data.asBytes()))
  }

  // --- Edge cases ---

  @Test
  fun sniffRejectsTooShort() {
    assertNull(decoder.sniff(ByteArray(8).asBytes()))
    assertNull(decoder.sniff(ByteArray(0).asBytes()))
  }

  @Test
  fun sniffRejectsJpeg() {
    val data = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
    assertNull(decoder.sniff(data.asBytes()))
  }

  @Test
  fun sniffRejectsMp4Brand() {
    // MP4 ftyp with "isom" - not an image format
    val data = ByteArray(16)
    data[4] = 0x66; data[5] = 0x74; data[6] = 0x79; data[7] = 0x70
    data[8] = 0x69; data[9] = 0x73; data[10] = 0x6F; data[11] = 0x6D // isom
    assertNull(decoder.sniff(data.asBytes()))
  }

  @Test
  fun sniffRejectsRandomData() {
    val garbage = ByteArray(64) { (it * 7 + 3).toByte() }
    assertNull(decoder.sniff(garbage.asBytes()))
  }
}
