@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package dev.transmute.gstreamer

import dev.transmute.common.PipelineContext
import dev.transmute.image.AlphaSemantics
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.ColorInfo
import dev.transmute.image.ImageDecodeOptions
import dev.transmute.image.ImageEncodeOptions
import dev.transmute.image.ImageFormat
import dev.transmute.image.ImageFormatDetector
import dev.transmute.image.ImageIR
import dev.transmute.image.ImageMetadata
import dev.transmute.image.Orientation
import dev.transmute.image.PixelFormat
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextGetData
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGBitmapByteOrder32Big
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.ImageIO.CGImageDestinationAddImage
import platform.ImageIO.CGImageDestinationCreateWithData
import platform.ImageIO.CGImageDestinationFinalize
import platform.ImageIO.CGImageSourceCreateImageAtIndex
import platform.ImageIO.CGImageSourceCreateWithData

/**
 * Image encode / decode engine for iOS via GStreamer cinterop bridge.
 *
 * Uses CoreGraphics for the PNG intermediate step:
 * - **Decode**: GStreamer -> PNG file -> [CGImageSourceCreateWithData] -> RGBA_8888 -> [ImageIR]
 * - **Encode**: [ImageIR] -> RGBA -> PNG via [CGImageDestinationCreateWithData] ->
 *   GStreamer pipeline -> target format
 */
internal object GStreamerIosImageEngine {

    val available: Boolean get() = GStreamerIosBridge.available

    // -- Decode ---

    suspend fun decode(
        source: Bytes,
        options: ImageDecodeOptions,
        context: PipelineContext,
    ): ImageIR = withContext(Dispatchers.Default) {
        check(available) { "GStreamer is not available on this device" }

        val format = ImageFormatDetector.detect(source)
        val ext = when (format) {
            ImageFormat.Heif -> "heif"
            ImageFormat.Heic -> "heic"
            ImageFormat.Avif -> "avif"
            else -> "bin"
        }

        val tmpDir = NSTemporaryDirectory()
        val inPath = "${tmpDir}transmute_gst_img_in.$ext"
        val outPath = "${tmpDir}transmute_gst_img_out.png"

        try {
            source.data.writeToTmpFile(inPath)

            val desc = buildIosPipelineDesc(
                "filesrc location=$inPath",
                "! decodebin",
                "! videoconvert",
                "! pngenc",
                "! filesink location=$outPath",
            )
            GStreamerIosBridge.runPipelineChecked(desc)

            val pngBytes = readTmpFile(outPath)
            decodePngToImageIR(pngBytes)
        } finally {
            deleteTmpFile(inPath)
            deleteTmpFile(outPath)
        }
    }

    // -- Encode ---

    suspend fun encode(
        ir: ImageIR,
        format: ImageFormat,
        options: ImageEncodeOptions,
        context: PipelineContext,
    ): Bytes = withContext(Dispatchers.Default) {
        check(available) { "GStreamer is not available on this device" }

        val buffer = ir.buffer as? ByteArrayPixelBuffer
            ?: error("GStreamerIosImageEngine requires ByteArrayPixelBuffer")
        require(ir.pixelFormat == PixelFormat.RGBA_8888) { "Only RGBA_8888 is supported" }

        val tmpDir = NSTemporaryDirectory()
        val inPath = "${tmpDir}transmute_gst_img_enc_in.png"
        val ext = when (format) {
            ImageFormat.Avif -> "avif"
            else -> "heif"
        }
        val outPath = "${tmpDir}transmute_gst_img_enc_out.$ext"

        try {
            // Encode ImageIR to PNG via CoreGraphics
            val pngBytes = encodeImageIRToPng(ir, buffer)
            pngBytes.writeToTmpFile(inPath)

            val encoderPipeline = when (format) {
                ImageFormat.Avif -> "! pngdec ! videoconvert ! av1enc ! isofmp4mux"
                else -> "! pngdec ! videoconvert ! x265enc ! h265parse ! mp4mux"
            }
            val desc = buildIosPipelineDesc(
                "filesrc location=$inPath",
                encoderPipeline,
                "! filesink location=$outPath",
            )
            GStreamerIosBridge.runPipelineChecked(desc)
            readTmpFile(outPath).asBytes()
        } finally {
            deleteTmpFile(inPath)
            deleteTmpFile(outPath)
        }
    }
}

// ---
// CoreGraphics PNG  ImageIR helpers
// ---

/**
 * Decode PNG bytes to [ImageIR] using CoreGraphics [CGImageSource].
 */
