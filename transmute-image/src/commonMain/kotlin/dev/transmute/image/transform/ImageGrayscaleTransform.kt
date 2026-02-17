package dev.transmute.image.transform

import dev.transmute.core.ConversionContext
import dev.transmute.image.AlphaSemantics
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.ImageIR
import dev.transmute.image.PixelFormat
import dev.transmute.core.pipeline.Transform
import dev.transmute.core.pipeline.TransformId

/**
 * Converts an [ImageIR] to grayscale using ITU-R BT.709 luma coefficients.
 *
 * BT.709 weights (0.2126 R, 0.7152 G, 0.0722 B) match how the human eye
 * perceives brightness - green contributes most, blue least. This produces
 * more natural-looking grayscale than a simple channel average.
 *
 * Supports [PixelFormat.RGBA_8888] and [PixelFormat.RGB_888].
 * Alpha channel is preserved when present.
 */
class ImageGrayscaleTransform : Transform<ImageIR> {

  override val id: TransformId = TransformId("image.grayscale")

  override suspend fun apply(ir: ImageIR, context: ConversionContext): ImageIR {
    val srcBuffer = ir.buffer as? ByteArrayPixelBuffer
      ?: error("ImageGrayscaleTransform requires ByteArrayPixelBuffer, got ${ir.buffer::class.simpleName}")

    context.logger.info("ImageGrayscaleTransform: converting ${ir.width}×${ir.height} to grayscale")

    val bpp = ir.pixelFormat.bytesPerPixel
    val srcData = srcBuffer.data
    val dstData = srcData.copyOf()

    val hasAlpha = ir.pixelFormat == PixelFormat.RGBA_8888

    for (y in 0 until ir.height) {
      for (x in 0 until ir.width) {
        val offset = y * ir.stride + x * bpp

        val r = dstData[offset].toInt() and 0xFF
        val g = dstData[offset + 1].toInt() and 0xFF
        val b = dstData[offset + 2].toInt() and 0xFF

        // BT.709 luma coefficients - perceptually accurate grayscale.
        val luma = (0.2126f * r + 0.7152f * g + 0.0722f * b).toInt().coerceIn(0, 255)

        dstData[offset] = luma.toByte()
        dstData[offset + 1] = luma.toByte()
        dstData[offset + 2] = luma.toByte()
        // Alpha (index 3) is untouched when present.
      }
    }

    return ir.copy(buffer = ByteArrayPixelBuffer(dstData))
  }
}
