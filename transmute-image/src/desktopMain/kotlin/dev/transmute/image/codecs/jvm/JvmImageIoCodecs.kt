package dev.transmute.image.codecs.jvm

import dev.transmute.core.ConversionContext
import dev.transmute.core.ImageFormat
import dev.transmute.image.*
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

class JvmImageIoDecoder : ImageDecoder {
  override val supportedFormats: Set<ImageFormat> = setOf(
    ImageFormat.JPEG,
    ImageFormat.PNG,
    ImageFormat.GIF,
    ImageFormat.BMP,
    ImageFormat.TIFF,
    ImageFormat.WEBP,
  )

  override suspend fun decode(source: ByteArray, context: ConversionContext): ImageIR {
    val input = ByteArrayInputStream(source)
    val img = ImageIO.read(input) ?: error("ImageIO could not decode image")

    val converted = BufferedImage(img.width, img.height, BufferedImage.TYPE_4BYTE_ABGR)
    val g: Graphics2D = converted.createGraphics()
    g.drawImage(img, 0, 0, null)
    g.dispose()

    val abgr = (converted.raster.dataBuffer as DataBufferByte).data
    val rgba = abgrToRgba(abgr)

    return ImageIR(
      buffer = ByteArrayPixelBuffer(rgba),
      width = converted.width,
      height = converted.height,
      stride = converted.width * 4,
      pixelFormat = PixelFormat.RGBA_8888,
      alphaSemantics = AlphaSemantics.STRAIGHT,
      colorInfo = ColorInfo(),
      orientation = Orientation.NORMAL,
      metadata = ImageMetadata(),
    )
  }

  private fun abgrToRgba(abgr: ByteArray): ByteArray {
    val out = ByteArray(abgr.size)
    var i = 0
    while (i < abgr.size) {
      val a = abgr[i]
      val b = abgr[i + 1]
      val g = abgr[i + 2]
      val r = abgr[i + 3]
      out[i] = r
      out[i + 1] = g
      out[i + 2] = b
      out[i + 3] = a
      i += 4
    }
    return out
  }
}

class JvmImageIoEncoder : ImageEncoder {
  override val supportedFormats: Set<ImageFormat> = setOf(
    ImageFormat.JPEG,
    ImageFormat.PNG,
    ImageFormat.GIF,
    ImageFormat.TIFF,
    ImageFormat.WEBP,
  )

  override suspend fun encode(ir: ImageIR, context: ConversionContext): ByteArray {
    val buffer = ir.buffer as? ByteArrayPixelBuffer
      ?: error("JvmImageIoEncoder requires ByteArrayPixelBuffer")
    require(ir.pixelFormat == PixelFormat.RGBA_8888) { "Only RGBA_8888 is supported" }

    val format = (context.scratchpad["image.output.format"] as? ImageFormat) ?: ImageFormat.PNG
    val quality = (context.scratchpad["image.output.quality"] as? Number)?.toFloat()
      ?: 0.85f

    return when (format) {
      ImageFormat.JPEG -> encodeJpeg(buffer, ir.width, ir.height, ir.stride, quality)
      ImageFormat.PNG -> encodePng(buffer, ir.width, ir.height, ir.stride)
      ImageFormat.GIF -> encodeViaImageIo(buffer, ir.width, ir.height, ir.stride, "gif", dropAlpha = true)
      ImageFormat.TIFF -> encodeViaImageIo(buffer, ir.width, ir.height, ir.stride, "tiff", dropAlpha = false)
      ImageFormat.WEBP -> encodeWebp(buffer, ir.width, ir.height, ir.stride, quality)
      else -> encodePng(buffer, ir.width, ir.height, ir.stride)
    }
  }

  private fun encodePng(
    buffer: ByteArrayPixelBuffer,
    width: Int,
    height: Int,
    stride: Int,
  ): ByteArray {
    val image = BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR)
    val abgr = rgbaToAbgr(buffer.data)
    val dest = (image.raster.dataBuffer as DataBufferByte).data
    System.arraycopy(abgr, 0, dest, 0, abgr.size)

