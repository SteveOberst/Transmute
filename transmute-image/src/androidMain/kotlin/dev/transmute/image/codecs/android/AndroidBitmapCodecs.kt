package dev.transmute.image.codecs.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.transmute.common.PipelineContext
import dev.transmute.image.*
import dev.transmute.io.TSource
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes
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

  override suspend fun decode(source: TSource, options: ImageDecodeOptions, context: PipelineContext): ImageIR {
    val opts = BitmapFactory.Options().apply {
      inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    val bytes = source.readAll()
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
      out[i] = bgra[i + 2] // R
      out[i + 1] = bgra[i + 1] // G
      out[i + 2] = bgra[i] // B
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

  override suspend fun encode(ir: ImageIR, format: ImageFormat, options: ImageEncodeOptions, context: PipelineContext): Bytes {
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
      out[i] = rgba[i + 2] // B
      out[i + 1] = rgba[i + 1] // G
      out[i + 2] = rgba[i] // R
      out[i + 3] = rgba[i + 3] // A
      i += 4
    }
    return out
  }
}
