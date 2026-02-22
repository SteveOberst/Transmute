package dev.transmute.image.codecs.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.transmute.core.ImageFormat
import dev.transmute.core.TransmuteContext
import dev.transmute.image.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.roundToInt

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

  override fun sniff(data: ByteArray): ImageFormat? {
    if (data.size < 4) return null
    // JPEG
    if (data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte() && data[2] == 0xFF.toByte())
      return ImageFormat.JPEG
    // PNG
    if (data.size >= 8 && data[0] == 0x89.toByte() && data[1] == 0x50.toByte() &&
      data[2] == 0x4E.toByte() && data[3] == 0x47.toByte())
      return ImageFormat.PNG
    // GIF
    if (data.size >= 6 && data[0] == 0x47.toByte() && data[1] == 0x49.toByte() &&
      data[2] == 0x46.toByte() && data[3] == 0x38.toByte())
      return ImageFormat.GIF
    // BMP
    if (data[0] == 0x42.toByte() && data[1] == 0x4D.toByte())
      return ImageFormat.BMP
    // TIFF
    if ((data[0] == 0x49.toByte() && data[1] == 0x49.toByte() && data[2] == 0x2A.toByte() && data[3] == 0x00.toByte()) ||
      (data[0] == 0x4D.toByte() && data[1] == 0x4D.toByte() && data[2] == 0x00.toByte() && data[3] == 0x2A.toByte()))
      return ImageFormat.TIFF
    // WebP
    if (data.size >= 12 && data[0] == 0x52.toByte() && data[1] == 0x49.toByte() &&
      data[2] == 0x46.toByte() && data[3] == 0x46.toByte() &&
      data[8] == 0x57.toByte() && data[9] == 0x45.toByte() &&
      data[10] == 0x42.toByte() && data[11] == 0x50.toByte())
      return ImageFormat.WEBP
    // HEIF/HEIC/AVIF
    if (data.size >= 12 && data[4] == 0x66.toByte() && data[5] == 0x74.toByte() &&
      data[6] == 0x79.toByte() && data[7] == 0x70.toByte()) {
      val brand = data.sliceArray(8 until 12).decodeToString()
      return when {
        brand == "heic" || brand == "heix" -> ImageFormat.HEIC
        brand == "mif1" || brand == "msf1" -> ImageFormat.HEIF
        brand == "hevc" || brand == "hevx" -> ImageFormat.HEIC
        brand == "avif" || brand == "avis" -> ImageFormat.AVIF
        brand == "heif" || brand == "heis" -> ImageFormat.HEIF
        else -> null
      }
    }
    return null
  }

  override suspend fun decode(source: ByteArray, options: ImageDecodeOptions, context: TransmuteContext): ImageIR {
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

  override suspend fun encode(
    ir: ImageIR,
    format: ImageFormat,
    options: ImageEncodeOptions,
    context: TransmuteContext,
  ): ByteArray {
    val buffer = ir.buffer as? ByteArrayPixelBuffer
      ?: error("AndroidBitmapImageEncoder requires ByteArrayPixelBuffer")
    require(ir.pixelFormat == PixelFormat.RGBA_8888) { "Only RGBA_8888 is supported" }

    require(format in supportedFormats) { "Unsupported format $format" }

    val qInt = when (format) {
      ImageFormat.JPEG -> {
        val quality = (options as? JpegEncodeOptions)?.quality ?: 0.85f
        (quality * 100f).roundToInt().coerceIn(0, 100)
      }
      // Android Bitmap WEBP encoder semantics vary by API level. Treat quality as best-effort.
      ImageFormat.WEBP -> {
        val quality = (options as? WebPEncodeOptions)?.quality ?: 0.85f
        (quality * 100f).roundToInt().coerceIn(0, 100)
      }
      else -> 100
    }

    val bgra = rgbaToBgra(buffer.data)

    val bitmap = Bitmap.createBitmap(ir.width, ir.height, Bitmap.Config.ARGB_8888)
    bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bgra))

    val out = ByteArrayOutputStream()
    val compressFormat = when (format) {
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



