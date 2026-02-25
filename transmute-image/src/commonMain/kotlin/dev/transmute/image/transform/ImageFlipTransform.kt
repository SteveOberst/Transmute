package dev.transmute.image.transform

import dev.transmute.common.PipelineContext
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.ImageIR
import dev.transmute.image.ImageHint
import dev.transmute.image.ImageTransform
import dev.transmute.codec.pipeline.TransformId

/**
 * Flips an [ImageIR] horizontally, vertically, or both.
 *
 * A pure pixel-shuffle with no interpolation - lossless and fast.
 * Supports any [PixelFormat][dev.transmute.image.PixelFormat] because it
 * operates on whole-pixel byte spans, not individual channels.
 *
 * @param horizontal Mirror left ↔ right.
 * @param vertical Mirror top ↔ bottom.
 */
class ImageFlipTransform(
  val horizontal: Boolean = false,
  val vertical: Boolean = false,
) : ImageTransform {

  override fun wouldTransform(hint: ImageHint): Boolean = horizontal || vertical

  override val id: TransformId = TransformId("image.flip")

  override suspend fun apply(ir: ImageIR, context: PipelineContext): ImageIR {
    if (!horizontal && !vertical) {
      context.logger.debug("ImageFlipTransform: no flip axis specified - skipping")
      return ir
    }

    val srcBuffer = ir.buffer as? ByteArrayPixelBuffer
      ?: error("ImageFlipTransform requires ByteArrayPixelBuffer, got ${ir.buffer::class.simpleName}")

    val label = buildString {
      if (horizontal) append("horizontal")
      if (horizontal && vertical) append("+")
      if (vertical) append("vertical")
    }
    context.logger.info("ImageFlipTransform: $label flip on ${ir.width}×${ir.height}")

    val bpp = ir.pixelFormat.bytesPerPixel
    val srcData = srcBuffer.data
    val w = ir.width
    val h = ir.height
    val stride = ir.stride
    val dstData = ByteArray(srcData.size)

    for (y in 0 until h) {
      for (x in 0 until w) {
        val dx = if (horizontal) w - 1 - x else x
        val dy = if (vertical) h - 1 - y else y

        val srcOff = y * stride + x * bpp
        val dstOff = dy * stride + dx * bpp
        srcData.copyInto(dstData, dstOff, srcOff, srcOff + bpp)
      }
    }

    return ir.copy(buffer = ByteArrayPixelBuffer(dstData))
  }
}