private fun decodePngToImageIR(pngBytes: ByteArray): ImageIR {
    val nsData = pngBytes.toNSData()
    val cfData = nsDataToCFData(nsData)
        ?: error("Failed to create CFData from PNG bytes")

    val imageSource = CGImageSourceCreateWithData(cfData, null)
        ?: run {
            CFRelease(cfData)
            error("CGImageSourceCreateWithData failed for PNG data")
        }

    val cgImage = CGImageSourceCreateImageAtIndex(imageSource, 0u, null)
        ?: run {
            CFRelease(imageSource)
            CFRelease(cfData)
            error("CGImageSourceCreateImageAtIndex failed")
        }

    val w = CGImageGetWidth(cgImage).toInt()
    val h = CGImageGetHeight(cgImage).toInt()
    val bytesPerRow = w * 4
    val colorSpace = CGColorSpaceCreateDeviceRGB()

    val bitmapInfo = kCGBitmapByteOrder32Big or CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value.toUInt()

    val bitmapCtx = CGBitmapContextCreate(
        data = null,
        width = w.convert(),
        height = h.convert(),
        bitsPerComponent = 8u,
        bytesPerRow = bytesPerRow.convert(),
        space = colorSpace,
        bitmapInfo = bitmapInfo,
    ) ?: run {
        CGImageRelease(cgImage)
        CFRelease(imageSource)
        CFRelease(cfData)
        error("CGBitmapContextCreate failed")
    }

    CGContextDrawImage(bitmapCtx, CGRectMake(0.0, 0.0, w.toDouble(), h.toDouble()), cgImage)

    val ptr = CGBitmapContextGetData(bitmapCtx)
        ?: run {
            CGContextRelease(bitmapCtx)
            CGImageRelease(cgImage)
            CFRelease(imageSource)
            CFRelease(cfData)
            error("CGBitmapContextGetData returned null")
        }

    val rgba = ByteArray(w * h * 4)
    val bytePtr = ptr.reinterpret<kotlinx.cinterop.ByteVar>()
    for (i in rgba.indices) {
        rgba[i] = bytePtr[i]
    }

    CGContextRelease(bitmapCtx)
    CGImageRelease(cgImage)
    CFRelease(imageSource)
    CFRelease(cfData)

    return ImageIR(
        buffer = ByteArrayPixelBuffer(rgba),
        width = w,
        height = h,
        stride = bytesPerRow,
        pixelFormat = PixelFormat.RGBA_8888,
        alphaSemantics = AlphaSemantics.STRAIGHT,
        colorInfo = ColorInfo(),
        orientation = Orientation.NORMAL,
        metadata = ImageMetadata(),
    )
}

/**
 * Encode [ImageIR] to PNG bytes using CoreGraphics [CGImageDestination].
 */
private fun encodeImageIRToPng(ir: ImageIR, buffer: ByteArrayPixelBuffer): ByteArray {
    val w = ir.width
    val h = ir.height
    val bytesPerRow = w * 4
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    val bitmapInfo = kCGBitmapByteOrder32Big or CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value.toUInt()

    val rgba = buffer.data
    val bitmapCtx = rgba.usePinned { pinned ->
        CGBitmapContextCreate(
            data = pinned.addressOf(0),
            width = w.convert(),
            height = h.convert(),
            bitsPerComponent = 8u,
            bytesPerRow = bytesPerRow.convert(),
            space = colorSpace,
            bitmapInfo = bitmapInfo,
        )
    } ?: error("CGBitmapContextCreate failed for PNG encoding")

    val cgImage = platform.CoreGraphics.CGBitmapContextCreateImage(bitmapCtx)
        ?: run {
            CGContextRelease(bitmapCtx)
            error("CGBitmapContextCreateImage failed")
        }

    val mutableData = platform.CoreFoundation.CFDataCreateMutable(kCFAllocatorDefault, 0)
        ?: run {
            CGImageRelease(cgImage)
            CGContextRelease(bitmapCtx)
            error("CFDataCreateMutable failed")
        }

    val dest = CGImageDestinationCreateWithData(
        mutableData,
        platform.UniformTypeIdentifiers.UTTypePNG.identifier as platform.CoreFoundation.CFStringRef,
        1u,
        null,
    ) ?: run {
        CFRelease(mutableData)
        CGImageRelease(cgImage)
        CGContextRelease(bitmapCtx)
        error("CGImageDestinationCreateWithData failed")
    }

    CGImageDestinationAddImage(dest, cgImage, null)
    CGImageDestinationFinalize(dest)

    val length = CFDataGetLength(mutableData).toInt()
    val ptr = CFDataGetBytePtr(mutableData)
        ?: run {
            CFRelease(dest)
            CFRelease(mutableData)
            CGImageRelease(cgImage)
            CGContextRelease(bitmapCtx)
            error("CFDataGetBytePtr returned null")
        }

    val result = ByteArray(length)
    val bytePtr = ptr.reinterpret<kotlinx.cinterop.ByteVar>()
    for (i in result.indices) {
        result[i] = bytePtr[i]
    }

    CFRelease(dest)
    CFRelease(mutableData)
    CGImageRelease(cgImage)
    CGContextRelease(bitmapCtx)

    return result
}

/**
 * Convert an NSData to a CFDataRef. Caller must CFRelease when done.
 */
private fun nsDataToCFData(nsData: NSData): CFDataRef? {
    val length = nsData.length.toInt()
    if (length == 0) return null
    val bytes = nsData.toByteArray()
    return bytes.usePinned { pinned ->
        CFDataCreate(kCFAllocatorDefault, pinned.addressOf(0).reinterpret(), length.convert())
    }
}
