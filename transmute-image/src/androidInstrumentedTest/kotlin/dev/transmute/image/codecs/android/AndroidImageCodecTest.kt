package dev.transmute.image.codecs.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.transmute.core.ImageFormat
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.ImageTestHelpers
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

  // -----------------------------------------------------------------------
  // Decode-only formats
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
