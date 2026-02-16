package dev.transmute.image.transform

import dev.transmute.core.ConversionContext
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.ImageIR
import dev.transmute.image.PixelFormat
import dev.transmute.core.pipeline.Transform
import dev.transmute.core.pipeline.TransformId

/**
 * Adjusts the opacity (alpha channel) of an [ImageIR].
 *
 * If the source pixel format is [PixelFormat.RGB_888] (no alpha), the
 * image is first promoted to [PixelFormat.RGBA_8888] — the caller only
 * pays for the extra byte-per-pixel when opacity is actually needed.
 *
 * @param opacity Multiplier for the alpha channel (0.0 = fully transparent, 1.0 = unchanged).
 */
class ImageOpacityTransform(
  private val opacity: Float,
) : Transform<ImageIR> {

  override val id: TransformId = TransformId("image.opacity")

  override suspend fun apply(ir: ImageIR, context: ConversionContext): ImageIR {
    if (opacity == 1f) {
      context.logger.debug("ImageOpacityTransform: opacity=1.0 — skipping")
      return ir
    }

    val clampedOpacity = opacity.coerceIn(0f, 1f)
    context.logger.info("ImageOpacityTransform: setting opacity to $clampedOpacity")

    val srcBuffer = ir.buffer as? ByteArrayPixelBuffer
      ?: error("ImageOpacityTransform requires ByteArrayPixelBuffer, got ${ir.buffer::class.simpleName}")

    val srcData = srcBuffer.data
    val w = ir.width
    val h = ir.height

    return when (ir.pixelFormat) {
      PixelFormat.RGBA_8888 -> {
        // Multiply existing alpha by opacity factor.
        val dstData = srcData.copyOf()
        for (y in 0 until h) {
          for (x in 0 until w) {
            val alphaIdx = y * ir.stride + x * 4 + 3
            val a = dstData[alphaIdx].toInt() and 0xFF
            dstData[alphaIdx] = (a * clampedOpacity).toInt().coerceIn(0, 255).toByte()
          }
        }
        ir.copy(buffer = ByteArrayPixelBuffer(dstData))
      }

      PixelFormat.RGB_888 -> {
        // Promote to RGBA and set alpha to opacity × 255.
        val newAlpha = (clampedOpacity * 255f).toInt().coerceIn(0, 255).toByte()
        val dstStride = w * 4
        val dstData = ByteArray(h * dstStride)

        for (y in 0 until h) {
          for (x in 0 until w) {
            val srcOff = y * ir.stride + x * 3
            val dstOff = y * dstStride + x * 4
            dstData[dstOff] = srcData[srcOff]
            dstData[dstOff + 1] = srcData[srcOff + 1]
            dstData[dstOff + 2] = srcData[srcOff + 2]
            dstData[dstOff + 3] = newAlpha
          }
        }

        ir.copy(
          buffer = ByteArrayPixelBuffer(dstData),
          stride = dstStride,
          pixelFormat = PixelFormat.RGBA_8888,
          alphaSemantics = dev.transmute.image.AlphaSemantics.STRAIGHT,
        )
      }

      else -> {
        context.logger.warn("ImageOpacityTransform: unsupported pixel format ${ir.pixelFormat} — skipping")
        ir
      }
    }
  }
}
