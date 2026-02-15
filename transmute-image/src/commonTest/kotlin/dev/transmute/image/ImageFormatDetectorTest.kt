package dev.transmute.image

import dev.transmute.core.ImageFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [ImageFormatDetector] — validates magic-byte detection for
 * every image format commonly encountered on iOS and Android.
 */
class ImageFormatDetectorTest {

  // ── JPEG ──

  @Test
  fun detectJpeg() {
    // Standard JPEG: FF D8 FF E0 (JFIF) or FF D8 FF E1 (Exif)
    val jfif = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10)
    assertEquals(ImageFormat.JPEG, ImageFormatDetector.detect(jfif))

    val exif = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(), 0x00, 0x00)
    assertEquals(ImageFormat.JPEG, ImageFormatDetector.detect(exif))
  }

  @Test
  fun detectJpegMinimalBytes() {
    val data = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xDB.toByte())
    assertEquals(ImageFormat.JPEG, ImageFormatDetector.detect(data))
  }

  // ── PNG ──

  @Test
  fun detectPng() {
    val magic = byteArrayOf(
      0x89.toByte(), 0x50, 0x4E, 0x47,
      0x0D, 0x0A, 0x1A, 0x0A,
      0x00, 0x00, 0x00, 0x0D, // IHDR chunk follows
    )
    assertEquals(ImageFormat.PNG, ImageFormatDetector.detect(magic))
  }

  // ── WebP ──

  @Test
  fun detectWebp() {
    // RIFF....WEBP
    val data = ByteArray(16)
    data[0] = 0x52 // R
    data[1] = 0x49 // I
    data[2] = 0x46 // F
    data[3] = 0x46 // F
    // bytes 4-7: file size (don't care for detection)
    data[8] = 0x57  // W
    data[9] = 0x45  // E
    data[10] = 0x42 // B
    data[11] = 0x50 // P
    assertEquals(ImageFormat.WEBP, ImageFormatDetector.detect(data))
  }

  // ── HEIC (iOS default) ──

  @Test
  fun detectHeic() {
    // ISO BMFF: [size][ftyp][heic]
    val data = ByteArray(16)
    data[0] = 0x00; data[1] = 0x00; data[2] = 0x00; data[3] = 0x18 // box size
    data[4] = 0x66; data[5] = 0x74; data[6] = 0x79; data[7] = 0x70 // "ftyp"
    data[8] = 0x68; data[9] = 0x65; data[10] = 0x69; data[11] = 0x63 // "heic"
    assertEquals(ImageFormat.HEIC, ImageFormatDetector.detect(data))
  }

  @Test
  fun detectHeicHeix() {
    val data = ByteArray(16)
    data[4] = 0x66; data[5] = 0x74; data[6] = 0x79; data[7] = 0x70
    data[8] = 0x68; data[9] = 0x65; data[10] = 0x69; data[11] = 0x78 // "heix"
    assertEquals(ImageFormat.HEIC, ImageFormatDetector.detect(data))
  }

  @Test
  fun detectHeicHevc() {
    val data = ByteArray(16)
    data[4] = 0x66; data[5] = 0x74; data[6] = 0x79; data[7] = 0x70
    data[8] = 0x68; data[9] = 0x65; data[10] = 0x76; data[11] = 0x63 // "hevc"
    assertEquals(ImageFormat.HEIC, ImageFormatDetector.detect(data))
  }

  // ── HEIF ──

  @Test
  fun detectHeifMif1() {
    val data = ByteArray(16)
    data[4] = 0x66; data[5] = 0x74; data[6] = 0x79; data[7] = 0x70
    data[8] = 0x6D; data[9] = 0x69; data[10] = 0x66; data[11] = 0x31 // "mif1"
    assertEquals(ImageFormat.HEIF, ImageFormatDetector.detect(data))
  }

  // ── AVIF ──

  @Test
  fun detectAvif() {
    val data = ByteArray(16)
    data[4] = 0x66; data[5] = 0x74; data[6] = 0x79; data[7] = 0x70
    data[8] = 0x61; data[9] = 0x76; data[10] = 0x69; data[11] = 0x66 // "avif"
    assertEquals(ImageFormat.AVIF, ImageFormatDetector.detect(data))
  }

  @Test
  fun detectAvifSequence() {
    val data = ByteArray(16)
    data[4] = 0x66; data[5] = 0x74; data[6] = 0x79; data[7] = 0x70
    data[8] = 0x61; data[9] = 0x76; data[10] = 0x69; data[11] = 0x73 // "avis"
    assertEquals(ImageFormat.AVIF, ImageFormatDetector.detect(data))
  }

  // ── GIF ──

  @Test
  fun detectGif89a() {
    val data = "GIF89a".encodeToByteArray() + ByteArray(10)
    assertEquals(ImageFormat.GIF, ImageFormatDetector.detect(data))
  }

  @Test
  fun detectGif87a() {
    val data = "GIF87a".encodeToByteArray() + ByteArray(10)
    assertEquals(ImageFormat.GIF, ImageFormatDetector.detect(data))
  }

  // ── BMP ──

  @Test
  fun detectBmp() {
    val data = byteArrayOf(0x42, 0x4D, 0x00, 0x00, 0x00, 0x00)
    assertEquals(ImageFormat.BMP, ImageFormatDetector.detect(data))
  }

  // ── TIFF ──

  @Test
  fun detectTiffLittleEndian() {
    // "II" + 42 as LE
    val data = byteArrayOf(0x49, 0x49, 0x2A, 0x00, 0x08, 0x00)
    assertEquals(ImageFormat.TIFF, ImageFormatDetector.detect(data))
  }

  @Test
  fun detectTiffBigEndian() {
    // "MM" + 42 as BE
    val data = byteArrayOf(0x4D, 0x4D, 0x00, 0x2A, 0x00, 0x08)
    assertEquals(ImageFormat.TIFF, ImageFormatDetector.detect(data))
  }

  // ── Edge cases ──

  @Test
  fun unknownForTooFewBytes() {
    assertEquals(ImageFormat.UNKNOWN, ImageFormatDetector.detect(byteArrayOf(0xFF.toByte())))
    assertEquals(ImageFormat.UNKNOWN, ImageFormatDetector.detect(ByteArray(0)))
    assertEquals(ImageFormat.UNKNOWN, ImageFormatDetector.detect(byteArrayOf(0, 0, 0)))
  }

  @Test
  fun unknownForRandomData() {
    val garbage = ByteArray(64) { (it * 7 + 3).toByte() }
    assertEquals(ImageFormat.UNKNOWN, ImageFormatDetector.detect(garbage))
  }

  @Test
  fun unknownForTextData() {
    val text = "Hello, World! This is not an image.".encodeToByteArray()
    assertEquals(ImageFormat.UNKNOWN, ImageFormatDetector.detect(text))
  }

  // ── Alpha support ──

  @Test
  fun alphaSupport() {
    assertTrue(ImageFormatDetector.supportsAlpha(ImageFormat.PNG))
    assertTrue(ImageFormatDetector.supportsAlpha(ImageFormat.WEBP))
    assertTrue(ImageFormatDetector.supportsAlpha(ImageFormat.GIF))
    assertTrue(ImageFormatDetector.supportsAlpha(ImageFormat.AVIF))

    assertFalse(ImageFormatDetector.supportsAlpha(ImageFormat.JPEG))
    assertFalse(ImageFormatDetector.supportsAlpha(ImageFormat.HEIC))
    assertFalse(ImageFormatDetector.supportsAlpha(ImageFormat.HEIF))
    assertFalse(ImageFormatDetector.supportsAlpha(ImageFormat.BMP))
    assertFalse(ImageFormatDetector.supportsAlpha(ImageFormat.TIFF))
  }

  // ── Lossy detection ──

  @Test
  fun lossyDetection() {
    assertTrue(ImageFormatDetector.isLossy(ImageFormat.JPEG))
    assertTrue(ImageFormatDetector.isLossy(ImageFormat.HEIC))
    assertTrue(ImageFormatDetector.isLossy(ImageFormat.HEIF))
    assertTrue(ImageFormatDetector.isLossy(ImageFormat.WEBP))
    assertTrue(ImageFormatDetector.isLossy(ImageFormat.AVIF))

    assertFalse(ImageFormatDetector.isLossy(ImageFormat.PNG))
    assertFalse(ImageFormatDetector.isLossy(ImageFormat.GIF))
    assertFalse(ImageFormatDetector.isLossy(ImageFormat.BMP))
    assertFalse(ImageFormatDetector.isLossy(ImageFormat.TIFF))
  }

  // ── MIME types ──

  @Test
  fun mimeTypesCorrect() {
    assertEquals("image/jpeg", ImageFormat.JPEG.mimeType)
    assertEquals("image/png", ImageFormat.PNG.mimeType)
    assertEquals("image/webp", ImageFormat.WEBP.mimeType)
    assertEquals("image/heic", ImageFormat.HEIC.mimeType)
    assertEquals("image/heif", ImageFormat.HEIF.mimeType)
    assertEquals("image/avif", ImageFormat.AVIF.mimeType)
    assertEquals("image/gif", ImageFormat.GIF.mimeType)
    assertEquals("image/bmp", ImageFormat.BMP.mimeType)
    assertEquals("image/tiff", ImageFormat.TIFF.mimeType)
  }

  // ── Extensions ──

  @Test
  fun extensionsCorrect() {
    assertEquals("jpg", ImageFormat.JPEG.extension)
    assertEquals("png", ImageFormat.PNG.extension)
    assertEquals("webp", ImageFormat.WEBP.extension)
    assertEquals("heic", ImageFormat.HEIC.extension)
    assertEquals("heif", ImageFormat.HEIF.extension)
    assertEquals("avif", ImageFormat.AVIF.extension)
  }
}
