package dev.transmute.image.codecs.jvm

import dev.transmute.core.ConversionContext
import dev.transmute.core.ImageFormat
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.ImageEncoder
import dev.transmute.image.ImageIR
import dev.transmute.image.PixelFormat
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * JVM image encoder backed by [ImageIO].
 *
 * Converts an [ImageIR] (RGBA_8888 or RGB_888) into the specified output
 * format. For JPEG, the [quality] parameter (0.0–1.0) controls compression.
 *
 * @param format The target [ImageFormat] — e.g. [ImageFormat.JPEG], [ImageFormat.PNG], [ImageFormat.BMP].
 * @param quality JPEG quality factor, 0.0 (worst) to 1.0 (best). Ignored for lossless formats.
 */
class JvmImageEncoder(
  private val format: ImageFormat = ImageFormat.JPEG,
  private val quality: Float = 0.85f,
) : ImageEncoder {

  /** Maps our enum to the ImageIO format name. */
  private val imageIoFormatName: String = when (format) {
    ImageFormat.JPEG -> "JPEG"
    ImageFormat.PNG -> "PNG"
    ImageFormat.BMP -> "BMP"
    ImageFormat.GIF -> "GIF"
    else -> format.extension.uppercase()
  }

  override val supportedFormats: Set<ImageFormat> = setOf(format)

  override suspend fun encode(ir: ImageIR, context: ConversionContext): ByteArray {
    context.logger.debug("JvmImageEncoder: encoding ${ir.width}×${ir.height} as $format (quality=$quality)")

    val srcBuffer = ir.buffer as? ByteArrayPixelBuffer
      ?: error("JvmImageEncoder requires ByteArrayPixelBuffer, got ${ir.buffer::class.simpleName}")

    val bufferedImage = toBufferedImage(srcBuffer.data, ir.width, ir.height, ir.stride, ir.pixelFormat)

    val outputStream = ByteArrayOutputStream()

    if (format == ImageFormat.JPEG) {
      // JPEG requires explicit quality parameter and no alpha channel.
      val jpegWriter = ImageIO.getImageWritersByFormatName("JPEG").next()
      val param = jpegWriter.defaultWriteParam.apply {
        compressionMode = ImageWriteParam.MODE_EXPLICIT
        compressionQuality = quality
      }
      ImageIO.createImageOutputStream(outputStream).use { ios ->
        jpegWriter.output = ios
        jpegWriter.write(null, IIOImage(bufferedImage, null, null), param)
      }
      jpegWriter.dispose()
    } else {
      ImageIO.write(bufferedImage, imageIoFormatName, outputStream)
    }

    val result = outputStream.toByteArray()
    context.logger.debug("JvmImageEncoder: produced ${result.size} bytes")
    return result
  }

  companion object {
    /**
     * Converts raw RGBA_8888 or RGB_888 pixel data into a [BufferedImage].
     */
    internal fun toBufferedImage(
      data: ByteArray,
      width: Int,
      height: Int,
      stride: Int,
      pixelFormat: PixelFormat,
    ): BufferedImage {
      return when (pixelFormat) {
        PixelFormat.RGBA_8888 -> {
          // JPEG doesn't support alpha — use TYPE_INT_RGB to drop alpha.
          val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
          for (y in 0 until height) {
            for (x in 0 until width) {
              val offset = y * stride + x * 4
              val r = data[offset].toInt() and 0xFF
              val g = data[offset + 1].toInt() and 0xFF
              val b = data[offset + 2].toInt() and 0xFF
              image.setRGB(x, y, (r shl 16) or (g shl 8) or b)
            }
          }
          image
        }
        PixelFormat.RGB_888 -> {
          val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
          for (y in 0 until height) {
            for (x in 0 until width) {
              val offset = y * stride + x * 3
              val r = data[offset].toInt() and 0xFF
              val g = data[offset + 1].toInt() and 0xFF
              val b = data[offset + 2].toInt() and 0xFF
              image.setRGB(x, y, (r shl 16) or (g shl 8) or b)
            }
          }
          image
        }
        else -> error("JvmImageEncoder does not support pixel format: $pixelFormat")
      }
    }

    /**
     * Creates a [BufferedImage] with alpha channel for PNG encoding.
     */
    internal fun toBufferedImageWithAlpha(
      data: ByteArray,
      width: Int,
      height: Int,
      stride: Int,
    ): BufferedImage {
      val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
      for (y in 0 until height) {
        for (x in 0 until width) {
          val offset = y * stride + x * 4
          val r = data[offset].toInt() and 0xFF
          val g = data[offset + 1].toInt() and 0xFF
          val b = data[offset + 2].toInt() and 0xFF
          val a = data[offset + 3].toInt() and 0xFF
          image.setRGB(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
        }
      }
      return image
    }
  }
}
