package dev.transmute.image.transform

import dev.transmute.core.ConversionContext
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.ImageIR
import dev.transmute.image.PixelFormat
import dev.transmute.core.pipeline.Transform
import dev.transmute.core.pipeline.TransformId

/**
 * Adjusts brightness and contrast of an [ImageIR].
 *
 * Both adjustments are applied in a single pass to minimise rounding error:
 *
 *     output = clamp((input - 128) × contrast + 128 + brightness, 0, 255)
 *
 * This formula centres contrast scaling around mid-grey (128) so that
 * contrast increases expand away from middle tones rather than zero,
 * which matches what users expect from photo editors.
 *
 * @param brightness Offset added to each channel (−255 .. +255). 0 = no change.
 * @param contrast   Multiplier applied around mid-grey (0.0 .. 3.0). 1.0 = no change.
 */
class ImageBrightnessContrastTransform(
  private val brightness: Float = 0f,
  private val contrast: Float = 1f,
) : Transform<ImageIR> {

  override val id: TransformId = TransformId("image.brightness-contrast")

  override suspend fun apply(ir: ImageIR, context: ConversionContext): ImageIR {
    if (brightness == 0f && contrast == 1f) {
      context.logger.debug("ImageBrightnessContrastTransform: no adjustment — skipping")
      return ir
    }

    val srcBuffer = ir.buffer as? ByteArrayPixelBuffer
      ?: error("ImageBrightnessContrastTransform requires ByteArrayPixelBuffer, got ${ir.buffer::class.simpleName}")

    context.logger.info("ImageBrightnessContrastTransform: brightness=$brightness, contrast=$contrast")

    val bpp = ir.pixelFormat.bytesPerPixel
    val srcData = srcBuffer.data
    val dstData = srcData.copyOf()
    val hasAlpha = ir.pixelFormat == PixelFormat.RGBA_8888
    // Number of colour channels to adjust (skip alpha).
    val channels = if (hasAlpha) 3 else bpp

    for (y in 0 until ir.height) {
      for (x in 0 until ir.width) {
        val offset = y * ir.stride + x * bpp
        for (c in 0 until channels) {
          val v = dstData[offset + c].toInt() and 0xFF
          val adjusted = ((v - 128) * contrast + 128 + brightness).toInt().coerceIn(0, 255)
          dstData[offset + c] = adjusted.toByte()
        }
      }
    }

    return ir.copy(buffer = ByteArrayPixelBuffer(dstData))
  }
}
