package dev.transmute.image.transform

import dev.transmute.common.PipelineContext
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.ImageIR
import dev.transmute.image.ImageHint
import dev.transmute.image.ImageTransform
import dev.transmute.codec.pipeline.TransformId

/**
 * Crops an [ImageIR] to a sub-region defined by [x], [y], [cropWidth], [cropHeight].
 *
 * The crop rectangle is clamped to image bounds - requesting a region that
 * extends beyond the edge simply truncates at the edge.
 *
 * Operates on [ByteArrayPixelBuffer] with any [PixelFormat].
 */
class ImageCropTransform(
  val x: Int,
  val y: Int,
  val cropWidth: Int,
  val cropHeight: Int,
) : ImageTransform {

  override fun wouldTransform(hint: ImageHint): Boolean = true // always crops

  override val id: TransformId = TransformId("image-crop")

  override suspend fun apply(ir: ImageIR, context: PipelineContext): ImageIR {
    val srcBuffer = ir.buffer as? ByteArrayPixelBuffer
      ?: error("ImageCropTransform requires ByteArrayPixelBuffer")

    // Clamp crop rect to image bounds.
    val cx = x.coerceIn(0, ir.width)
    val cy = y.coerceIn(0, ir.height)
    val cw = cropWidth.coerceIn(0, ir.width - cx)
    val ch = cropHeight.coerceIn(0, ir.height - cy)

    if (cw == 0 || ch == 0) {
      context.logger.warn("ImageCropTransform: crop region is empty after clamping - returning original")
      return ir
    }

    if (cx == 0 && cy == 0 && cw == ir.width && ch == ir.height) {
      context.logger.debug("ImageCropTransform: crop region equals full image - skipping")
      return ir
    }

    context.logger.info("ImageCropTransform: cropping to ($cx,$cy) ${cw}×${ch} from ${ir.width}×${ir.height}")

    val bpp = ir.pixelFormat.bytesPerPixel
    val srcData = srcBuffer.data
    val srcStride = ir.stride
    val dstStride = cw * bpp
    val dstData = ByteArray(ch * dstStride)

    for (row in 0 until ch) {
      val srcOffset = (cy + row) * srcStride + cx * bpp
      val dstOffset = row * dstStride
      srcData.copyInto(dstData, dstOffset, srcOffset, srcOffset + dstStride)
    }

    return ir.copy(
      buffer = ByteArrayPixelBuffer(dstData),
      width = cw,
      height = ch,
      stride = dstStride,
    )
  }
}