    val out = ByteArrayOutputStream()
    val ok = ImageIO.write(image, "png", out)
    if (!ok) error("ImageIO could not encode PNG")
    return out.toByteArray()
  }

  private fun encodeJpeg(
    buffer: ByteArrayPixelBuffer,
    width: Int,
    height: Int,
    stride: Int,
    quality: Float,
  ): ByteArray {
    val q = quality.coerceIn(0.0f, 1.0f)

    // JPEG doesn't support alpha; drop it into an RGB image.
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val rgba = buffer.data
    for (y in 0 until height) {
      val rowBase = y * stride
      for (x in 0 until width) {
        val off = rowBase + x * 4
        val r = rgba[off].toInt() and 0xFF
        val g = rgba[off + 1].toInt() and 0xFF
        val b = rgba[off + 2].toInt() and 0xFF
        image.setRGB(x, y, (r shl 16) or (g shl 8) or b)
      }
    }

    val writer = ImageIO.getImageWritersByFormatName("jpeg").asSequence().firstOrNull()
      ?: error("No ImageIO JPEG writer available")
    val param = writer.defaultWriteParam.apply {
      if (canWriteCompressed()) {
        compressionMode = ImageWriteParam.MODE_EXPLICIT
        compressionQuality = q
      }
    }

    val out = ByteArrayOutputStream()
    ImageIO.createImageOutputStream(out).use { ios ->
      writer.output = ios
      writer.write(null, IIOImage(image, null, null), param)
    }
    writer.dispose()
    return out.toByteArray()
  }

  /**
   * WebP encode via TwelveMonkeys ImageIO plugin with lossy quality control.
   * Falls back to lossless if no writer supports compression params.
   */
  private fun encodeWebp(
    buffer: ByteArrayPixelBuffer,
    width: Int,
    height: Int,
    stride: Int,
    quality: Float,
  ): ByteArray {
    val q = quality.coerceIn(0.0f, 1.0f)

    // WebP supports alpha — use ABGR for full transparency support.
    val image = BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR)
    val abgr = rgbaToAbgr(buffer.data)
    val dest = (image.raster.dataBuffer as DataBufferByte).data
    System.arraycopy(abgr, 0, dest, 0, abgr.size)

    val writer = ImageIO.getImageWritersByFormatName("webp").asSequence().firstOrNull()
      ?: error("No ImageIO WebP writer available — ensure TwelveMonkeys imageio-webp is on the classpath")
    val param = writer.defaultWriteParam.apply {
      if (canWriteCompressed()) {
        compressionMode = ImageWriteParam.MODE_EXPLICIT
        compressionType = compressionTypes?.firstOrNull() ?: "Lossy"
        compressionQuality = q
      }
    }

    val out = ByteArrayOutputStream()
    ImageIO.createImageOutputStream(out).use { ios ->
      writer.output = ios
      writer.write(null, IIOImage(image, null, null), param)
    }
    writer.dispose()
    return out.toByteArray()
  }

  /**
   * Generic ImageIO encode path for formats like GIF and TIFF.
   */
  private fun encodeViaImageIo(
    buffer: ByteArrayPixelBuffer,
    width: Int,
    height: Int,
    stride: Int,
    formatName: String,
    dropAlpha: Boolean,
  ): ByteArray {
    val image = if (dropAlpha) {
      // GIF doesn't support full alpha — drop to RGB
      val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
      val rgba = buffer.data
      for (y in 0 until height) {
        val rowBase = y * stride
        for (x in 0 until width) {
          val off = rowBase + x * 4
          val r = rgba[off].toInt() and 0xFF
          val g = rgba[off + 1].toInt() and 0xFF
          val b = rgba[off + 2].toInt() and 0xFF
          img.setRGB(x, y, (r shl 16) or (g shl 8) or b)
        }
      }
      img
    } else {
      val img = BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR)
      val abgr = rgbaToAbgr(buffer.data)
      val dest = (img.raster.dataBuffer as DataBufferByte).data
      System.arraycopy(abgr, 0, dest, 0, abgr.size)
      img
    }

    val out = ByteArrayOutputStream()
    val ok = ImageIO.write(image, formatName, out)
    if (!ok) error("ImageIO could not encode $formatName")
    return out.toByteArray()
  }

  private fun rgbaToAbgr(rgba: ByteArray): ByteArray {
    val out = ByteArray(rgba.size)
    var i = 0
    while (i < rgba.size) {
      val r = rgba[i]
      val g = rgba[i + 1]
      val b = rgba[i + 2]
      val a = rgba[i + 3]
      out[i] = a
      out[i + 1] = b
      out[i + 2] = g
      out[i + 3] = r
      i += 4
    }
    return out
  }
}
