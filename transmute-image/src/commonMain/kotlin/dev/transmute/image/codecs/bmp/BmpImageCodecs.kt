package dev.transmute.image.codecs.bmp

import dev.transmute.model.core.Bytes
import dev.transmute.common.PipelineContext
import dev.transmute.image.*

/**
 * Minimal, pure-Kotlin BMP (Windows Bitmap) decoder/encoder.
 *
 * Supports:
 * - BITMAPFILEHEADER + BITMAPINFOHEADER
 * - Uncompressed BI_RGB
 * - 24-bit BGR and 32-bit BGRA
 *
 * Does NOT support:
 * - RLE compression
 * - BITMAPV4/V5 masks/profiles
 * - Paletted (1/4/8bpp) BMPs
 */
class BmpImageDecoder : ImageDecoder {
  override val supportedFormats: Set<ImageFormat> = setOf(ImageFormat.Bmp)

  override fun sniff(data: Bytes): ImageFormat? {
    val bytes = data.data
    if (bytes.size >= 2 && bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte()) return ImageFormat.Bmp
    return null
  }

  override suspend fun decode(source: Bytes, options: ImageDecodeOptions, context: PipelineContext): ImageIR {
    val bytes = source.data
    require(bytes.size >= 54) { "BMP too small" }
    require(bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte()) { "Not a BMP" }

    val pixelOffset = leInt(bytes, 10)
    val dibHeaderSize = leInt(bytes, 14)
    require(dibHeaderSize >= 40) { "Unsupported DIB header size: $dibHeaderSize" }

    val width = leInt(bytes, 18)
    val rawHeight = leInt(bytes, 22)
    val topDown = rawHeight < 0
    val height = kotlin.math.abs(rawHeight)

    val planes = leShort(bytes, 26)
    require(planes == 1) { "Invalid BMP planes: $planes" }

    val bpp = leShort(bytes, 28)
    require(bpp == 24 || bpp == 32) { "Unsupported BMP bpp: $bpp" }

    val compression = leInt(bytes, 30)
    require(compression == 0) { "Unsupported BMP compression: $compression" }

    require(width > 0 && height > 0) { "Invalid BMP dimensions: ${width}x${height}" }
    require(pixelOffset in 0..bytes.size) { "Invalid BMP pixel data offset" }

    val bytesPerPixel = bpp / 8
    val rowSizeUnpadded = width * bytesPerPixel
    val rowSize = ((rowSizeUnpadded + 3) / 4) * 4
    val needed = pixelOffset + rowSize * height
    require(needed <= bytes.size) { "BMP truncated: need $needed bytes, have ${bytes.size}" }

    val rgba = ByteArray(width * height * 4)

    for (y in 0 until height) {
      val srcY = if (topDown) y else (height - 1 - y)
      val rowStart = pixelOffset + srcY * rowSize

      for (x in 0 until width) {
        val srcIndex = rowStart + x * bytesPerPixel
        val b = bytes[srcIndex]
        val g = bytes[srcIndex + 1]
        val r = bytes[srcIndex + 2]
        val a = if (bytesPerPixel == 4) bytes[srcIndex + 3] else 0xFF.toByte()

        val dstIndex = (y * width + x) * 4
        rgba[dstIndex] = r
        rgba[dstIndex + 1] = g
        rgba[dstIndex + 2] = b
        rgba[dstIndex + 3] = a
      }
    }

    return ImageIR(
      buffer = ByteArrayPixelBuffer(rgba),
      width = width,
      height = height,
      stride = width * 4,
      pixelFormat = PixelFormat.RGBA_8888,
      alphaSemantics = if (bytesPerPixel == 4) AlphaSemantics.STRAIGHT else AlphaSemantics.OPAQUE,
      colorInfo = ColorInfo(),
      orientation = Orientation.NORMAL,
      metadata = ImageMetadata(),
    )
  }

