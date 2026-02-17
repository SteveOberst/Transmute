package dev.transmute.image.codecs.jvm

import dev.transmute.core.ImageFormat
import dev.transmute.image.ImageFormatDetector
import dev.transmute.image.ImageTestHelpers.adjustAlphaForComparison
import dev.transmute.image.ImageTestHelpers.checkerboard
import dev.transmute.image.ImageTestHelpers.horizontalGradient
import dev.transmute.image.ImageTestHelpers.meanAbsoluteError
import dev.transmute.image.ImageTestHelpers.pixelAt
import dev.transmute.image.ImageTestHelpers.solidColor
import dev.transmute.image.ImageTestHelpers.testContext
import dev.transmute.image.ImageTestHelpers.testContextWith
import dev.transmute.image.ImageIR
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for extended format support: GIF and TIFF encode/decode,
 * plus WebP decode (TwelveMonkeys imageio-webp is read-only).
 *
 * GIF uses JDK built-in ImageIO.
 * TIFF uses the TwelveMonkeys ImageIO plugin.
 */
class ExtendedFormatCodecTest {

  private val decoder = JvmImageIoDecoder()
  private val encoder = JvmImageIoEncoder()
  private val ctx = testContext()

  private suspend fun encodeGif(ir: ImageIR): ByteArray =
    encoder.encode(ir, testContextWith(format = ImageFormat.GIF))

  private suspend fun encodeTiff(ir: ImageIR): ByteArray =
    encoder.encode(ir, testContextWith(format = ImageFormat.TIFF))

  // --- WebP decode ---

  @Test
  fun webpDecoderDeclaredInSupportedFormats() {
    assertTrue(
      ImageFormat.WEBP in decoder.supportedFormats,
      "Decoder should list WEBP as a supported format",
    )
  }

  // --- GIF ---

  @Test
  fun gifRoundTripSolidColor() = runTest {
    val original = solidColor(64, 64, r = 200, g = 100, b = 50)
    val encoded = encodeGif(original)
    val detected = ImageFormatDetector.detect(encoded)
    assertEquals(ImageFormat.GIF, detected, "Encoded bytes should be detected as GIF")

    val decoded = decoder.decode(encoded, ctx)
    assertEquals(64, decoded.width)
    assertEquals(64, decoded.height)

    // GIF uses a palette - colour may shift slightly
    val center = pixelAt(decoded, 32, 32)
    assertTrue(center[0] in 190..210, "R should be near 200, got ${center[0]}")
    assertTrue(center[1] in 90..110, "G should be near 100, got ${center[1]}")
    assertTrue(center[2] in 40..60, "B should be near 50, got ${center[2]}")
  }

  @Test
  fun gifDimensionsPreserved() = runTest {
    for ((w, h) in listOf(1 to 1, 10 to 10, 100 to 50)) {
      val original = solidColor(w, h, 128, 128, 128)
      val encoded = encodeGif(original)
      val decoded = decoder.decode(encoded, ctx)
      assertEquals(w, decoded.width, "Width should be preserved for ${w}×${h}")
      assertEquals(h, decoded.height, "Height should be preserved for ${w}×${h}")
    }
  }

  @Test
  fun gifFullLoopPreservesSimplePattern() = runTest {
    // Pure black and pure white - GIF palette should handle this perfectly
    val original = checkerboard(64, 64, blockSize = 32,
      colorA = intArrayOf(255, 255, 255, 255),
      colorB = intArrayOf(0, 0, 0, 255),
    )
    val encoded = encodeGif(original)
    val decoded = decoder.decode(encoded, ctx)

    assertEquals(64, decoded.width)
    assertEquals(64, decoded.height)

    // Check two block centers
    val whiteCenter = pixelAt(decoded, 16, 16) // center of top-left white block
    assertTrue(whiteCenter[0] > 240, "White block R should be > 240, got ${whiteCenter[0]}")

    val blackCenter = pixelAt(decoded, 48, 16) // center of second black block
    assertTrue(blackCenter[0] < 15, "Black block R should be < 15, got ${blackCenter[0]}")
  }

