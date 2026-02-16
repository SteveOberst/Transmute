package dev.transmute.image.codecs.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import dev.transmute.core.ImageFormat
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.ImageTestHelpers
import dev.transmute.image.codecs.bmp.BmpImageEncoder
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Android instrumented tests for [AndroidBitmapImageDecoder] and
 * [AndroidBitmapImageEncoder].
 *
 * These tests run on a real device or emulator and exercise the actual
 * Android BitmapFactory / Bitmap.compress APIs.
 *
 * Run: ./gradlew :transmute-image:connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class AndroidImageCodecTest {

  private val decoder = AndroidBitmapImageDecoder()
  private val encoder = AndroidBitmapImageEncoder()

  // -----------------------------------------------------------------------
  // JPEG roundtrip
  // -----------------------------------------------------------------------

  @Test
  fun jpegRoundTripPreservesDimensions() = runTest {
    val original = ImageTestHelpers.solidColor(64, 48, r = 200, g = 100, b = 50)
    val ctx = ImageTestHelpers.testContext()
    ctx.scratchpad["image.output.format"] = ImageFormat.JPEG
    ctx.scratchpad["image.quality"] = 0.9f

    val encoded = encoder.encode(original, ctx)
    assertTrue(encoded.isNotEmpty())

    val decoded = decoder.decode(encoded, ctx)
    assertEquals(64, decoded.width)
    assertEquals(48, decoded.height)
  }

  @Test
  fun jpegRoundTripSolidColorHasLowError() = runTest {
    val original = ImageTestHelpers.solidColor(32, 32, r = 128, g = 128, b = 128)
    val ctx = ImageTestHelpers.testContext()
    ctx.scratchpad["image.output.format"] = ImageFormat.JPEG
    ctx.scratchpad["image.quality"] = 0.95f

    val encoded = encoder.encode(original, ctx)
    val decoded = decoder.decode(encoded, ctx)
    val diff = ImageTestHelpers.peakDifference(original, decoded)
    assertTrue(diff < 10, "JPEG solid color peak diff $diff should be < 10")
  }

  // -----------------------------------------------------------------------
  // PNG roundtrip
  // -----------------------------------------------------------------------

  @Test
  fun pngRoundTripIsLossless() = runTest {
    val original = ImageTestHelpers.checkerboard(32, 32)
    val ctx = ImageTestHelpers.testContext()
    ctx.scratchpad["image.output.format"] = ImageFormat.PNG

    val encoded = encoder.encode(original, ctx)
    val decoded = decoder.decode(encoded, ctx)

    assertEquals(32, decoded.width)
    assertEquals(32, decoded.height)
    val diff = ImageTestHelpers.peakDifference(original, decoded)
    assertEquals(0, diff, "PNG should be lossless")
  }

  // -----------------------------------------------------------------------
  // WebP roundtrip
  // -----------------------------------------------------------------------

  @Test
  fun webpRoundTripPreservesDimensions() = runTest {
    val original = ImageTestHelpers.horizontalGradient(64, 32)
    val ctx = ImageTestHelpers.testContext()
    ctx.scratchpad["image.output.format"] = ImageFormat.WEBP
    ctx.scratchpad["image.quality"] = 0.9f

    val encoded = encoder.encode(original, ctx)
    assertTrue(encoded.isNotEmpty())

    val decoded = decoder.decode(encoded, ctx)
    assertEquals(64, decoded.width)
    assertEquals(32, decoded.height)
  }

  // -----------------------------------------------------------------------  // BMP roundtrip (common encoder → Android decoder)
  // -----------------------------------------------------------------------    

  @Test
  fun bmpDecodeProducesCorrectDimensions() = runTest {
    // Encode with the common BMP encoder, then decode with Android BitmapFactory
    val original = ImageTestHelpers.solidColor(32, 32, r = 255, g = 0, b = 0)
    val ctx = ImageTestHelpers.testContext()
    val bmpEncoder = BmpImageEncoder()
    val bmpBytes = bmpEncoder.encode(original, ctx)
    assertTrue(bmpBytes.isNotEmpty(), "BMP encoded bytes should not be empty")

    val decoded = decoder.decode(bmpBytes, ctx)
    assertEquals(32, decoded.width, "BMP: width should be 32")
    assertEquals(32, decoded.height, "BMP: height should be 32")
  }

  // -----------------------------------------------------------------------    
  // GIF decode (minimal valid GIF)
  // -----------------------------------------------------------------------    

  @Test
  fun gifDecodeProducesCorrectDimensions() = runTest {
    // Minimal valid 1×1 GIF89a with a single red pixel
    val gif = byteArrayOf(
      0x47, 0x49, 0x46, 0x38, 0x39, 0x61,       // GIF89a
      0x01, 0x00, 0x01, 0x00,                     // 1×1
      0x80.toByte(), 0x00, 0x00,                  // GCT flag, bg=0, aspect=0
      0xFF.toByte(), 0x00, 0x00,                  // palette[0] = red
      0x00, 0x00, 0x00,                           // palette[1] = black
      0x2C,                                       // image descriptor
      0x00, 0x00, 0x00, 0x00,                     // left=0, top=0
      0x01, 0x00, 0x01, 0x00,                     // 1×1
      0x00,                                       // no local color table
      0x02,                                       // LZW minimum code size
      0x02, 0x44, 0x01,                           // 2 bytes of LZW data
      0x00,                                       // block terminator
      0x3B,                                       // trailer
    )

    val ctx = ImageTestHelpers.testContext()
    val decoded = decoder.decode(gif, ctx)
    assertEquals(1, decoded.width, "GIF: width should be 1")
    assertEquals(1, decoded.height, "GIF: height should be 1")
  }

  // -----------------------------------------------------------------------    
  // HEIF decode (API 28+)
  // -----------------------------------------------------------------------    

  @Test
  fun heifIsReportedAsSupported() {
    // Android supports HEIF/HEIC decoding from API 28+
    if (Build.VERSION.SDK_INT >= 28) {
      assertTrue(ImageFormat.HEIF in decoder.supportedFormats, "HEIF should be supported on API 28+")
      assertTrue(ImageFormat.HEIC in decoder.supportedFormats, "HEIC should be supported on API 28+")
    }
  }

  // -----------------------------------------------------------------------      // Decode-only formats
  // -----------------------------------------------------------------------

  @Test
  fun decoderReportsAllSupportedFormats() {
    val formats = decoder.supportedFormats
    assertTrue(ImageFormat.JPEG in formats)
    assertTrue(ImageFormat.PNG in formats)
    assertTrue(ImageFormat.WEBP in formats)
    assertTrue(ImageFormat.GIF in formats)
    assertTrue(ImageFormat.BMP in formats)
    assertTrue(ImageFormat.HEIF in formats)
    assertTrue(ImageFormat.HEIC in formats)
    assertTrue(ImageFormat.AVIF in formats)
  }

  @Test
  fun encoderSupportsJpegPngWebp() {
    val formats = encoder.supportedFormats
    assertTrue(ImageFormat.JPEG in formats)
    assertTrue(ImageFormat.PNG in formats)
    assertTrue(ImageFormat.WEBP in formats)
  }
}