  private fun leInt(bytes: ByteArray, offset: Int): Int {
    return (bytes[offset].toInt() and 0xFF) or
      ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
      ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
      ((bytes[offset + 3].toInt() and 0xFF) shl 24)
  }

  private fun leShort(bytes: ByteArray, offset: Int): Int {
    return (bytes[offset].toInt() and 0xFF) or
      ((bytes[offset + 1].toInt() and 0xFF) shl 8)
  }
}

class BmpImageEncoder : ImageEncoder {
  override val supportedFormats: Set<ImageFormat> = setOf(ImageFormat.Bmp)

  override suspend fun encode(
    ir: ImageIR,
    format: ImageFormat,
    options: ImageEncodeOptions,
    context: PipelineContext,
  ): Bytes {
    val buffer = ir.buffer as? ByteArrayPixelBuffer
      ?: error("BmpImageEncoder requires ByteArrayPixelBuffer")
    require(ir.pixelFormat == PixelFormat.RGBA_8888) { "Only RGBA_8888 is supported" }

    require(format == ImageFormat.Bmp) { "BmpImageEncoder only supports BMP, got $format" }

    // Encode as 24-bit BI_RGB for maximal compatibility.
    val bytesPerPixel = 3
    val rowSizeUnpadded = ir.width * bytesPerPixel
    val rowSize = ((rowSizeUnpadded + 3) / 4) * 4
    val pixelBytes = rowSize * ir.height

    val fileHeaderSize = 14
    val dibHeaderSize = 40
    val pixelOffset = fileHeaderSize + dibHeaderSize
    val fileSize = pixelOffset + pixelBytes

    val out = ByteArray(fileSize)

    // BITMAPFILEHEADER
    out[0] = 'B'.code.toByte()
    out[1] = 'M'.code.toByte()
    putLeInt(out, 2, fileSize)
    putLeShort(out, 6, 0)
    putLeShort(out, 8, 0)
    putLeInt(out, 10, pixelOffset)

    // BITMAPINFOHEADER
    putLeInt(out, 14, dibHeaderSize)
    putLeInt(out, 18, ir.width)
    putLeInt(out, 22, ir.height) // bottom-up
    putLeShort(out, 26, 1)
    putLeShort(out, 28, 24)
    putLeInt(out, 30, 0) // BI_RGB
    putLeInt(out, 34, pixelBytes)
    putLeInt(out, 38, 0) // ppm X
    putLeInt(out, 42, 0) // ppm Y
    putLeInt(out, 46, 0) // colors used
    putLeInt(out, 50, 0) // important colors

    val rgba = buffer.data
    val padding = rowSize - rowSizeUnpadded

    for (y in 0 until ir.height) {
      // BMP is bottom-up
      val dstY = ir.height - 1 - y
      val rowStart = pixelOffset + dstY * rowSize

      for (x in 0 until ir.width) {
        val srcIndex = (y * ir.width + x) * 4
        val r = rgba[srcIndex]
        val g = rgba[srcIndex + 1]
        val b = rgba[srcIndex + 2]

        val dstIndex = rowStart + x * 3
        out[dstIndex] = b
        out[dstIndex + 1] = g
        out[dstIndex + 2] = r
      }

      // row padding
      for (p in 0 until padding) {
        out[rowStart + rowSizeUnpadded + p] = 0
      }
    }

    return Bytes(out)
  }

  private fun putLeInt(bytes: ByteArray, offset: Int, value: Int) {
    bytes[offset] = (value and 0xFF).toByte()
    bytes[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    bytes[offset + 2] = ((value ushr 16) and 0xFF).toByte()
    bytes[offset + 3] = ((value ushr 24) and 0xFF).toByte()
  }

  private fun putLeShort(bytes: ByteArray, offset: Int, value: Int) {
    bytes[offset] = (value and 0xFF).toByte()
    bytes[offset + 1] = ((value ushr 8) and 0xFF).toByte()
  }
}
