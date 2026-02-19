package dev.transmute.image.transform

import dev.transmute.core.ConversionContext
import dev.transmute.core.pipeline.TransformId
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.ImageHint
import dev.transmute.image.ImageIR
import dev.transmute.image.ImageTransform
import dev.transmute.image.transform.kernel.ResampleFactory
import dev.transmute.image.transform.kernel.ResampleFilter
import dev.transmute.image.transform.kernel.ResampleKernel
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Resizes an [ImageIR] to exact [targetWidth]×[targetHeight] dimensions
 * using a configurable [ResampleFilter].
 *
 * The resize is performed as a two-pass **separable convolution** - first
 * horizontal, then vertical - which is both fast (O(n·k) per axis instead
 * of O(n·k²) for a 2-D kernel) and mathematically equivalent to the full
 * 2-D filter for all symmetric kernels.
 *
 * When downscaling, the kernel window is automatically widened by the
 * scale ratio so that the filter acts as a proper anti-aliasing
 * (low-pass) pre-filter.
 *
 * Supports [PixelFormat.RGBA_8888][dev.transmute.image.PixelFormat.RGBA_8888]
 * and [PixelFormat.RGB_888][dev.transmute.image.PixelFormat.RGB_888].
 *
 * @param targetWidth   Desired output width in pixels.
 * @param targetHeight  Desired output height in pixels.
 * @param filter        Resampling filter to use (default: Mitchell–Netravali bicubic).
 * @param allowUpscale  When `false`, images already smaller than the target are returned unchanged.
 */
class ImageResizeTransform(
  val targetWidth: Int,
  val targetHeight: Int,
  val filter: ResampleFilter = ResampleFilter.BICUBIC_MITCHELL,
  val allowUpscale: Boolean = true,
) : ImageTransform {

  override fun wouldTransform(hint: ImageHint): Boolean =
    hint.width == null || hint.height == null ||
      hint.width != targetWidth || hint.height != targetHeight

  override val id: TransformId = TransformId("image.resize")

  private val kernel: ResampleKernel = ResampleFactory.kernelFor(filter)

  override suspend fun apply(ir: ImageIR, context: ConversionContext): ImageIR {
    val srcW = ir.width
    val srcH = ir.height

    // Already at target size - nothing to do.
    if (srcW == targetWidth && srcH == targetHeight) {
      context.logger.debug("ImageResizeTransform: already ${srcW}×${srcH} - skipping")
      return ir
    }

    // Upscale guard.
    if (!allowUpscale && srcW <= targetWidth && srcH <= targetHeight) {
      context.logger.debug(
        "ImageResizeTransform: ${srcW}×${srcH} smaller than ${targetWidth}×${targetHeight} " +
          "and upscale disabled - skipping"
      )
      return ir
    }

    context.logger.info(
      "ImageResizeTransform: ${srcW}×${srcH} → ${targetWidth}×${targetHeight} (${filter.name})"
    )

    val srcBuffer = ir.buffer as? ByteArrayPixelBuffer
      ?: error("ImageResizeTransform requires ByteArrayPixelBuffer, got ${ir.buffer::class.simpleName}")

    val bpp = ir.pixelFormat.bytesPerPixel
    val srcData = srcBuffer.data
    val srcStride = ir.stride

    // --- Pass 1: horizontal (srcW → targetWidth, height stays srcH) ---
    val tmpStride = targetWidth * bpp
    val tmpData = ByteArray(srcH * tmpStride)
    resample1D(
      src = srcData, dst = tmpData,
      srcLen = srcW, dstLen = targetWidth, lines = srcH,
      srcStride = srcStride, dstStride = tmpStride,
      bpp = bpp, horizontal = true, kernel = kernel,
    )

    // --- Pass 2: vertical (srcH → targetHeight, width stays targetWidth) ---
    val dstStride = targetWidth * bpp
    val dstData = ByteArray(targetHeight * dstStride)
    resample1D(
      src = tmpData, dst = dstData,
      srcLen = srcH, dstLen = targetHeight, lines = targetWidth,
      srcStride = tmpStride, dstStride = dstStride,
      bpp = bpp, horizontal = false, kernel = kernel,
    )

    return ir.copy(
      buffer = ByteArrayPixelBuffer(dstData),
      width = targetWidth,
      height = targetHeight,
      stride = dstStride,
    )
  }

  companion object {

    /**
     * Resamples one axis of a pixel buffer using the given [kernel].
     *
     * @param src           Source pixel data.
     * @param dst           Destination pixel data (pre-allocated).
     * @param srcLen        Source length along the resampled axis (pixels).
     * @param dstLen        Destination length along the resampled axis (pixels).
     * @param lines         Number of independent scan-lines (rows for horizontal, columns for vertical).
     * @param srcStride     Byte stride of the source buffer.
     * @param dstStride     Byte stride of the destination buffer.
     * @param bpp           Bytes per pixel (3 for RGB, 4 for RGBA).
     * @param horizontal    `true` = resample along x; `false` = resample along y.
     * @param kernel        The 1-D resample kernel to evaluate.
     */
    internal fun resample1D(
      src: ByteArray,
      dst: ByteArray,
      srcLen: Int,
      dstLen: Int,
      lines: Int,
      srcStride: Int,
      dstStride: Int,
      bpp: Int,
      horizontal: Boolean,
      kernel: ResampleKernel,
    ) {
      val ratio = srcLen.toDouble() / dstLen

      // When downscaling, widen the kernel window proportionally for anti-aliasing.
      val filterScale = if (ratio > 1.0) ratio else 1.0
      val scaledSupport = kernel.support * filterScale

      for (line in 0 until lines) {
        for (dp in 0 until dstLen) {
          // Map destination pixel centre to source coordinate space.
          val center = (dp + 0.5) * ratio - 0.5

          val windowStart = ceil(center - scaledSupport).toInt().coerceAtLeast(0)
          val windowEnd = floor(center + scaledSupport).toInt().coerceAtMost(srcLen - 1)

          val sums = DoubleArray(bpp)
          var wTotal = 0.0

          for (sp in windowStart..windowEnd) {
            val dist = ((sp.toDouble() - center) / filterScale).toFloat()
            val w = kernel.weight(dist)
            if (w == 0f) continue
            wTotal += w

            val srcOff = if (horizontal) {
              line * srcStride + sp * bpp
            } else {
              sp * srcStride + line * bpp
            }
            for (c in 0 until bpp) {
              sums[c] += w * (src[srcOff + c].toInt() and 0xFF)
            }
          }

          val dstOff = if (horizontal) {
            line * dstStride + dp * bpp
          } else {
            dp * dstStride + line * bpp
          }

          if (wTotal > 0.0) {
            for (c in 0 until bpp) {
              dst[dstOff + c] = (sums[c] / wTotal).roundToInt().coerceIn(0, 255).toByte()
            }
          }
        }
      }
    }
  }
}