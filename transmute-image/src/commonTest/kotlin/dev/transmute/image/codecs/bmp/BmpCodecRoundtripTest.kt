package dev.transmute.image.codecs.bmp

import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.ImageFormat
import dev.transmute.image.ImageTestHelpers
import dev.transmute.image.ImageTestHelpers.horizontalGradient
import dev.transmute.image.ImageTestHelpers.meanAbsoluteError
import dev.transmute.image.ImageTestHelpers.peakDifference
import dev.transmute.image.ImageTestHelpers.pixelAt
import dev.transmute.image.ImageTestHelpers.solidColor
import dev.transmute.image.ImageTestHelpers.testContext
import dev.transmute.image.ImageIR
import dev.transmute.image.PixelFormat
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import dev.transmute.image.CanonicalImageEncodeOptions
import dev.transmute.image.CanonicalImageDecodeOptions

/**
 * Platform integration tests for the pure-Kotlin BMP codec.
 *
 * Runs on ALL targets (Android, iOS, Desktop/JVM) because
 * [BmpImageDecoder] and [BmpImageEncoder] are implemented in commonMain
 * with no platform dependencies.
 *
 * Invariants validated:
 * - Encode output is non-empty and starts with "BM" magic bytes
 * - Decode succeeds and produces the expected dimensions
 * - Lossless round-trip: decoded RGB pixels match the original exactly
 *   (BMP is lossless; alpha is dropped because the encoder writes 24-bit)
 */
class BmpCodecRoundtripTest {

  private val decoder = BmpImageDecoder()
  private val encoder = BmpImageEncoder()
  private val ctx = testContext()

  // --- Magic bytes ---

  @Test
  fun encodedBmpStartsWithMagicBytes() = runTest {
    val ir = solidColor(16, 16, r = 128, g = 64, b = 32)
    val encoded = encoder.encode(ir, ImageFormat.Bmp, CanonicalImageEncodeOptions(), ctx)

    assertTrue(encoded.size > 54, "BMP: encoded output should be > 54 bytes (header size), got ${encoded.size}")
    assertEquals('B'.code.toByte(), encoded.data[0], "BMP: first byte should be 'B'")
    assertEquals('M'.code.toByte(), encoded.data[1], "BMP: second byte should be 'M'")
  }

  // --- Lossless round-trip: solid color ---

  @Test
  fun roundTripSolidColorIsLossless() = runTest {
    val original = solidColor(32, 32, r = 200, g = 100, b = 50)
    val encoded = encoder.encode(original, ImageFormat.Bmp, CanonicalImageEncodeOptions(), ctx)
    assertTrue(encoded.isNotEmpty(), "BMP: encode output must not be empty")

    val decoded = decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)
    assertEquals(32, decoded.width, "BMP: width mismatch after round-trip")
    assertEquals(32, decoded.height, "BMP: height mismatch after round-trip")
    assertEquals(PixelFormat.RGBA_8888, decoded.pixelFormat, "BMP: pixel format mismatch")

