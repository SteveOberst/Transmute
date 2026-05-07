package dev.transmute.libheif

import dev.transmute.common.PipelineContext
import dev.transmute.common.PrintLogger
import dev.transmute.image.AlphaSemantics
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.ColorInfo
import dev.transmute.image.ImageIR
import dev.transmute.image.PixelFormat

/**
 * Test utilities for libheif integration tests.
 *
 * Provides a synthetic image generator and a test [PipelineContext] used by
 * the integration test suite. Self-contained -- does not depend on test
 * helpers from other modules (which aren't on the test classpath).
 *
 * libheif availability is gated at the Gradle level: the `desktopTest` task
 * is configured with `onlyIf` so it only runs when a working libheif
 * installation is detected (or `TRANSMUTE_LIBHEIF_TESTS=on` is set).
 */
internal object LibHeifTestHelpers {

  /**
   * Creates a test [PipelineContext] with print logging.
   */
  fun testContext(): PipelineContext = PipelineContext(logger = PrintLogger)

  // -- Synthetic image ---

  /**
   * Create a solid-colour RGBA_8888 [ImageIR].
   */
  fun solidColor(width: Int, height: Int, r: Int, g: Int, b: Int, a: Int = 255): ImageIR {
    val bpp = 4
    val stride = width * bpp
    val pixels = ByteArray(height * stride)
    for (y in 0 until height) {
      for (x in 0 until width) {
        val off = y * stride + x * bpp
        pixels[off] = r.toByte()
        pixels[off + 1] = g.toByte()
        pixels[off + 2] = b.toByte()
        pixels[off + 3] = a.toByte()
      }
    }
    return ImageIR(
      buffer = ByteArrayPixelBuffer(pixels),
      width = width,
      height = height,
      stride = stride,
      pixelFormat = PixelFormat.RGBA_8888,
      alphaSemantics = if (a < 255) AlphaSemantics.STRAIGHT else AlphaSemantics.OPAQUE,
      colorInfo = ColorInfo(),
    )
  }
}
