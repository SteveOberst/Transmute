package dev.transmute.image.codecs.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.transmute.core.ConversionContext
import dev.transmute.core.ImageFormat
import dev.transmute.image.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class AndroidBitmapImageDecoder : ImageDecoder {
  override val supportedFormats: Set<ImageFormat> = setOf(
    ImageFormat.JPEG,
    ImageFormat.PNG,
    ImageFormat.WEBP,
    ImageFormat.GIF,
    ImageFormat.BMP,
    ImageFormat.TIFF,
    ImageFormat.HEIF,
    ImageFormat.HEIC,
    ImageFormat.AVIF,
  )

  override suspend fun decode(source: ByteArray, context: ConversionContext): ImageIR {
    val opts = BitmapFactory.Options().apply {
      inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    val bitmap = BitmapFactory.decodeByteArray(source, 0, source.size, opts)
      ?: error("AndroidBitmapImageDecoder: BitmapFactory returned null")

    val width = bitmap.width
    val height = bitmap.height
    val bgra = ByteArray(width * height * 4)
    bitmap.copyPixelsToBuffer(ByteBuffer.wrap(bgra))

    val rgba = bgraToRgba(bgra)

    return ImageIR(
      buffer = ByteArrayPixelBuffer(rgba),
      width = width,
      height = height,
      stride = width * 4,
      pixelFormat = PixelFormat.RGBA_8888,
      alphaSemantics = if (bitmap.hasAlpha()) AlphaSemantics.STRAIGHT else AlphaSemantics.OPAQUE,
      colorInfo = ColorInfo(),
      orientation = Orientation.NORMAL,
      metadata = ImageMetadata(),
    )
  }

  private fun bgraToRgba(bgra: ByteArray): ByteArray {
    val out = ByteArray(bgra.size)
    var i = 0
    while (i < bgra.size) {
      out[i]     = bgra[i + 2] // R
      out[i + 1] = bgra[i + 1] // G
      out[i + 2] = bgra[i]     // B
      out[i + 3] = bgra[i + 3] // A
      i += 4
    }
    return out
  }
}

class AndroidBitmapImageEncoder : ImageEncoder {
  override val supportedFormats: Set<ImageFormat> = setOf(
    ImageFormat.JPEG,
    ImageFormat.PNG,
    ImageFormat.WEBP,
  )

  override suspend fun encode(ir: ImageIR, context: ConversionContext): ByteArray {
    val buffer = ir.buffer as? ByteArrayPixelBuffer
      ?: error("AndroidBitmapImageEncoder requires ByteArrayPixelBuffer")
    require(ir.pixelFormat == PixelFormat.RGBA_8888) { "Only RGBA_8888 is supported" }

    val quality = ((context.scratchpad["image.quality"] as? Float) ?: 0.85f)
      .coerceIn(0f, 1f)
    val qInt = (quality * 100).toInt().coerceIn(0, 100)

    val bgra = rgbaToBgra(buffer.data)

    val bitmap = Bitmap.createBitmap(ir.width, ir.height, Bitmap.Config.ARGB_8888)
    bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bgra))

    val out = ByteArrayOutputStream()
    val outputFormat = context.scratchpad["image.output.format"] as? ImageFormat
    val compressFormat = when (outputFormat) {
      ImageFormat.PNG -> Bitmap.CompressFormat.PNG
      ImageFormat.WEBP -> {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
          Bitmap.CompressFormat.WEBP_LOSSY
        } else {
          @Suppress("DEPRECATION")
          Bitmap.CompressFormat.WEBP
        }
      }
      else -> Bitmap.CompressFormat.JPEG
    }

    val ok = bitmap.compress(compressFormat, qInt, out)
    if (!ok) error("AndroidBitmapImageEncoder: bitmap.compress returned false")

    return out.toByteArray()
  }

  private fun rgbaToBgra(rgba: ByteArray): ByteArray {
    val out = ByteArray(rgba.size)
    var i = 0
    while (i < rgba.size) {
      out[i]     = rgba[i + 2] // B
      out[i + 1] = rgba[i + 1] // G
      out[i + 2] = rgba[i]     // R
      out[i + 3] = rgba[i + 3] // A
      i += 4
    }
    return out
  }
}
