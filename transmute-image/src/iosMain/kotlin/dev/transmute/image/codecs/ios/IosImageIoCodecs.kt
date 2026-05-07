@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.transmute.image.codecs.ios

import dev.transmute.common.PipelineContext
import dev.transmute.io.TSource
import dev.transmute.image.ImageFormat
import dev.transmute.image.*
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes
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
    ImageFormat.Jpeg,
    ImageFormat.Png,
    ImageFormat.Heif,
    ImageFormat.Heic,
    ImageFormat.Webp,
    ImageFormat.Gif,
    ImageFormat.Bmp,
    ImageFormat.Tiff,
    ImageFormat.Avif,
  )

  override suspend fun decode(source: TSource, options: ImageDecodeOptions, context: PipelineContext): ImageIR {
    val sourceBytes = source.readAll()
    val cfData = sourceBytes.usePinned { pinned ->
      CFDataCreate(null, pinned.addressOf(0).reinterpret(), sourceBytes.size.toLong())
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
    ImageFormat.Jpeg,
    ImageFormat.Png,
    ImageFormat.Heif,
    ImageFormat.Heic,
    ImageFormat.Avif,
    ImageFormat.Webp,
    ImageFormat.Tiff,
    ImageFormat.Gif,
    ImageFormat.Bmp,
  )

  override suspend fun encode(
    ir: ImageIR,
    format: ImageFormat,
    options: ImageEncodeOptions,
    context: PipelineContext,
  ): Bytes {
    val buffer = ir.buffer as? ByteArrayPixelBuffer
      ?: error("IosImageIoEncoder requires ByteArrayPixelBuffer")
    require(ir.pixelFormat == PixelFormat.RGBA_8888) { "Only RGBA_8888 is supported" }

    require(format in supportedFormats) { "Unsupported format $format" }
    val utiString = when (format) {
      ImageFormat.Jpeg -> "public.jpeg"
      ImageFormat.Png -> "public.png"
      ImageFormat.Heif -> "public.heif"
      ImageFormat.Heic -> "public.heic"
      ImageFormat.Avif -> "public.avif"
      ImageFormat.Webp -> "public.webp"
      ImageFormat.Tiff -> "public.tiff"
      ImageFormat.Gif -> "com.compuserve.gif"
      ImageFormat.Bmp -> "com.microsoft.bmp"
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

      val props = if (format == ImageFormat.Jpeg) {
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
      return ByteArray(len) { idx -> ptr[idx].toByte() }.asBytes()
    } finally {
      CGImageRelease(cgImage)
      CGColorSpaceRelease(colorSpace)
      CFRelease(mutableData)
      CFRelease(uti)
    }
  }
}
