package dev.transmute.image.transform

import dev.transmute.common.PipelineContext
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.ImageIR
import dev.transmute.image.PixelFormat
import dev.transmute.image.ImageHint
import dev.transmute.image.ImageTransform
import dev.transmute.codec.pipeline.TransformId

/**
 * Applies a box blur to an [ImageIR].
 *
 * Box blur is separable - we run a 1-D horizontal pass then a 1-D
 * vertical pass, each O(width×height) regardless of [radius]. This
 * keeps the total cost O(N) instead of the O(N×R²) of a naive 2-D kernel.
 *
 * Box blur produces slightly blockier results than Gaussian, but it is
 * much simpler and faster in pure Kotlin where we don't have SIMD.
 * Stacking two passes at the same radius approximates a tent filter;
 * three passes approximates Gaussian.
 *
 * @param radius Blur radius in pixels. 1 = 3×3 kernel, 2 = 5×5, etc.
 */
class ImageBlurTransform(
  val radius: Int = 1,
) : ImageTransform {

  override fun wouldTransform(hint: ImageHint): Boolean = radius > 0

  override val id: TransformId = TransformId("image.blur")

  override suspend fun apply(ir: ImageIR, context: PipelineContext): ImageIR {
    if (radius <= 0) {
      context.logger.debug("ImageBlurTransform: radius ≤ 0 - skipping")
      return ir
    }

    val srcBuffer = ir.buffer as? ByteArrayPixelBuffer
      ?: error("ImageBlurTransform requires ByteArrayPixelBuffer, got ${ir.buffer::class.simpleName}")

    context.logger.info("ImageBlurTransform: radius=$radius on ${ir.width}×${ir.height}")

    val bpp = ir.pixelFormat.bytesPerPixel
    val w = ir.width
    val h = ir.height
    val stride = ir.stride
    val hasAlpha = ir.pixelFormat == PixelFormat.RGBA_8888
    val channels = if (hasAlpha) 3 else bpp

    // Horizontal pass
    val hData = horizontalPass(srcBuffer.data, w, h, stride, bpp, channels, radius)
    // Vertical pass on top of horizontal
    val vData = verticalPass(hData, w, h, stride, bpp, channels, radius)

    return ir.copy(buffer = ByteArrayPixelBuffer(vData))
  }

  companion object {

    /**
     * Sliding-window horizontal blur. For each row, maintains a running
     * sum per channel so each pixel costs O(1) additions.
     */
    internal fun horizontalPass(
      src: ByteArray, w: Int, h: Int, stride: Int, bpp: Int, channels: Int, r: Int,
    ): ByteArray {
      val dst = src.copyOf()
      val span = 2 * r + 1

      for (y in 0 until h) {
        val rowBase = y * stride
        for (c in 0 until channels) {
          // Seed the accumulator with the first window.
          var sum = 0
          for (k in -r..r) {
            val sx = k.coerceIn(0, w - 1)
            sum += src[rowBase + sx * bpp + c].toInt() and 0xFF
          }
          dst[rowBase + c] = (sum / span).toByte()

          // Slide right, subtracting the pixel that leaves and adding the one that enters.
          for (x in 1 until w) {
            val dropX = (x - r - 1).coerceIn(0, w - 1)
            val addX = (x + r).coerceIn(0, w - 1)
            sum -= src[rowBase + dropX * bpp + c].toInt() and 0xFF
            sum += src[rowBase + addX * bpp + c].toInt() and 0xFF
            dst[rowBase + x * bpp + c] = (sum / span).toByte()
          }
        }

        // Copy alpha channel unchanged when present.
        if (channels < bpp) {
          for (x in 0 until w) {
            val off = rowBase + x * bpp
            for (c in channels until bpp) {
              dst[off + c] = src[off + c]
            }
          }
        }
      }
      return dst
    }

    /**
     * Sliding-window vertical blur - identical strategy, transposed.
     */
    internal fun verticalPass(
      src: ByteArray, w: Int, h: Int, stride: Int, bpp: Int, channels: Int, r: Int,
    ): ByteArray {
      val dst = src.copyOf()
      val span = 2 * r + 1

      for (x in 0 until w) {
        val colBase = x * bpp
        for (c in 0 until channels) {
          var sum = 0
          for (k in -r..r) {
            val sy = k.coerceIn(0, h - 1)
            sum += src[sy * stride + colBase + c].toInt() and 0xFF
          }
          dst[colBase + c] = (sum / span).toByte()

          for (y in 1 until h) {
            val dropY = (y - r - 1).coerceIn(0, h - 1)
            val addY = (y + r).coerceIn(0, h - 1)
            sum -= src[dropY * stride + colBase + c].toInt() and 0xFF
            sum += src[addY * stride + colBase + c].toInt() and 0xFF
            dst[y * stride + colBase + c] = (sum / span).toByte()
          }
        }
      }
      return dst
    }
  }
}
