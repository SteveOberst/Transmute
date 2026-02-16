package dev.transmute.image.codecs.jvm

import dev.transmute.core.ImageFormat
import dev.transmute.image.ImageTestHelpers
import dev.transmute.image.ImageTestHelpers.horizontalGradient
import dev.transmute.image.ImageTestHelpers.meanAbsoluteError
import dev.transmute.image.ImageTestHelpers.peakDifference
import dev.transmute.image.ImageTestHelpers.pixelAt
import dev.transmute.image.ImageTestHelpers.solidColor
import dev.transmute.image.ImageTestHelpers.testContext
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.ImageIR
import dev.transmute.image.PixelFormat
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [JvmImageIoDecoder] and [JvmImageIoEncoder].
 *
 * These tests encode synthetic images to real JPEG/PNG bytes, decode them
 * back to [ImageIR], and verify pixel correctness with appropriate tolerance.
 */
class JvmImageCodecTest {

  private val decoder = JvmImageIoDecoder()
  private val encoder = JvmImageIoEncoder()
  private val ctx = testContext()

  private fun ctxWith(
    format: ImageFormat,
    quality: Float? = null,
  ) = ctx.copy(
    scratchpad = ctx.scratchpad.toMutableMap().apply {
      this["image.output.format"] = format
      if (quality != null) this["image.output.quality"] = quality else remove("image.output.quality")
    },
  )

  private suspend fun encodePng(ir: ImageIR): ByteArray =
    encoder.encode(ir, ctxWith(format = ImageFormat.PNG))

  private suspend fun encodeJpeg(ir: ImageIR, quality: Float): ByteArray =
    encoder.encode(ir, ctxWith(format = ImageFormat.JPEG, quality = quality))

  // --- PNG round-trip (lossless) ---

  @Test
  fun pngRoundTripIsLossless() = runTest {
    val original = solidColor(64, 64, r = 200, g = 100, b = 50)
    val encoded = encodePng(original)
    val decoded = decoder.decode(encoded, ctx)

    assertEquals(64, decoded.width)
    assertEquals(64, decoded.height)
    assertEquals(PixelFormat.RGBA_8888, decoded.pixelFormat)

    // PNG is lossless — RGB channels should be identical.
    // (Alpha channel may differ since PNG preserves it differently.)
    for (y in 0 until 64) {
      for (x in 0 until 64) {
        val pixel = pixelAt(decoded, x, y)
        assertEquals(200, pixel[0], "R at ($x,$y)")
        assertEquals(100, pixel[1], "G at ($x,$y)")
        assertEquals(50, pixel[2], "B at ($x,$y)")
      }
    }
  }

  @Test
  fun pngRoundTripGradientLossless() = runTest {
    val original = horizontalGradient(256, 10)
    val encoded = encodePng(original)
    val decoded = decoder.decode(encoded, ctx)

    assertEquals(256, decoded.width)
    assertEquals(10, decoded.height)

    // Check gradient endpoints
    val left = pixelAt(decoded, 0, 5)
    val right = pixelAt(decoded, 255, 5)
    assertEquals(0, left[0], "Left R should be 0")
    assertEquals(255, right[0], "Right R should be 255")

    // Lossless — peak difference should be 0 for RGB
    val diff = peakDifference(
      original.adjustAlphaForComparison(),
      decoded.adjustAlphaForComparison(),
    )
    assertTrue(diff <= 1, "PNG round-trip should be lossless, got peak diff = $diff")
  }

  // --- JPEG round-trip (lossy) ---

  @Test
  fun jpegRoundTripSolidColorCloseToOriginal() = runTest {
    val original = solidColor(100, 100, r = 128, g = 64, b = 192)
    val encoded = encodeJpeg(original, quality = 0.95f)
    val decoded = decoder.decode(encoded, ctx)

    assertEquals(100, decoded.width)
    assertEquals(100, decoded.height)

    // JPEG at quality=95: solid color should be very close (within ~5 per channel).
    val centerPixel = pixelAt(decoded, 50, 50)
    assertTrue(centerPixel[0] in 123..133, "R should be near 128, got ${centerPixel[0]}")
    assertTrue(centerPixel[1] in 59..69, "G should be near 64, got ${centerPixel[1]}")
    assertTrue(centerPixel[2] in 187..197, "B should be near 192, got ${centerPixel[2]}")
  }

  @Test
  fun jpegRoundTripGradientReasonablyAccurate() = runTest {
    val original = horizontalGradient(256, 50,
      startR = 20, startG = 20, startB = 20,
      endR = 235, endG = 235, endB = 235,
    )
    val encoded = encodeJpeg(original, quality = 0.95f)
    val decoded = decoder.decode(encoded, ctx)

    assertEquals(256, decoded.width)
    assertEquals(50, decoded.height)

    // JPEG quality 95 on a smooth gradient should have low MAE.
    val mae = meanAbsoluteError(
      original.adjustAlphaForComparison(),
      decoded.adjustAlphaForComparison(),
    )
    assertTrue(mae < 5.0, "JPEG Q95 gradient MAE should be < 5, got $mae")
  }

  @Test
  fun jpegHighQualityProducesLargerFilesThanLow() = runTest {
    val original = horizontalGradient(200, 200)
    val highBytes = encodeJpeg(original, quality = 0.95f)
    val lowBytes = encodeJpeg(original, quality = 0.40f)

    assertTrue(
      highBytes.size > lowBytes.size,
      "High quality (${highBytes.size} bytes) should be larger than low quality (${lowBytes.size} bytes)",
    )
  }