    // BMP encoder writes 24-bit (no alpha); decoder produces 0xFF alpha.
    // RGB channels must match exactly since BMP is lossless.
    for (y in 0 until 32) {
      for (x in 0 until 32) {
        val pixel = pixelAt(decoded, x, y)
        assertEquals(200, pixel[0], "BMP: R mismatch at ($x,$y)")
        assertEquals(100, pixel[1], "BMP: G mismatch at ($x,$y)")
        assertEquals(50, pixel[2], "BMP: B mismatch at ($x,$y)")
      }
    }
  }

  // --- Lossless round-trip: gradient ---

  @Test
  fun roundTripGradientIsLossless() = runTest {
    val original = horizontalGradient(256, 10)
    val encoded = encoder.encode(original, ImageFormat.Bmp, CanonicalImageEncodeOptions(), ctx)
    val decoded = decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)

    assertEquals(256, decoded.width, "BMP: gradient width mismatch")
    assertEquals(10, decoded.height, "BMP: gradient height mismatch")

    // Gradient endpoints
    val left = pixelAt(decoded, 0, 5)
    val right = pixelAt(decoded, 255, 5)
    assertEquals(0, left[0], "BMP: left gradient R should be 0")
    assertEquals(255, right[0], "BMP: right gradient R should be 255")

    // Lossless - compare RGB channels only
    // (alpha ignored: encoder writes 24-bit, decoder sets alpha to 0xFF)
    val diff = peakDifference(
      original.adjustAlphaTo0xFF(),
      decoded.adjustAlphaTo0xFF(),
    )
    assertTrue(diff == 0, "BMP: lossless round-trip should have zero peak diff, got $diff")
  }

  // --- Dimension preservation ---

  @Test
  fun dimensionsPreservedForVariousSizes() = runTest {
    val sizes = listOf(1 to 1, 7 to 13, 32 to 32, 100 to 50, 640 to 480)
    for ((w, h) in sizes) {
      val original = solidColor(w, h, 128, 128, 128)
      val encoded = encoder.encode(original, ImageFormat.Bmp, CanonicalImageEncodeOptions(), ctx)
      val decoded = decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)
      assertEquals(w, decoded.width, "BMP: width not preserved for ${w}×${h}")
      assertEquals(h, decoded.height, "BMP: height not preserved for ${w}×${h}")
    }
  }

  // --- Checkerboard pattern ---

  @Test
  fun roundTripCheckerboardIsLossless() = runTest {
    val original = ImageTestHelpers.checkerboard(
      64, 64, blockSize = 8,
      colorA = intArrayOf(255, 0, 0, 255),
      colorB = intArrayOf(0, 0, 255, 255),
    )
    val encoded = encoder.encode(original, ImageFormat.Bmp, CanonicalImageEncodeOptions(), ctx)
    val decoded = decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)

    assertEquals(64, decoded.width, "BMP: checkerboard width mismatch")
    assertEquals(64, decoded.height, "BMP: checkerboard height mismatch")

    // Check block centers
    val redBlock = pixelAt(decoded, 4, 4)   // center of first (red) block
    assertEquals(255, redBlock[0], "BMP: red block R channel")
    assertEquals(0, redBlock[1], "BMP: red block G channel")
    assertEquals(0, redBlock[2], "BMP: red block B channel")

    val blueBlock = pixelAt(decoded, 12, 4) // center of second (blue) block
    assertEquals(0, blueBlock[0], "BMP: blue block R channel")
    assertEquals(0, blueBlock[1], "BMP: blue block G channel")
    assertEquals(255, blueBlock[2], "BMP: blue block B channel")
  }

  // --- MAE for full-image comparison ---

  @Test
  fun roundTripMeanAbsoluteErrorIsZero() = runTest {
    val original = horizontalGradient(
      128, 32,
      startR = 10, startG = 50, startB = 200,
      endR = 245, endG = 200, endB = 10,
    )
    val encoded = encoder.encode(original, ImageFormat.Bmp, CanonicalImageEncodeOptions(), ctx)
    val decoded = decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)

    val mae = meanAbsoluteError(
      original.adjustAlphaTo0xFF(),
      decoded.adjustAlphaTo0xFF(),
    )
    assertTrue(mae == 0.0, "BMP: lossless round-trip MAE should be 0, got $mae")
  }

  // --- Helper ---

  /**
   * Force all alpha bytes to 0xFF so we can compare RGB faithfully.
   * BMP encoder writes 24-bit (drops alpha); decoder sets alpha to 0xFF.
   */
  private fun ImageIR.adjustAlphaTo0xFF(): ImageIR {
    if (pixelFormat != PixelFormat.RGBA_8888) return this
    val buf = (buffer as ByteArrayPixelBuffer).data.copyOf()
    for (y in 0 until height) {
      for (x in 0 until width) {
        buf[y * stride + x * 4 + 3] = 0xFF.toByte()
      }
    }
    return copy(buffer = ByteArrayPixelBuffer(buf))
  }
}
