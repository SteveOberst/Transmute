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
      // Best-effort: avoid premultiplication to keep transforms predictable.
      inPremultiplied = false
    }

    val bitmap = BitmapFactory.decodeByteArray(source, 0, source.size, opts)
      ?: error("AndroidBitmapImageDecoder: BitmapFactory returned null")

    val width = bitmap.width
    val height = bitmap.height
    val argb = ByteArray(width * height * 4)
    bitmap.copyPixelsToBuffer(ByteBuffer.wrap(argb))

    val rgba = argbToRgba(argb)

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

  private fun argbToRgba(argb: ByteArray): ByteArray {
    val out = ByteArray(argb.size)
    var i = 0
    while (i < argb.size) {
      val a = argb[i]
      val r = argb[i + 1]
      val g = argb[i + 2]
      val b = argb[i + 3]
      out[i] = r
      out[i + 1] = g
      out[i + 2] = b
      out[i + 3] = a
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

    val argb = rgbaToArgb(buffer.data)

    val bitmap = Bitmap.createBitmap(ir.width, ir.height, Bitmap.Config.ARGB_8888)
    bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(argb))

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

  private fun rgbaToArgb(rgba: ByteArray): ByteArray {
    val out = ByteArray(rgba.size)
    var i = 0
    while (i < rgba.size) {
      val r = rgba[i]
      val g = rgba[i + 1]
      val b = rgba[i + 2]
      val a = rgba[i + 3]
      out[i] = a
      out[i + 1] = r
      out[i + 2] = g
      out[i + 3] = b
      i += 4
    }
    return out
  }
}
