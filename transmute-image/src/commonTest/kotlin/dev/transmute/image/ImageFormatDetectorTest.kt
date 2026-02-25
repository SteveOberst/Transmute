package dev.transmute.image

import dev.transmute.model.core.asBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [ImageFormatDetector] - validates magic-byte detection for
 * every image format commonly encountered on iOS and Android.
 */
class ImageFormatDetectorTest {

  // --- JPEG ---

  @Test
  fun detectJpeg() {
    // Standard JPEG: FF D8 FF E0 (JFIF) or FF D8 FF E1 (Exif)
    val jfif = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10)
    assertEquals(ImageFormat.Jpeg, ImageFormatDetector.detect(jfif.asBytes()))

    val exif = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(), 0x00, 0x00)
    assertEquals(ImageFormat.Jpeg, ImageFormatDetector.detect(exif.asBytes()))
  }

  @Test
  fun detectJpegMinimalBytes() {
    val data = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xDB.toByte())
    assertEquals(ImageFormat.Jpeg, ImageFormatDetector.detect(data.asBytes()))
  }

  // --- PNG ---

  @Test
  fun detectPng() {
    val magic = byteArrayOf(
      0x89.toByte(), 0x50, 0x4E, 0x47,
      0x0D, 0x0A, 0x1A, 0x0A,
      0x00, 0x00, 0x00, 0x0D, // IHDR chunk follows
    )
    assertEquals(ImageFormat.Png, ImageFormatDetector.detect(magic.asBytes()))
  }

  // --- WebP ---

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
    assertEquals(ImageFormat.Webp, ImageFormatDetector.detect(data.asBytes()))
  }

  // --- HEIC (iOS default) ---

  @Test
  fun detectHeic() {
    // ISO BMFF: [size][ftyp][heic]
    val data = ByteArray(16)
    data[0] = 0x00; data[1] = 0x00; data[2] = 0x00; data[3] = 0x18 // box size
    data[4] = 0x66; data[5] = 0x74; data[6] = 0x79; data[7] = 0x70 // "ftyp"
    data[8] = 0x68; data[9] = 0x65; data[10] = 0x69; data[11] = 0x63 // "heic"
    assertEquals(ImageFormat.Heic, ImageFormatDetector.detect(data.asBytes()))
  }

  @Test
  fun detectHeicHeix() {
    val data = ByteArray(16)
    data[4] = 0x66; data[5] = 0x74; data[6] = 0x79; data[7] = 0x70
    data[8] = 0x68; data[9] = 0x65; data[10] = 0x69; data[11] = 0x78 // "heix"
    assertEquals(ImageFormat.Heic, ImageFormatDetector.detect(data.asBytes()))
  }

  @Test
  fun detectHeicHevc() {
    val data = ByteArray(16)
    data[4] = 0x66; data[5] = 0x74; data[6] = 0x79; data[7] = 0x70
    data[8] = 0x68; data[9] = 0x65; data[10] = 0x76; data[11] = 0x63 // "hevc"
    assertEquals(ImageFormat.Heic, ImageFormatDetector.detect(data.asBytes()))
  }

  // --- HEIF ---

  @Test
  fun detectHeifMif1() {
    val data = ByteArray(16)
    data[4] = 0x66; data[5] = 0x74; data[6] = 0x79; data[7] = 0x70
    data[8] = 0x6D; data[9] = 0x69; data[10] = 0x66; data[11] = 0x31 // "mif1"
    assertEquals(ImageFormat.Heif, ImageFormatDetector.detect(data.asBytes()))
  }

  // --- AVIF ---

  @Test
  fun detectAvif() {
    val data = ByteArray(16)
    data[4] = 0x66; data[5] = 0x74; data[6] = 0x79; data[7] = 0x70
    data[8] = 0x61; data[9] = 0x76; data[10] = 0x69; data[11] = 0x66 // "avif"
    assertEquals(ImageFormat.Avif, ImageFormatDetector.detect(data.asBytes()))
  }

  @Test
  fun detectAvifSequence() {
    val data = ByteArray(16)
    data[4] = 0x66; data[5] = 0x74; data[6] = 0x79; data[7] = 0x70
    data[8] = 0x61; data[9] = 0x76; data[10] = 0x69; data[11] = 0x73 // "avis"
    assertEquals(ImageFormat.Avif, ImageFormatDetector.detect(data.asBytes()))
  }

  // --- GIF ---

  @Test
  fun detectGif89a() {
    val data = "GIF89a".encodeToByteArray() + ByteArray(10)
    assertEquals(ImageFormat.Gif, ImageFormatDetector.detect(data.asBytes()))
  }

  @Test
  fun detectGif87a() {
    val data = "GIF87a".encodeToByteArray() + ByteArray(10)
    assertEquals(ImageFormat.Gif, ImageFormatDetector.detect(data.asBytes()))
  }

  // --- BMP ---

  @Test
  fun detectBmp() {
    val data = byteArrayOf(0x42, 0x4D, 0x00, 0x00, 0x00, 0x00)
    assertEquals(ImageFormat.Bmp, ImageFormatDetector.detect(data.asBytes()))
  }

  // --- TIFF ---

  @Test
  fun detectTiffLittleEndian() {
    // "II" + 42 as LE
    val data = byteArrayOf(0x49, 0x49, 0x2A, 0x00, 0x08, 0x00)
    assertEquals(ImageFormat.Tiff, ImageFormatDetector.detect(data.asBytes()))
  }

  @Test
  fun detectTiffBigEndian() {
    // "MM" + 42 as BE
    val data = byteArrayOf(0x4D, 0x4D, 0x00, 0x2A, 0x00, 0x08)
    assertEquals(ImageFormat.Tiff, ImageFormatDetector.detect(data.asBytes()))
  }

  // --- Edge cases ---

  @Test
  fun unknownForTooFewBytes() {
    assertEquals(ImageFormat.Unknown, ImageFormatDetector.detect(byteArrayOf(0xFF.toByte()).asBytes()))
    assertEquals(ImageFormat.Unknown, ImageFormatDetector.detect(ByteArray(0).asBytes()))
    assertEquals(ImageFormat.Unknown, ImageFormatDetector.detect(byteArrayOf(0, 0, 0).asBytes()))
  }

  @Test
  fun unknownForRandomData() {
    val garbage = ByteArray(64) { (it * 7 + 3).toByte() }
    assertEquals(ImageFormat.Unknown, ImageFormatDetector.detect(garbage.asBytes()))
  }

  @Test
  fun unknownForTextData() {
    val text = "Hello, World! This is not an image.".encodeToByteArray()
    assertEquals(ImageFormat.Unknown, ImageFormatDetector.detect(text.asBytes()))
  }

  // --- Alpha support ---

  @Test
  fun alphaSupport() {
    assertTrue(ImageFormatDetector.supportsAlpha(ImageFormat.Png))
    assertTrue(ImageFormatDetector.supportsAlpha(ImageFormat.Webp))
    assertTrue(ImageFormatDetector.supportsAlpha(ImageFormat.Gif))
    assertTrue(ImageFormatDetector.supportsAlpha(ImageFormat.Avif))

    assertFalse(ImageFormatDetector.supportsAlpha(ImageFormat.Jpeg))
    assertFalse(ImageFormatDetector.supportsAlpha(ImageFormat.Heic))
    assertFalse(ImageFormatDetector.supportsAlpha(ImageFormat.Heif))
    assertFalse(ImageFormatDetector.supportsAlpha(ImageFormat.Bmp))
    assertFalse(ImageFormatDetector.supportsAlpha(ImageFormat.Tiff))
  }

  // --- Lossy detection ---

  @Test
  fun lossyDetection() {
    assertTrue(ImageFormatDetector.isLossy(ImageFormat.Jpeg))
    assertTrue(ImageFormatDetector.isLossy(ImageFormat.Heic))
    assertTrue(ImageFormatDetector.isLossy(ImageFormat.Heif))
    assertTrue(ImageFormatDetector.isLossy(ImageFormat.Webp))
    assertTrue(ImageFormatDetector.isLossy(ImageFormat.Avif))

    assertFalse(ImageFormatDetector.isLossy(ImageFormat.Png))
    assertFalse(ImageFormatDetector.isLossy(ImageFormat.Gif))
    assertFalse(ImageFormatDetector.isLossy(ImageFormat.Bmp))
    assertFalse(ImageFormatDetector.isLossy(ImageFormat.Tiff))
  }

  // --- MIME types ---

  @Test
  fun mimeTypesCorrect() {
    assertEquals("image/jpeg", ImageFormat.Jpeg.mimeType)
    assertEquals("image/png", ImageFormat.Png.mimeType)
    assertEquals("image/webp", ImageFormat.Webp.mimeType)
    assertEquals("image/heic", ImageFormat.Heic.mimeType)
    assertEquals("image/heif", ImageFormat.Heif.mimeType)
    assertEquals("image/avif", ImageFormat.Avif.mimeType)
    assertEquals("image/gif", ImageFormat.Gif.mimeType)
    assertEquals("image/bmp", ImageFormat.Bmp.mimeType)
    assertEquals("image/tiff", ImageFormat.Tiff.mimeType)
  }

  // --- Extensions ---

  @Test
  fun extensionsCorrect() {
    assertEquals("jpg", ImageFormat.Jpeg.extension)
    assertEquals("png", ImageFormat.Png.extension)
    assertEquals("webp", ImageFormat.Webp.extension)
    assertEquals("heic", ImageFormat.Heic.extension)
    assertEquals("heif", ImageFormat.Heif.extension)
    assertEquals("avif", ImageFormat.Avif.extension)
  }
}
