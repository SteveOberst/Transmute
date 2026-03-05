package dev.transmute.image

import dev.transmute.common.PipelineContext
import dev.transmute.common.PrintLogger
import kotlin.math.abs

/**
 * Shared test utilities for the image conversion module.
 *
 * Provides synthetic image generation, pixel comparison,
 * and a common [PipelineContext] for tests.
 */
object ImageTestHelpers {

  // --- PipelineContext ---

  /** Creates a minimal PipelineContext suitable for unit tests. */
  fun testContext() = PipelineContext(logger = PrintLogger)

  // --- Synthetic image creation ---

  /**
   * Creates a solid-color RGBA_8888 image.
   *
   * @return An [ImageIR] filled with the specified color, useful for
   * verifying that encoders/decoders preserve uniform regions.
   */
  fun solidColor(width: Int, height: Int, r: Int, g: Int, b: Int, a: Int = 255): ImageIR {
    val bpp = 4
    val stride = width * bpp
    val data = ByteArray(height * stride)
    for (y in 0 until height) {
      for (x in 0 until width) {
        val offset = y * stride + x * bpp
        data[offset] = r.toByte()
        data[offset + 1] = g.toByte()
        data[offset + 2] = b.toByte()
        data[offset + 3] = a.toByte()
      }
    }
    return ImageIR(
      buffer = ByteArrayPixelBuffer(data),
      width = width,
      height = height,
      stride = stride,
      pixelFormat = PixelFormat.RGBA_8888,
      alphaSemantics = if (a < 255) AlphaSemantics.STRAIGHT else AlphaSemantics.OPAQUE,
      colorInfo = ColorInfo(),
    )
  }

  /**
   * Creates a horizontal gradient from [startR,startG,startB] (left)
   * to [endR,endG,endB] (right).
   *
   * Useful for verifying that lossy codecs don't introduce banding or
   * major color shifts, and that scaling preserves gradients smoothly.
   */
  fun horizontalGradient(
    width: Int,
    height: Int,
    startR: Int = 0,
    startG: Int = 0,
    startB: Int = 0,
    endR: Int = 255,
    endG: Int = 255,
    endB: Int = 255,
  ): ImageIR {
    val bpp = 4
    val stride = width * bpp
    val data = ByteArray(height * stride)
    for (y in 0 until height) {
      for (x in 0 until width) {
        val t = if (width > 1) x.toFloat() / (width - 1) else 0f
        val offset = y * stride + x * bpp
        data[offset] = lerp(startR, endR, t).toByte()
        data[offset + 1] = lerp(startG, endG, t).toByte()
        data[offset + 2] = lerp(startB, endB, t).toByte()
        data[offset + 3] = 0xFF.toByte()
      }
    }
    return ImageIR(
      buffer = ByteArrayPixelBuffer(data),
      width = width,
      height = height,
      stride = stride,
      pixelFormat = PixelFormat.RGBA_8888,
      alphaSemantics = AlphaSemantics.OPAQUE,
      colorInfo = ColorInfo(),
    )
  }

  /**
   * Creates a checkerboard pattern - alternating [colorA] / [colorB] blocks.
   *
   * Useful for verifying that scaling doesn't smear boundaries and
   * that codecs handle high-frequency detail.
   *
   * @param blockSize Size of each checker square in pixels.
   */
  fun checkerboard(
    width: Int,
    height: Int,
    blockSize: Int = 8,
    colorA: IntArray = intArrayOf(255, 255, 255, 255),
    colorB: IntArray = intArrayOf(0, 0, 0, 255),
  ): ImageIR {
    require(colorA.size == 4 && colorB.size == 4) { "Colors must be RGBA (4 elements)" }
    val bpp = 4
    val stride = width * bpp
    val data = ByteArray(height * stride)
    for (y in 0 until height) {
      for (x in 0 until width) {
        val isA = ((x / blockSize) + (y / blockSize)) % 2 == 0
        val c = if (isA) colorA else colorB
        val offset = y * stride + x * bpp
        data[offset] = c[0].toByte()
        data[offset + 1] = c[1].toByte()
        data[offset + 2] = c[2].toByte()
        data[offset + 3] = c[3].toByte()
      }
    }
    return ImageIR(
      buffer = ByteArrayPixelBuffer(data),
      width = width,
      height = height,
      stride = stride,
      pixelFormat = PixelFormat.RGBA_8888,
      alphaSemantics = AlphaSemantics.OPAQUE,
      colorInfo = ColorInfo(),
    )
  }

