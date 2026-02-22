package dev.transmute.image.codecs.jvm

import dev.transmute.core.ImageFormat
import dev.transmute.core.PrintLogger
import dev.transmute.image.ImageFormatDetector
import dev.transmute.image.ImageTestHelpers
import dev.transmute.image.ImageTestHelpers.adjustAlphaForComparison
import dev.transmute.image.ImageTestHelpers.horizontalGradient
import dev.transmute.image.ImageTestHelpers.meanAbsoluteError
import dev.transmute.image.ImageTestHelpers.pixelAt
import dev.transmute.image.ImageTestHelpers.solidColor
import dev.transmute.image.ImageTestHelpers.testContext
import dev.transmute.image.ImageIR
import dev.transmute.image.PixelFormat
import kotlinx.coroutines.test.runTest
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import dev.transmute.image.CanonicalImageDecodeOptions
import dev.transmute.image.WebPEncodeOptions

/**
 * Platform integration tests for WebP on desktop/JVM.
 *
 * Uses the TwelveMonkeys ImageIO WebP plugin via [JvmImageIoDecoder]
 * and [JvmImageIoEncoder].
 *
 * **Note:** TwelveMonkeys `imageio-webp` may be decode-only on some
 * JVM configurations (no WebP writer registered). Encode-dependent
 * roundtrip tests are skipped when the writer is unavailable.
 *
 * Invariants validated when writer IS available:
 * - Encode output is non-empty and starts with RIFF/WEBP magic
 * - Decode succeeds with correct dimensions
 * - Lossy round-trip: pixel values within tolerance (MAE < 10)
 *
 * Invariants validated unconditionally:
 * - Decoder declares WEBP in supportedFormats
 * - Encoder declares WEBP in supportedFormats
 */
class WebPCodecRoundtripTest {

  private val log = PrintLogger
  private val decoder = JvmImageIoDecoder()
  private val encoder = JvmImageIoEncoder()
  private val ctx = testContext()

  /** `true` when the ImageIO WebP writer is available on this JVM. */
  private val canEncodeWebp: Boolean =
    ImageIO.getImageWritersByFormatName("webp").asSequence().firstOrNull() != null

  private suspend fun encodeWebp(ir: ImageIR, quality: Float = 0.85f): ByteArray =
    encoder.encode(ir, ImageFormat.WEBP, WebPEncodeOptions(quality = quality), ctx)

  private inline fun requireWebpWriter(block: () -> Unit) {
    if (!canEncodeWebp) {
      log.warn("SKIP: WebP writer not available on this JVM - encode/roundtrip tests skipped")
      return
    }
    block()
  }

  // --- Encoder/Decoder declare WebP support ---

  @Test
  fun decoderSupportsWebP() {
    assertTrue(
      ImageFormat.WEBP in decoder.supportedFormats,
      "WebP: JvmImageIoDecoder should list WEBP in supportedFormats",
    )
  }

  @Test
  fun encoderSupportsWebP() {
    assertTrue(
      ImageFormat.WEBP in encoder.supportedFormats,
      "WebP: JvmImageIoEncoder should list WEBP in supportedFormats",
    )
  }

  // --- Magic bytes / container validation ---

  @Test
  fun encodedWebpHasCorrectMagicBytes() = runTest {
    requireWebpWriter {
      val ir = solidColor(32, 32, r = 128, g = 64, b = 32)
      val encoded = encodeWebp(ir)

      assertTrue(encoded.size > 12, "WebP: encoded output should be > 12 bytes, got ${encoded.size}")
      // WebP files start with RIFF....WEBP
      assertEquals('R'.code.toByte(), encoded[0], "WebP: byte 0 should be 'R'")
      assertEquals('I'.code.toByte(), encoded[1], "WebP: byte 1 should be 'I'")
      assertEquals('F'.code.toByte(), encoded[2], "WebP: byte 2 should be 'F'")
      assertEquals('F'.code.toByte(), encoded[3], "WebP: byte 3 should be 'F'")
      assertEquals('W'.code.toByte(), encoded[8], "WebP: byte 8 should be 'W'")
      assertEquals('E'.code.toByte(), encoded[9], "WebP: byte 9 should be 'E'")
      assertEquals('B'.code.toByte(), encoded[10], "WebP: byte 10 should be 'B'")
      assertEquals('P'.code.toByte(), encoded[11], "WebP: byte 11 should be 'P'")
    }
  }

