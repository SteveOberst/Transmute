@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.transmute.image.codecs.ios

import dev.transmute.common.PipelineContext
import dev.transmute.image.ImageFormat
import dev.transmute.image.*
import kotlinx.cinterop.*
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataCreateMutable
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.kCFNumberDoubleType
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreGraphics.*
import platform.ImageIO.*
import dev.transmute.image.ImageDecodeOptions

class IosImageIoDecoder : ImageDecoder {
  override val supportedFormats: Set<ImageFormat> = setOf(
    ImageFormat.JPEG,
    ImageFormat.PNG,
    ImageFormat.HEIF,
    ImageFormat.HEIC,
    ImageFormat.WEBP,
    ImageFormat.GIF,
    ImageFormat.BMP,
    ImageFormat.TIFF,
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

  override suspend fun decode(source: ByteArray, options: ImageDecodeOptions, context: PipelineContext): ImageIR {
    val cfData = source.usePinned { pinned ->
      CFDataCreate(null, pinned.addressOf(0).reinterpret(), source.size.toLong())
    } ?: error("CFDataCreate failed")

    try {
      val src = CGImageSourceCreateWithData(cfData, null)
        ?: error("CGImageSourceCreateWithData failed")

      val cgImage = CGImageSourceCreateImageAtIndex(src, 0u, null)
        ?: error("CGImageSourceCreateImageAtIndex failed")

      val width = CGImageGetWidth(cgImage).toInt()
      val height = CGImageGetHeight(cgImage).toInt()
      val bytesPerRow = width * 4
      val outBytes = ByteArray(bytesPerRow * height)

      val colorSpace = CGColorSpaceCreateDeviceRGB()
      val bitmapInfo = (
        CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value.toUInt() or
          kCGBitmapByteOrder32Big.toUInt()
        )

      outBytes.usePinned { pinned ->
        val ctx = CGBitmapContextCreate(
          data = pinned.addressOf(0),
          width = width.toULong(),
          height = height.toULong(),
          bitsPerComponent = 8u,
          bytesPerRow = bytesPerRow.toULong(),
          space = colorSpace,
          bitmapInfo = bitmapInfo,
        ) ?: error("CGBitmapContextCreate failed")

        CGContextDrawImage(ctx, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), cgImage)
        CGContextRelease(ctx)
      }

      CGColorSpaceRelease(colorSpace)
      CGImageRelease(cgImage)
      CFRelease(src)

      return ImageIR(
        buffer = ByteArrayPixelBuffer(outBytes),
        width = width,
        height = height,
        stride = bytesPerRow,
        pixelFormat = PixelFormat.RGBA_8888,
        alphaSemantics = AlphaSemantics.PREMULTIPLIED,
        colorInfo = ColorInfo(),
        orientation = Orientation.NORMAL,
        metadata = ImageMetadata(),
      )
    } finally {
      CFRelease(cfData)
    }
  }
}

class IosImageIoEncoder : ImageEncoder {
  override val supportedFormats: Set<ImageFormat> = setOf(
    ImageFormat.JPEG,
    ImageFormat.PNG,
    ImageFormat.HEIF,
    ImageFormat.HEIC,
    ImageFormat.AVIF,
    ImageFormat.WEBP,
    ImageFormat.TIFF,
    ImageFormat.GIF,
    ImageFormat.BMP,
  )

  override suspend fun encode(
    ir: ImageIR,
    format: ImageFormat,
    options: ImageEncodeOptions,
    context: PipelineContext,
  ): ByteArray {
    val buffer = ir.buffer as? ByteArrayPixelBuffer
      ?: error("IosImageIoEncoder requires ByteArrayPixelBuffer")
    require(ir.pixelFormat == PixelFormat.RGBA_8888) { "Only RGBA_8888 is supported" }

    require(format in supportedFormats) { "Unsupported format $format" }
    val utiString = when (format) {
      ImageFormat.JPEG -> "public.jpeg"
      ImageFormat.PNG -> "public.png"
      ImageFormat.HEIF -> "public.heif"
      ImageFormat.HEIC -> "public.heic"
      ImageFormat.AVIF -> "public.avif"
      ImageFormat.WEBP -> "public.webp"
      ImageFormat.TIFF -> "public.tiff"
      ImageFormat.GIF -> "com.compuserve.gif"
      ImageFormat.BMP -> "com.microsoft.bmp"
      else -> "public.png"
    }
    val uti = CFStringCreateWithCString(null, utiString, kCFStringEncodingUTF8)
      ?: error("CFStringCreateWithCString failed for $utiString")

    // Build CGImage from RGBA buffer
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    val bitmapInfo = (
      CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value.toUInt() or
        kCGBitmapByteOrder32Big.toUInt()
      )

    val cgImage = buffer.data.usePinned { pinned ->
      val provider = CGDataProviderCreateWithData(null, pinned.addressOf(0), buffer.data.size.toULong(), null)
        ?: error("CGDataProviderCreateWithData failed")
      val img = CGImageCreate(
        width = ir.width.toULong(),
        height = ir.height.toULong(),
        bitsPerComponent = 8u,
        bitsPerPixel = 32u,
        bytesPerRow = (ir.width * 4).toULong(),
        space = colorSpace,
        bitmapInfo = bitmapInfo,
        provider = provider,
        decode = null,
        shouldInterpolate = false,
        intent = CGColorRenderingIntent.kCGRenderingIntentDefault,
      ) ?: error("CGImageCreate failed")
      CGDataProviderRelease(provider)
      img
    }

    val mutableData = CFDataCreateMutable(null, 0)
      ?: error("CFDataCreateMutable failed")

    try {
      val dest = CGImageDestinationCreateWithData(mutableData, uti, 1u, null)
        ?: error("CGImageDestinationCreateWithData failed")

      val props = if (format == ImageFormat.JPEG) {
        val q = ((options as? JpegEncodeOptions)?.quality ?: 0.85f).coerceIn(0f, 1f).toDouble()
        val num = memScoped { CFNumberCreate(null, kCFNumberDoubleType, alloc<DoubleVar>().apply { value = q }.ptr) }
          ?: error("CFNumberCreate failed")
        val dict = CFDictionaryCreateMutable(null, 0, null, null)
          ?: error("CFDictionaryCreateMutable failed")
        CFDictionarySetValue(dict, kCGImageDestinationLossyCompressionQuality, num)
        // CFDictionary retains values; release our local reference.
        CFRelease(num)
        dict
      } else {
        null
      }

      CGImageDestinationAddImage(dest, cgImage, props)
      val ok = CGImageDestinationFinalize(dest)
      if (!ok) error("CGImageDestinationFinalize failed")

      if (props != null) {
        CFRelease(props)
      }

      val len = CFDataGetLength(mutableData).toInt()
      val ptr = CFDataGetBytePtr(mutableData) ?: error("CFDataGetBytePtr failed")
      return ByteArray(len) { idx -> ptr[idx].toByte() }
    } finally {
      CGImageRelease(cgImage)
      CGColorSpaceRelease(colorSpace)
      CFRelease(mutableData)
      CFRelease(uti)
    }
  }
}
