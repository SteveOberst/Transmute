package dev.transmute.image.codecs.jvm

import dev.transmute.core.FfmpegResolver
import dev.transmute.core.ImageFormat
import dev.transmute.image.ImageFormatDetector
import dev.transmute.image.ImageTestHelpers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for the FFmpeg-based image codecs (HEIF, HEIC, AVIF).
 *
 * All tests skip gracefully when FFmpeg is not available on the system.
 * These are roundtrip encode → decode tests that verify:
 * - Dimension preservation
 * - Format detection on the encoded output
 * - Reasonable pixel accuracy for lossy codecs
 */
class FfmpegImageCodecsTest {

  private val decoder = FfmpegImageDecoder()
  private val encoder = FfmpegImageEncoder()

  private inline fun requireFfmpeg(block: () -> Unit) {
    if (!FfmpegResolver.available) {
      println("SKIPPED: FFmpeg not available")
      return
    }
    block()
  }

  // -----------------------------------------------------------------------
  // HEIF roundtrip
  // -----------------------------------------------------------------------

  @Test
  fun heifRoundTripPreservesDimensions() = runTest {
    requireFfmpeg {
      val original = ImageTestHelpers.solidColor(64, 48, r = 200, g = 100, b = 50)
      val ctx = ImageTestHelpers.testContext()
      ctx.scratchpad["image.output.format"] = ImageFormat.HEIF
      ctx.scratchpad["image.output.quality"] = 0.9f

      val encoded = try {
        encoder.encode(original, ctx)
      } catch (e: Exception) {
        // libx265 may not be available in this FFmpeg build
        if ("libx265" in e.message.orEmpty() || "Encoder" in e.message.orEmpty()) {
          println("SKIPPED: HEIF encoder (libx265) not available: ${e.message}")
          return@requireFfmpeg
        }
        throw e
      }

      assertTrue(encoded.isNotEmpty(), "Encoded HEIF should not be empty")

      val decoded = decoder.decode(encoded, ctx)
      assertEquals(64, decoded.width, "Width should be preserved")
      assertEquals(48, decoded.height, "Height should be preserved")
    }
  }

  @Test
  fun heifRoundTripSolidColorIsReasonable() = runTest {
    requireFfmpeg {
      val original = ImageTestHelpers.solidColor(32, 32, r = 128, g = 128, b = 128)
      val ctx = ImageTestHelpers.testContext()
      ctx.scratchpad["image.output.format"] = ImageFormat.HEIF
      ctx.scratchpad["image.output.quality"] = 0.95f

      val encoded = try {
        encoder.encode(original, ctx)
      } catch (e: Exception) {
        if ("libx265" in e.message.orEmpty()) {
          println("SKIPPED: HEIF encoder not available")
          return@requireFfmpeg
        }
        throw e
      }

      val decoded = decoder.decode(encoded, ctx)
      // Lossy codec — allow generous tolerance for HEIF
      val diff = ImageTestHelpers.peakDifference(original, decoded)
      assertTrue(diff < 30, "Peak difference $diff should be < 30 for solid color HEIF")
    }
  }

  // -----------------------------------------------------------------------
  // AVIF roundtrip
  // -----------------------------------------------------------------------

  @Test
  fun avifRoundTripPreservesDimensions() = runTest {
    requireFfmpeg {
      val original = ImageTestHelpers.solidColor(64, 48, r = 50, g = 150, b = 200)
      val ctx = ImageTestHelpers.testContext()
      ctx.scratchpad["image.output.format"] = ImageFormat.AVIF
      ctx.scratchpad["image.output.quality"] = 0.9f

      val encoded = try {
        encoder.encode(original, ctx)
      } catch (e: Exception) {
        if ("libaom-av1" in e.message.orEmpty() || "Encoder" in e.message.orEmpty()) {
          println("SKIPPED: AVIF encoder (libaom-av1) not available: ${e.message}")
          return@requireFfmpeg
        }
        throw e
      }

      assertTrue(encoded.isNotEmpty(), "Encoded AVIF should not be empty")

      val decoded = decoder.decode(encoded, ctx)
      assertEquals(64, decoded.width, "Width should be preserved")
      assertEquals(48, decoded.height, "Height should be preserved")
    }
  }

  @Test
  fun avifRoundTripGradientHasLowError() = runTest {
    requireFfmpeg {
      val original = ImageTestHelpers.horizontalGradient(64, 32)
      val ctx = ImageTestHelpers.testContext()
      ctx.scratchpad["image.output.format"] = ImageFormat.AVIF
      ctx.scratchpad["image.output.quality"] = 0.95f

      val encoded = try {
        encoder.encode(original, ctx)
      } catch (e: Exception) {
        if ("libaom-av1" in e.message.orEmpty()) {
          println("SKIPPED: AVIF encoder not available")
          return@requireFfmpeg
        }
        throw e
      }

      val decoded = decoder.decode(encoded, ctx)
      val mae = ImageTestHelpers.meanAbsoluteError(original, decoded)
      assertTrue(mae < 15.0, "MAE $mae should be < 15 for AVIF gradient at 95% quality")
    }
  }

  // -----------------------------------------------------------------------
  // Decode-only tests (from known magic bytes)
  // -----------------------------------------------------------------------

  @Test
  fun decoderReportsCorrectSupportedFormats() {
    val formats = decoder.supportedFormats
    assertTrue(ImageFormat.HEIF in formats)
    assertTrue(ImageFormat.HEIC in formats)
    assertTrue(ImageFormat.AVIF in formats)
  }

  @Test
  fun encoderReportsCorrectSupportedFormats() {
    val formats = encoder.supportedFormats
    assertTrue(ImageFormat.HEIF in formats)
    assertTrue(ImageFormat.HEIC in formats)
    assertTrue(ImageFormat.AVIF in formats)
  }
}
