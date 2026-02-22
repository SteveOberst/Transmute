package dev.transmute.image.codecs.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.transmute.core.TransmuteContext
import dev.transmute.core.Bytes
import dev.transmute.core.asBytes
import dev.transmute.image.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.roundToInt

class AndroidBitmapImageDecoder : ImageDecoder {
  override val supportedFormats: Set<ImageFormat> = setOf(
    ImageFormat.Jpeg,
    ImageFormat.Png,
    ImageFormat.Webp,
    ImageFormat.Gif,
    ImageFormat.Bmp,
    ImageFormat.Tiff,
    ImageFormat.Heif,
    ImageFormat.Heic,
    ImageFormat.Avif,
  )

  override fun sniff(data: Bytes): ImageFormat? {
    val bytes = data.data
    if (bytes.size < 4) return null
    // JPEG
    if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte())
      return ImageFormat.Jpeg
    // PNG
    if (bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
      bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte())
      return ImageFormat.Png
    // GIF
    if (bytes.size >= 6 && bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() &&
      bytes[2] == 0x46.toByte() && bytes[3] == 0x38.toByte())
      return ImageFormat.Gif
    // BMP
    if (bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte())
      return ImageFormat.Bmp
    // TIFF
    if ((bytes[0] == 0x49.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x2A.toByte() && bytes[3] == 0x00.toByte()) ||
      (bytes[0] == 0x4D.toByte() && bytes[1] == 0x4D.toByte() && bytes[2] == 0x00.toByte() && bytes[3] == 0x2A.toByte()))
      return ImageFormat.Tiff
    // WebP
    if (bytes.size >= 12 && bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
      bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
      bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
      bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte())
      return ImageFormat.Webp
    // HEIF/HEIC/AVIF
    if (bytes.size >= 12 && bytes[4] == 0x66.toByte() && bytes[5] == 0x74.toByte() &&
      bytes[6] == 0x79.toByte() && bytes[7] == 0x70.toByte()) {
      val brand = bytes.sliceArray(8 until 12).decodeToString()
      return when {
        brand == "heic" || brand == "heix" -> ImageFormat.Heic
        brand == "mif1" || brand == "msf1" -> ImageFormat.Heif
        brand == "hevc" || brand == "hevx" -> ImageFormat.Heic
        brand == "avif" || brand == "avis" -> ImageFormat.Avif
        brand == "heif" || brand == "heis" -> ImageFormat.Heif
        else -> null
      }
    }
    return null
  }

  override suspend fun decode(source: Bytes, options: ImageDecodeOptions, context: TransmuteContext): ImageIR {
    val opts = BitmapFactory.Options().apply {
      inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    val bytes = source.data
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
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
    ImageFormat.Jpeg,
    ImageFormat.Png,
    ImageFormat.Webp,
  )

  override suspend fun encode(
    ir: ImageIR,
    format: ImageFormat,
    options: ImageEncodeOptions,
    context: TransmuteContext,
  ): Bytes {
    val buffer = ir.buffer as? ByteArrayPixelBuffer
      ?: error("AndroidBitmapImageEncoder requires ByteArrayPixelBuffer")
    require(ir.pixelFormat == PixelFormat.RGBA_8888) { "Only RGBA_8888 is supported" }

    require(format in supportedFormats) { "Unsupported format $format" }

    val qInt = when (format) {
      ImageFormat.Jpeg -> {
        val quality = (options as? JpegEncodeOptions)?.quality ?: 0.85f
        (quality * 100f).roundToInt().coerceIn(0, 100)
      }
      // Android Bitmap WEBP encoder semantics vary by API level. Treat quality as best-effort.
      ImageFormat.Webp -> {
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
      ImageFormat.Png -> Bitmap.CompressFormat.PNG
      ImageFormat.Webp -> {
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

    return out.toByteArray().asBytes()
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



