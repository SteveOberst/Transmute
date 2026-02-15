package dev.transmute.image.codecs.jvm

import dev.transmute.core.ConversionContext
import dev.transmute.core.ImageFormat
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.ImageEncoder
import dev.transmute.image.ImageIR
import dev.transmute.image.PixelFormat
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Lossless PNG encoder for JVM.
 *
 * Preserves alpha channel from RGBA_8888 sources. Useful for testing
 * (lossless round-trips) and for images where transparency matters.
 */
class JvmPngEncoder : ImageEncoder {

  override val supportedFormats: Set<ImageFormat> = setOf(ImageFormat.PNG)

  override suspend fun encode(ir: ImageIR, context: ConversionContext): ByteArray {
    context.logger.debug("JvmPngEncoder: encoding ${ir.width}×${ir.height} as PNG")

    val srcBuffer = ir.buffer as? ByteArrayPixelBuffer
      ?: error("JvmPngEncoder requires ByteArrayPixelBuffer")

    val bufferedImage = when (ir.pixelFormat) {
      PixelFormat.RGBA_8888 -> JvmImageEncoder.toBufferedImageWithAlpha(
        srcBuffer.data, ir.width, ir.height, ir.stride,
      )
      PixelFormat.RGB_888 -> JvmImageEncoder.toBufferedImage(
        srcBuffer.data, ir.width, ir.height, ir.stride, ir.pixelFormat,
      )
      else -> error("JvmPngEncoder does not support pixel format: ${ir.pixelFormat}")
    }

    val output = ByteArrayOutputStream()
    ImageIO.write(bufferedImage, "PNG", output)
    val result = output.toByteArray()
    context.logger.debug("JvmPngEncoder: produced ${result.size} bytes")
    return result
  }
}
