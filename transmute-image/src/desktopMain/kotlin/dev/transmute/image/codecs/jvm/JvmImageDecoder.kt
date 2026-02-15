package dev.transmute.image.codecs.jvm

import dev.transmute.core.ConversionContext
import dev.transmute.core.ImageFormat
import dev.transmute.image.AlphaSemantics
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.ColorInfo
import dev.transmute.image.ImageIR
import dev.transmute.image.ImageDecoder
import dev.transmute.image.PixelFormat
import java.awt.color.ColorSpace
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * JVM image decoder backed by [ImageIO].
 *
 * Supports JPEG, PNG, BMP, GIF, and any format that has a registered
 * `javax.imageio` reader on the classpath. Images are always converted
 * to [PixelFormat.RGBA_8888] for uniform downstream processing.
 */
class JvmImageDecoder : ImageDecoder {

  override val supportedFormats: Set<ImageFormat> = setOf(
    ImageFormat.JPEG,
    ImageFormat.PNG,
    ImageFormat.BMP,
    ImageFormat.GIF,
    ImageFormat.WEBP, // Available if a WebP ImageIO plugin is on the classpath.
  )

  override suspend fun decode(source: ByteArray, context: ConversionContext): ImageIR {
    context.logger.debug("JvmImageDecoder: decoding ${source.size} bytes")

    val bufferedImage = ByteArrayInputStream(source).use { stream ->
      ImageIO.read(stream) ?: error("ImageIO could not decode the image (unsupported or corrupt)")
    }

    val width = bufferedImage.width
    val height = bufferedImage.height

    // Convert to RGBA_8888 for uniform processing.
    val rgba = toRgba8888(bufferedImage)
    val stride = width * 4
    val hasAlpha = bufferedImage.colorModel.hasAlpha()

    context.logger.debug("JvmImageDecoder: decoded ${width}×${height}, ${rgba.size} bytes, hasAlpha=$hasAlpha")

    return ImageIR(
      buffer = ByteArrayPixelBuffer(rgba),
      width = width,
      height = height,
      stride = stride,
      pixelFormat = PixelFormat.RGBA_8888,
      alphaSemantics = if (hasAlpha) AlphaSemantics.STRAIGHT else AlphaSemantics.OPAQUE,
      colorInfo = ColorInfo(), // sRGB default
    )
  }

  companion object {
    /**
     * Extracts raw RGBA_8888 pixel data from a [BufferedImage],
     * regardless of its original type.
     */
    internal fun toRgba8888(image: BufferedImage): ByteArray {
      val w = image.width
      val h = image.height
      val data = ByteArray(w * h * 4)

      for (y in 0 until h) {
        for (x in 0 until w) {
          val argb = image.getRGB(x, y) // Always returns TYPE_INT_ARGB
          val offset = (y * w + x) * 4
          data[offset] = (argb shr 16 and 0xFF).toByte()     // R
          data[offset + 1] = (argb shr 8 and 0xFF).toByte()  // G
          data[offset + 2] = (argb and 0xFF).toByte()         // B
          data[offset + 3] = (argb shr 24 and 0xFF).toByte() // A
        }
      }
      return data
    }
  }
}
