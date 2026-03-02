package dev.transmute.image.transform

import dev.transmute.common.PipelineContext
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.ImageHint
import dev.transmute.image.ImageIR
import dev.transmute.image.ImageTransform
import dev.transmute.image.PixelFormat
import dev.transmute.codec.pipeline.TransformId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Scales an [ImageIR] to fit within [maxWidth]x[maxHeight] while
 * preserving the original aspect ratio.
 *
 * Uses bilinear interpolation for quality downscaling.
 * Only supports [PixelFormat.RGBA_8888] and [PixelFormat.RGB_888].
 *
 * If the image already fits within the target bounds, it is returned
 * unchanged (no upscaling).
 */
class ImageScaleTransform(
  val maxWidth: Int,
  val maxHeight: Int,
) : ImageTransform {

  override fun wouldTransform(hint: ImageHint): Boolean =
    hint.width == null || hint.height == null ||
      hint.width > maxWidth || hint.height > maxHeight

  override val id: TransformId = TransformId("image-scale")

  override suspend fun apply(ir: ImageIR, context: PipelineContext): ImageIR {
    // Don't upscale - only downscale.
    if (ir.width <= maxWidth && ir.height <= maxHeight) {
      context.logger.debug("ImageScaleTransform: image ${ir.width}×${ir.height} already fits within $maxWidth×$maxHeight - skipping")
      return ir
    }

    val (targetW, targetH) = fitDimensions(ir.width, ir.height, maxWidth, maxHeight)
    context.logger.info("ImageScaleTransform: ${ir.width}×${ir.height} → ${targetW}×${targetH}")

    val srcBuffer = ir.buffer as? ByteArrayPixelBuffer
      ?: error("ImageScaleTransform requires ByteArrayPixelBuffer, got ${ir.buffer::class.simpleName}")

    val bpp = ir.pixelFormat.bytesPerPixel
    val srcData = srcBuffer.data
    val srcStride = ir.stride

    val dstStride = targetW * bpp
    val dstData = ByteArray(targetH * dstStride)

    bilinearScale(
      srcData = srcData,
      srcWidth = ir.width,
      srcHeight = ir.height,
      srcStride = srcStride,
      dstData = dstData,
      dstWidth = targetW,
      dstHeight = targetH,
      dstStride = dstStride,
      bpp = bpp,
    )

    return ir.copy(
      buffer = ByteArrayPixelBuffer(dstData),
      width = targetW,
      height = targetH,
      stride = dstStride,
    )
  }

  companion object {

    /**
     * Computes the largest dimensions that fit within [maxW]x[maxH]
     * while preserving the aspect ratio of [srcW]x[srcH].
     */
    fun fitDimensions(srcW: Int, srcH: Int, maxW: Int, maxH: Int): Pair<Int, Int> {
      val scaleX = maxW.toDouble() / srcW
      val scaleY = maxH.toDouble() / srcH
      val scale = min(scaleX, scaleY)
      val w = max(1, (srcW * scale).roundToInt())
      val h = max(1, (srcH * scale).roundToInt())
      return w to h
    }

    /**
     * Bilinear interpolation scaler operating on raw pixel bytes.
     *
     * For each destination pixel, computes the fractional source coordinate,
     * samples the four nearest source pixels, and blends by sub-pixel weights.
     */
    internal fun bilinearScale(
      srcData: ByteArray,
      srcWidth: Int,
      srcHeight: Int,
      srcStride: Int,
      dstData: ByteArray,
      dstWidth: Int,
      dstHeight: Int,
      dstStride: Int,
      bpp: Int,
    ) {
      val xRatio = if (dstWidth > 1) (srcWidth - 1).toDouble() / (dstWidth - 1) else 0.0
      val yRatio = if (dstHeight > 1) (srcHeight - 1).toDouble() / (dstHeight - 1) else 0.0

      for (dy in 0 until dstHeight) {
        val srcY = dy * yRatio
        val y0 = srcY.toInt()
        val y1 = min(y0 + 1, srcHeight - 1)
        val yFrac = srcY - y0

        for (dx in 0 until dstWidth) {
          val srcX = dx * xRatio
          val x0 = srcX.toInt()
          val x1 = min(x0 + 1, srcWidth - 1)
          val xFrac = srcX - x0

          val dstOffset = dy * dstStride + dx * bpp

          for (c in 0 until bpp) {
            // Sample four neighbours
            val topLeft = srcData[y0 * srcStride + x0 * bpp + c].toInt() and 0xFF
            val topRight = srcData[y0 * srcStride + x1 * bpp + c].toInt() and 0xFF
            val botLeft = srcData[y1 * srcStride + x0 * bpp + c].toInt() and 0xFF
            val botRight = srcData[y1 * srcStride + x1 * bpp + c].toInt() and 0xFF

            // Bilinear blend
            val top = topLeft + (topRight - topLeft) * xFrac
            val bot = botLeft + (botRight - botLeft) * xFrac
            val value = top + (bot - top) * yFrac

            dstData[dstOffset + c] = value.roundToInt().coerceIn(0, 255).toByte()
          }
        }
      }
    }
  }
}
