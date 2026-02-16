@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.transmute.image.codecs.ios

import dev.transmute.core.ConversionContext
import dev.transmute.core.ImageFormat
import dev.transmute.image.*
import kotlinx.cinterop.*
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataCreateMutable
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreGraphics.*
import platform.ImageIO.*

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

  override suspend fun decode(source: ByteArray, context: ConversionContext): ImageIR {
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

  override suspend fun encode(ir: ImageIR, context: ConversionContext): ByteArray {
    val buffer = ir.buffer as? ByteArrayPixelBuffer
      ?: error("IosImageIoEncoder requires ByteArrayPixelBuffer")
    require(ir.pixelFormat == PixelFormat.RGBA_8888) { "Only RGBA_8888 is supported" }

    val format = (context.scratchpad["image.output.format"] as? ImageFormat) ?: ImageFormat.PNG
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

      CGImageDestinationAddImage(dest, cgImage, null)
      val ok = CGImageDestinationFinalize(dest)
      if (!ok) error("CGImageDestinationFinalize failed")

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