  @Test
  fun encodedWebpDetectedAsWebP() = runTest {
    requireWebpWriter {
      val ir = solidColor(64, 64, r = 200, g = 100, b = 50)
      val encoded = encodeWebp(ir)
      assertEquals(
        ImageFormat.WEBP,
        ImageFormatDetector.detect(encoded),
        "WebP: encoded bytes should be detected as WEBP",
      )
    }
  }

  // --- Lossy round-trip: solid color ---

  @Test
  fun roundTripSolidColorWithinTolerance() = runTest {
    requireWebpWriter {
      val original = solidColor(64, 64, r = 200, g = 100, b = 50)
      val encoded = encodeWebp(original, quality = 0.90f)
      assertTrue(encoded.isNotEmpty(), "WebP: encode output must not be empty")

      val decoded = decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)
      assertEquals(64, decoded.width, "WebP: width mismatch after round-trip")
      assertEquals(64, decoded.height, "WebP: height mismatch after round-trip")
      assertEquals(PixelFormat.RGBA_8888, decoded.pixelFormat, "WebP: pixel format mismatch")

      // WebP is lossy - check center pixel within tolerance
      val center = pixelAt(decoded, 32, 32)
      assertTrue(center[0] in 185..215, "WebP: R should be near 200, got ${center[0]}")
      assertTrue(center[1] in 85..115, "WebP: G should be near 100, got ${center[1]}")
      assertTrue(center[2] in 35..65, "WebP: B should be near 50, got ${center[2]}")
    }
  }

  // --- Lossy round-trip: gradient ---

  @Test
  fun roundTripGradientWithinTolerance() = runTest {
    requireWebpWriter {
      val original = horizontalGradient(256, 50,
        startR = 20, startG = 20, startB = 20,
        endR = 235, endG = 235, endB = 235,
      )
      val encoded = encodeWebp(original, quality = 0.90f)
      val decoded = decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)

      assertEquals(256, decoded.width, "WebP: gradient width mismatch")
      assertEquals(50, decoded.height, "WebP: gradient height mismatch")

      // MAE on a smooth gradient at Q90 should be low
      val mae = meanAbsoluteError(
        adjustAlphaForComparison(original),
        adjustAlphaForComparison(decoded),
      )
      assertTrue(mae < 10.0, "WebP: Q90 gradient MAE should be < 10, got $mae")
    }
  }

  // --- Dimension preservation ---

  @Test
  fun dimensionsPreservedThroughWebpRoundTrip() = runTest {
    requireWebpWriter {
      for ((w, h) in listOf(1 to 1, 7 to 13, 100 to 50, 640 to 480)) {
        val original = solidColor(w, h, 100, 100, 100)
        val encoded = encodeWebp(original)
        val decoded = decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)
        assertEquals(w, decoded.width, "WebP: width not preserved for ${w}×${h}")
        assertEquals(h, decoded.height, "WebP: height not preserved for ${w}×${h}")
      }
    }
  }

  // --- Checkerboard (high-frequency detail) ---

  @Test
  fun roundTripCheckerboardReasonable() = runTest {
    requireWebpWriter {
      val original = ImageTestHelpers.checkerboard(
        64, 64, blockSize = 16,
        colorA = intArrayOf(255, 255, 255, 255),
        colorB = intArrayOf(0, 0, 0, 255),
      )
      val encoded = encodeWebp(original, quality = 0.95f)
      val decoded = decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)

      assertEquals(64, decoded.width, "WebP: checkerboard width mismatch")
      assertEquals(64, decoded.height, "WebP: checkerboard height mismatch")

      // Block centers should be recognizable even with lossy compression
      val whiteCenter = pixelAt(decoded, 8, 8) // center of white block
      assertTrue(whiteCenter[0] > 230, "WebP: white block R should be > 230, got ${whiteCenter[0]}")

      val blackCenter = pixelAt(decoded, 24, 8) // center of black block
      assertTrue(blackCenter[0] < 25, "WebP: black block R should be < 25, got ${blackCenter[0]}")
    }
  }

  // --- Quality affects file size ---

  @Test
  fun highQualityProducesLargerFilesThanLow() = runTest {
    requireWebpWriter {
      val original = horizontalGradient(200, 200)
      val highBytes = encodeWebp(original, quality = 0.95f)
      val lowBytes = encodeWebp(original, quality = 0.30f)

      assertTrue(
        highBytes.size > lowBytes.size,
        "WebP: high quality (${highBytes.size} B) should be larger than low quality (${lowBytes.size} B)",
      )
    }
  }

}