  // --- TIFF ---

  @Test
  fun tiffRoundTripSolidColor() = runTest {
    val original = solidColor(64, 64, r = 200, g = 100, b = 50)
    val encoded = encodeTiff(original)
    val detected = ImageFormatDetector.detect(encoded)
    assertEquals(ImageFormat.TIFF, detected, "Encoded bytes should be detected as TIFF")

    val decoded = decoder.decode(encoded, ctx)
    assertEquals(64, decoded.width)
    assertEquals(64, decoded.height)

    // TIFF is lossless (uncompressed) - pixel match should be exact
    val center = pixelAt(decoded, 32, 32)
    assertEquals(200, center[0], "R should be 200")
    assertEquals(100, center[1], "G should be 100")
    assertEquals(50, center[2], "B should be 50")
  }

  @Test
  fun tiffRoundTripGradientLossless() = runTest {
    val original = horizontalGradient(256, 10)
    val encoded = encodeTiff(original)
    val decoded = decoder.decode(encoded, ctx)

    assertEquals(256, decoded.width)
    assertEquals(10, decoded.height)

    // TIFF is lossless - MAE should be 0
    val mae = meanAbsoluteError(
      adjustAlphaForComparison(original),
      adjustAlphaForComparison(decoded),
    )
    assertTrue(mae < 1.0, "TIFF round-trip MAE should be ~0, got $mae")
  }

  @Test
  fun tiffDimensionsPreserved() = runTest {
    for ((w, h) in listOf(1 to 1, 7 to 13, 100 to 50, 640 to 480)) {
      val original = solidColor(w, h, 100, 100, 100)
      val encoded = encodeTiff(original)
      val decoded = decoder.decode(encoded, ctx)
      assertEquals(w, decoded.width, "Width should be preserved for ${w}×${h}")
      assertEquals(h, decoded.height, "Height should be preserved for ${w}×${h}")
    }
  }

  @Test
  fun tiffPreservesRgbWithAlpha() = runTest {
    // Note: TIFF round-trip may or may not preserve alpha depending on the
    // writer; we verify the RGB channels are intact.
    val original = solidColor(32, 32, r = 100, g = 150, b = 200, a = 128)
    val encoded = encodeTiff(original)
    val decoded = decoder.decode(encoded, ctx)

    val center = pixelAt(decoded, 16, 16)
    // RGB should survive losslessly through TIFF
    assertTrue(center[0] in 98..102, "R should be near 100, got ${center[0]}")
    assertTrue(center[1] in 148..152, "G should be near 150, got ${center[1]}")
    assertTrue(center[2] in 198..202, "B should be near 200, got ${center[2]}")
  }

  // --- Cross-format: encode in one format, decode and re-encode in another ---

  @Test
  fun tiffToGifCrossFormat() = runTest {
    val original = solidColor(40, 40, r = 180, g = 90, b = 45)
    val tiffBytes = encodeTiff(original)
    val fromTiff = decoder.decode(tiffBytes, ctx)

    val gifBytes = encodeGif(fromTiff)
    val fromGif = decoder.decode(gifBytes, ctx)

    assertEquals(40, fromGif.width)
    assertEquals(40, fromGif.height)
    assertEquals(ImageFormat.GIF, ImageFormatDetector.detect(gifBytes))
  }

  @Test
  fun gifToTiffCrossFormat() = runTest {
    val original = solidColor(100, 20, r = 100, g = 200, b = 50)
    val gifBytes = encodeGif(original)
    val fromGif = decoder.decode(gifBytes, ctx)

    val tiffBytes = encodeTiff(fromGif)
    val fromTiff = decoder.decode(tiffBytes, ctx)

    assertEquals(100, fromTiff.width)
    assertEquals(20, fromTiff.height)
    assertEquals(ImageFormat.TIFF, ImageFormatDetector.detect(tiffBytes))
  }

}
