package dev.transmute.image.codecs.stub

import dev.transmute.core.ConversionContext
import dev.transmute.core.ImageFormat
import dev.transmute.image.*

/**
 * Placeholder image decoder — returns a 1×1 transparent pixel.
 * Replace with a real platform codec (Skia, Android BitmapFactory, etc.).
 */
class StubImageDecoder : ImageDecoder {
  override val supportedFormats = setOf(ImageFormat.JPEG, ImageFormat.PNG, ImageFormat.WEBP)

  override suspend fun decode(source: ByteArray, context: ConversionContext): ImageIR {
    context.logger.debug("StubImageDecoder: producing 1×1 placeholder")
    return ImageIR(
      buffer = ByteArrayPixelBuffer(byteArrayOf(0, 0, 0, 0)),
      width = 1,
      height = 1,
      stride = 4,
      pixelFormat = PixelFormat.RGBA_8888,
      alphaSemantics = AlphaSemantics.STRAIGHT,
      colorInfo = ColorInfo(),
    )
  }
}

/**
 * Placeholder image encoder — returns an empty byte array.
 */
class StubImageEncoder : ImageEncoder {
  override val supportedFormats = setOf(ImageFormat.JPEG, ImageFormat.PNG, ImageFormat.WEBP)

  override suspend fun encode(ir: ImageIR, context: ConversionContext): ByteArray {
    context.logger.debug("StubImageEncoder: producing empty output")
    return ByteArray(0)
  }
}
