package dev.transmute.image.codecs.ios

import dev.transmute.core.ImageFormat
import dev.transmute.core.OutputFormat
import dev.transmute.image.ImageTestHelpers
import dev.transmute.image.CanonicalImageEncodeOptions
import dev.transmute.image.HeifEncodeOptions
import dev.transmute.image.JpegEncodeOptions
import dev.transmute.image.PngEncodeOptions
import dev.transmute.image.WebPEncodeOptions
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import dev.transmute.image.CanonicalImageDecodeOptions

/**
 * iOS integration tests for [IosImageIoDecoder] and [IosImageIoEncoder].
 *
 * These tests run on the iOS simulator via Kotlin/Native and exercise the
 * real CoreGraphics / ImageIO APIs.
 *
 * Run: `./gradlew :transmute-image:iosSimulatorArm64Test`
 */
class IosImageCodecTest {

  private val decoder = IosImageIoDecoder()
  private val encoder = IosImageIoEncoder()

  // JPEG roundtrip

  @Test
  fun jpegRoundTripPreservesDimensions() = runTest {
    val original = ImageTestHelpers.solidColor(64, 48, r = 200, g = 100, b = 50)
    val ctx = ImageTestHelpers.testContext()
    val encoded = encoder.encode(original, ImageFormat.JPEG, JpegEncodeOptions(), ctx)
    assertTrue(encoded.isNotEmpty(), "JPEG encoded bytes should not be empty")

    val decoded = decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)
    assertEquals(64, decoded.width, "Width should be preserved")
    assertEquals(48, decoded.height, "Height should be preserved")
  }

  @Test
  fun jpegRoundTripSolidColorHasLowError() = runTest {
    val original = ImageTestHelpers.solidColor(32, 32, r = 128, g = 128, b = 128)
    val ctx = ImageTestHelpers.testContext()
    val encoded = encoder.encode(original, ImageFormat.JPEG, JpegEncodeOptions(quality = 0.95f), ctx)
    val decoded = decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)
    val diff = ImageTestHelpers.peakDifference(original, decoded)
    assertTrue(diff < 15,
      "JPEG solid color peak diff $diff should be < 15")
  }

  // PNG roundtrip (lossless)

  @Test
  fun pngRoundTripIsLossless() = runTest {
    val original = ImageTestHelpers.checkerboard(32, 32)
    val ctx = ImageTestHelpers.testContext()
    val encoded = encoder.encode(original, ImageFormat.PNG, PngEncodeOptions(), ctx)
    val decoded = decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)

    assertEquals(32, decoded.width)
    assertEquals(32, decoded.height)
    val diff = ImageTestHelpers.peakDifference(original, decoded)
    assertEquals(0, diff, "PNG lossless: diff should be 0, was $diff")
  }

  // WebP roundtrip

  @Test
  fun webpRoundTripPreservesDimensions() = runTest {
    val original = ImageTestHelpers.horizontalGradient(64, 32)
    val ctx = ImageTestHelpers.testContext()
    val encoded = try {
      encoder.encode(original, ImageFormat.WEBP, WebPEncodeOptions(), ctx)
    } catch (_: IllegalStateException) {
      // WebP encoding not supported on this simulator — skip.
      println("SKIP: WebP encoding not available on this simulator")
      return@runTest
    }
    assertTrue(encoded.isNotEmpty(), "WebP encoded bytes should not be empty")

    val decoded = decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)
    assertEquals(64, decoded.width)
    assertEquals(32, decoded.height)
  }

  // HEIF roundtrip

  @Test
  fun heifRoundTripPreservesDimensions() = runTest {
    val original = ImageTestHelpers.solidColor(64, 48, r = 100, g = 200, b = 50)
    val ctx = ImageTestHelpers.testContext()
    val encoded = try {
      encoder.encode(original, ImageFormat.HEIF, HeifEncodeOptions(format = ImageFormat.HEIF), ctx)
    } catch (_: IllegalStateException) {
      // HEIF encoding not supported on this simulator — skip.
      println("SKIP: HEIF encoding not available on this simulator")
      return@runTest
    }
    assertTrue(encoded.isNotEmpty(), "HEIF encoded bytes should not be empty")

    val decoded = decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)
    assertEquals(64, decoded.width, "Width should be preserved")
    assertEquals(48, decoded.height, "Height should be preserved")
  }

  // TIFF roundtrip

  @Test
  fun tiffRoundTripPreservesDimensions() = runTest {
    val original = ImageTestHelpers.solidColor(32, 32, r = 0, g = 255, b = 0)
    val ctx = ImageTestHelpers.testContext()
    val encoded =
      encoder.encode(original, ImageFormat.TIFF, CanonicalImageEncodeOptions(outputFormat = OutputFormat.Exact(ImageFormat.TIFF)), ctx)
    assertTrue(encoded.isNotEmpty(), "TIFF encoded bytes should not be empty")

    val decoded = decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)
    assertEquals(32, decoded.width)
    assertEquals(32, decoded.height)
  }

  // GIF roundtrip

  @Test
  fun gifRoundTripPreservesDimensions() = runTest {
    val original = ImageTestHelpers.solidColor(16, 16, r = 255, g = 0, b = 0)
    val ctx = ImageTestHelpers.testContext()
    val encoded =
      encoder.encode(original, ImageFormat.GIF, CanonicalImageEncodeOptions(outputFormat = OutputFormat.Exact(ImageFormat.GIF)), ctx)
    assertTrue(encoded.isNotEmpty(), "GIF encoded bytes should not be empty")

    val decoded = decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)
    assertEquals(16, decoded.width)
    assertEquals(16, decoded.height)
  }

  // BMP roundtrip

  @Test
  fun bmpRoundTripPreservesDimensions() = runTest {
    val original = ImageTestHelpers.solidColor(32, 32, r = 0, g = 0, b = 255)
    val ctx = ImageTestHelpers.testContext()
    val encoded =
      encoder.encode(original, ImageFormat.BMP, CanonicalImageEncodeOptions(outputFormat = OutputFormat.Exact(ImageFormat.BMP)), ctx)
    assertTrue(encoded.isNotEmpty(), "BMP encoded bytes should not be empty")

    val decoded = decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)
    assertEquals(32, decoded.width)
    assertEquals(32, decoded.height)
  }

  // Format assertions

  @Test
  fun decoderReportsAllExpectedFormats() {
    val formats = decoder.supportedFormats
    assertTrue(ImageFormat.JPEG in formats, "Decoder should support JPEG")
    assertTrue(ImageFormat.PNG in formats, "Decoder should support PNG")
    assertTrue(ImageFormat.WEBP in formats, "Decoder should support WebP")
    assertTrue(ImageFormat.HEIF in formats, "Decoder should support HEIF")
    assertTrue(ImageFormat.GIF in formats, "Decoder should support GIF")
    assertTrue(ImageFormat.BMP in formats, "Decoder should support BMP")
    assertTrue(ImageFormat.TIFF in formats, "Decoder should support TIFF")
  }

  @Test
  fun encoderReportsAllExpectedFormats() {
    val formats = encoder.supportedFormats
    assertTrue(ImageFormat.JPEG in formats, "Encoder should support JPEG")
    assertTrue(ImageFormat.PNG in formats, "Encoder should support PNG")
    assertTrue(ImageFormat.WEBP in formats, "Encoder should support WebP")
    assertTrue(ImageFormat.HEIF in formats, "Encoder should support HEIF")
    assertTrue(ImageFormat.TIFF in formats, "Encoder should support TIFF")
    assertTrue(ImageFormat.GIF in formats, "Encoder should support GIF")
  }
}