  // --- Pixel comparison ---

  /**
   * Returns the pixel value at (x, y) as an [IntArray] of [bpp] channels (0-255).
   */
  fun pixelAt(ir: ImageIR, x: Int, y: Int): IntArray {
    val buffer = ir.buffer as ByteArrayPixelBuffer
    val bpp = ir.pixelFormat.bytesPerPixel
    val offset = y * ir.stride + x * bpp
    return IntArray(bpp) { buffer.data[offset + it].toInt() and 0xFF }
  }

  /**
   * Computes the peak per-channel difference between two images.
   *
   * Both images must have the same dimensions and pixel format.
   * Returns the maximum absolute difference across all pixels and channels.
   * A return value of 0 means the images are identical.
   *
   * For lossy codec testing, a tolerance of 3-10 is typical for JPEG.
   */
  fun peakDifference(a: ImageIR, b: ImageIR): Int {
    require(a.width == b.width && a.height == b.height) {
      "Dimension mismatch: ${a.width}x${a.height} vs ${b.width}x${b.height}"
    }
    require(a.pixelFormat == b.pixelFormat) {
      "Pixel format mismatch: ${a.pixelFormat} vs ${b.pixelFormat}"
    }
    val aBuf = (a.buffer as ByteArrayPixelBuffer).data
    val bBuf = (b.buffer as ByteArrayPixelBuffer).data
    val bpp = a.pixelFormat.bytesPerPixel
    var maxDiff = 0
    for (y in 0 until a.height) {
      for (x in 0 until a.width) {
        val aOff = y * a.stride + x * bpp
        val bOff = y * b.stride + x * bpp
        for (c in 0 until bpp) {
          val av = aBuf[aOff + c].toInt() and 0xFF
          val bv = bBuf[bOff + c].toInt() and 0xFF
          val diff = abs(av - bv)
          if (diff > maxDiff) maxDiff = diff
        }
      }
    }
    return maxDiff
  }

  /**
   * Computes the mean absolute error (MAE) across all pixels and channels.
   *
   * Useful for overall quality assessment. For JPEG at quality=85,
   * MAE should typically be < 5 on smooth images.
   */
  fun meanAbsoluteError(a: ImageIR, b: ImageIR): Double {
    require(a.width == b.width && a.height == b.height)
    require(a.pixelFormat == b.pixelFormat)
    val aBuf = (a.buffer as ByteArrayPixelBuffer).data
    val bBuf = (b.buffer as ByteArrayPixelBuffer).data
    val bpp = a.pixelFormat.bytesPerPixel
    var totalDiff = 0L
    var count = 0L
    for (y in 0 until a.height) {
      for (x in 0 until a.width) {
        val aOff = y * a.stride + x * bpp
        val bOff = y * b.stride + x * bpp
        for (c in 0 until bpp) {
          val av = aBuf[aOff + c].toInt() and 0xFF
          val bv = bBuf[bOff + c].toInt() and 0xFF
          totalDiff += abs(av - bv)
          count++
        }
      }
    }
    return if (count > 0) totalDiff.toDouble() / count else 0.0
  }

  // --- Alpha normalization ---

  /**
   * Replaces every alpha byte with 0xFF so that pixel comparisons are not
   * affected by codecs that discard alpha (e.g. JPEG).
   */
  fun adjustAlphaForComparison(ir: ImageIR): ImageIR {
    if (ir.pixelFormat != PixelFormat.RGBA_8888) return ir
    val buf = (ir.buffer as ByteArrayPixelBuffer).data.copyOf()
    val bpp = ir.pixelFormat.bytesPerPixel
    for (i in buf.indices) {
      if (i % bpp == bpp - 1) buf[i] = 0xFF.toByte()
    }
    return ir.copy(buffer = ByteArrayPixelBuffer(buf))
  }

  private fun lerp(start: Int, end: Int, t: Float): Int = (start + (end - start) * t).toInt().coerceIn(0, 255)
}