  @Test
  fun jpegLowQualityHasMoreArtifacts() = runTest {
    val original = horizontalGradient(200, 200)
    val highEncoded = encodeJpeg(original, quality = 0.95f)
    val lowEncoded = encodeJpeg(original, quality = 0.40f)

    val highDecoded = decoder.decode(highEncoded, ctx)
    val lowDecoded = decoder.decode(lowEncoded, ctx)

    val highMae = meanAbsoluteError(
      original.adjustAlphaForComparison(),
      highDecoded.adjustAlphaForComparison(),
    )
    val lowMae = meanAbsoluteError(
      original.adjustAlphaForComparison(),
      lowDecoded.adjustAlphaForComparison(),
    )

    assertTrue(lowMae > highMae,
      "Low quality MAE ($lowMae) should be worse than high quality MAE ($highMae)")
  }

  // --- JPEG file structure ---

  @Test
  fun jpegOutputStartsWithCorrectMagicBytes() = runTest {
    val original = solidColor(10, 10, 128, 128, 128)
    val encoded = encodeJpeg(original, quality = 0.90f)

    // JPEG files always start with FF D8 FF
    assertTrue(encoded.size > 4, "JPEG output should be non-trivial")
    assertEquals(0xFF.toByte(), encoded[0], "First byte should be 0xFF")
    assertEquals(0xD8.toByte(), encoded[1], "Second byte should be 0xD8")
    assertEquals(0xFF.toByte(), encoded[2], "Third byte should be 0xFF")
  }

  @Test
  fun pngOutputStartsWithCorrectMagicBytes() = runTest {
    val original = solidColor(10, 10, 128, 128, 128)
    val encoded = encodePng(original)

    // PNG magic: 89 50 4E 47 0D 0A 1A 0A
    assertTrue(encoded.size > 8, "PNG output should be non-trivial")
    assertEquals(0x89.toByte(), encoded[0])
    assertEquals(0x50.toByte(), encoded[1]) // 'P'
    assertEquals(0x4E.toByte(), encoded[2]) // 'N'
    assertEquals(0x47.toByte(), encoded[3]) // 'G'
  }

  // --- Dimension preservation ---

  @Test
  fun dimensionsPreservedThroughJpegRoundTrip() = runTest {
    for ((w, h) in listOf(1 to 1, 7 to 13, 100 to 50, 640 to 480)) {
      val original = solidColor(w, h, 100, 100, 100)
      val encoded = encodeJpeg(original, quality = 0.90f)
      val decoded = decoder.decode(encoded, ctx)
      assertEquals(w, decoded.width, "Width should be preserved for ${w}×${h}")
      assertEquals(h, decoded.height, "Height should be preserved for ${w}×${h}")
    }
  }

  @Test
  fun dimensionsPreservedThroughPngRoundTrip() = runTest {
    for ((w, h) in listOf(1 to 1, 7 to 13, 100 to 50, 640 to 480)) {
      val original = solidColor(w, h, 100, 100, 100)
      val encoded = encodePng(original)
      val decoded = decoder.decode(encoded, ctx)
      assertEquals(w, decoded.width, "Width should be preserved for ${w}×${h}")
      assertEquals(h, decoded.height, "Height should be preserved for ${w}×${h}")
    }
  }

  // --- Checkerboard stress test ---

  @Test
  fun jpegCheckerboardRoundTrip() = runTest {
    val original = ImageTestHelpers.checkerboard(128, 128, blockSize = 16)
    val encoded = encodeJpeg(original, quality = 0.95f)
    val decoded = decoder.decode(encoded, ctx)

    assertEquals(128, decoded.width)
    assertEquals(128, decoded.height)

    // Checkerboard is hard for JPEG (sharp edges). Even at Q95, artifacts are expected
    // around block boundaries. But the center of each block should be fairly accurate.
    val blockCenter = pixelAt(decoded, 8, 8) // Center of top-left white block
    assertTrue(blockCenter[0] > 240, "White block center R should be > 240, got ${blockCenter[0]}")

    val blackCenter = pixelAt(decoded, 24, 8) // Center of second black block
    assertTrue(blackCenter[0] < 15, "Black block center R should be < 15, got ${blackCenter[0]}")
  }

  // --- End-to-end: scale + encode + decode ---

  @Test
  fun scaleDownThenJpegRoundTrip() = runTest {
    val original = horizontalGradient(800, 600)

    // Scale down
    val scaleTransform = dev.transmute.image.transform.ImageScaleTransform(
      maxWidth = 200, maxHeight = 150,
    )
    val scaled = scaleTransform.apply(original, ctx)
    assertEquals(200, scaled.width)
    assertEquals(150, scaled.height)

    // Encode to JPEG
    val encoded = encodeJpeg(scaled, quality = 0.85f)
    assertTrue(encoded.size < 200 * 150 * 4, "JPEG should compress significantly")

    // Decode back
    val decoded = decoder.decode(encoded, ctx)
    assertEquals(200, decoded.width)
    assertEquals(150, decoded.height)

    // Gradient should still be recognizable
    val left = pixelAt(decoded, 5, 75)
    val right = pixelAt(decoded, 195, 75)
    assertTrue(left[0] < 30, "Left R after scale+JPEG should still be dark, got ${left[0]}")
    assertTrue(right[0] > 225, "Right R after scale+JPEG should still be bright, got ${right[0]}")
  }

  // --- Helper: adjust alpha for fair comparison ---

  /**
   * JPEG discards alpha. When comparing original (with alpha=255) against a
   * JPEG round-trip result (which might have alpha=255 from decode), we need
   * to ensure alpha channels match for peakDifference / MAE comparisons.
   */
  private fun ImageIR.adjustAlphaForComparison(): ImageIR {
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
