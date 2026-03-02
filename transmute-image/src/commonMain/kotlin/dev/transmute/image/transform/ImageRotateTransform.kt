package dev.transmute.image.transform

import dev.transmute.common.PipelineContext
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.ImageIR
import dev.transmute.image.Orientation
import dev.transmute.image.ImageHint
import dev.transmute.image.ImageTransform
import dev.transmute.codec.pipeline.TransformId

/**
 * Rotates an [ImageIR] by an explicit number of degrees clockwise.
 *
 * Supported angles: 90, 180, 270.
 * The rotation is a pure pixel shuffle - no interpolation, no quality loss.
 * The [Orientation] field of the resulting IR is always reset to [Orientation.NORMAL].
 *
 * @param degrees Clockwise rotation angle; must be 90, 180, or 270. Default: 90.
 */
class ImageRotateTransform(val degrees: Int = 90) : ImageTransform {

  init {
    require(degrees in setOf(90, 180, 270)) {
      "ImageRotateTransform: degrees must be 90, 180, or 270, got $degrees"
    }
  }

  override fun wouldTransform(hint: ImageHint): Boolean = true

  override val id: TransformId = TransformId("image-rotate")

  override suspend fun apply(ir: ImageIR, context: PipelineContext): ImageIR {
    val srcBuffer = ir.buffer as? ByteArrayPixelBuffer
      ?: error("ImageRotateTransform requires ByteArrayPixelBuffer")

    context.logger.info("ImageRotateTransform: rotating ${degrees}° CW")

    val bpp = ir.pixelFormat.bytesPerPixel
    val srcData = srcBuffer.data
    val srcW = ir.width
    val srcH = ir.height
    val srcStride = ir.stride

    return when (degrees) {
      90 -> {
        // 90 deg CW: (x,y) -> (srcH-1-y, x). New dimensions: srcH x srcW.
        val dstW = srcH
        val dstH = srcW
        val dstStride = dstW * bpp
        val dstData = ByteArray(dstH * dstStride)

        for (y in 0 until srcH) {
          for (x in 0 until srcW) {
            val srcOff = y * srcStride + x * bpp
            val dx = srcH - 1 - y
            val dy = x
            val dstOff = dy * dstStride + dx * bpp
            srcData.copyInto(dstData, dstOff, srcOff, srcOff + bpp)
          }
        }

        ir.copy(
          buffer = ByteArrayPixelBuffer(dstData),
          width = dstW,
          height = dstH,
          stride = dstStride,
          orientation = Orientation.NORMAL,
        )
      }

      180 -> {
        // 180 deg: (x,y) -> (srcW-1-x, srcH-1-y). Same dimensions.
        val dstStride = srcW * bpp
        val dstData = ByteArray(srcH * dstStride)

        for (y in 0 until srcH) {
          for (x in 0 until srcW) {
            val srcOff = y * srcStride + x * bpp
            val dx = srcW - 1 - x
            val dy = srcH - 1 - y
            val dstOff = dy * dstStride + dx * bpp
            srcData.copyInto(dstData, dstOff, srcOff, srcOff + bpp)
          }
        }

        ir.copy(
          buffer = ByteArrayPixelBuffer(dstData),
          width = srcW,
          height = srcH,
          stride = dstStride,
          orientation = Orientation.NORMAL,
        )
      }

      else -> { // 270
        // 270 deg CW (= 90 deg CCW): (x,y) -> (y, srcW-1-x). New dimensions: srcH x srcW.
        val dstW = srcH
        val dstH = srcW
        val dstStride = dstW * bpp
        val dstData = ByteArray(dstH * dstStride)

        for (y in 0 until srcH) {
          for (x in 0 until srcW) {
            val srcOff = y * srcStride + x * bpp
            val dx = y
            val dy = srcW - 1 - x
            val dstOff = dy * dstStride + dx * bpp
            srcData.copyInto(dstData, dstOff, srcOff, srcOff + bpp)
          }
        }

        ir.copy(
          buffer = ByteArrayPixelBuffer(dstData),
          width = dstW,
          height = dstH,
          stride = dstStride,
          orientation = Orientation.NORMAL,
        )
      }

    }
  }
}
