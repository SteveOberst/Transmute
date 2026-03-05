package dev.transmute.gstreamer

import dev.transmute.common.PipelineContext
import dev.transmute.image.*
import dev.transmute.io.TSource
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * GStreamer-based image codecs for formats not supported by Java ImageIO.
 *
 * Handles HEIF, HEIC, and AVIF via GStreamer subprocess:
 * - **Decode**: GStreamer converts to PNG temp file -> ImageIO reads PNG
 * - **Encode**: ImageIO writes input to PNG temp file -> GStreamer converts to target format
 */

// ---------------------------------------------------------------------------
// GStreamer Image Decoder - HEIF, HEIC, AVIF
// ---------------------------------------------------------------------------

/**
 * Decodes HEIF, HEIC, and AVIF images using GStreamer on the desktop.
 *
 * Flow: ByteArray -> temp file -> gst-launch-1.0 (-> PNG) -> temp -> ImageIO -> ImageIR
 */
internal class GstImageDecoder : ImageDecoder {
  override val supportedFormats: Set<ImageFormat> = setOf(
    ImageFormat.Heif,
    ImageFormat.Heic,
    ImageFormat.Avif,
  )

  override suspend fun decode(source: TSource, options: ImageDecodeOptions, context: PipelineContext): ImageIR =
    withContext(Dispatchers.IO) {
      check(GStreamerResolver.available) { "GStreamer is not available" }

      val bytes = if (source is Bytes) source else Bytes(source.readAll())
      val format = ImageFormatDetector.detect(bytes)
      val ext = when (format) {
        ImageFormat.Heif -> "heif"
        ImageFormat.Heic -> "heic"
        ImageFormat.Avif -> "avif"
        else -> "bin"
      }

      val tmpIn = File.createTempFile("transmute_gst_img_in_", ".$ext")
      val tmpOut = File.createTempFile("transmute_gst_img_out_", ".png")
      try {
        tmpIn.writeBytes(bytes.data)

        val args = buildGstPipeline(
          "filesrc", "location=${tmpIn.absolutePath.toGstPath()}",
          "!", "decodebin",
          "!", "videoconvert",
          "!", "pngenc",
          "!", "filesink", "location=${tmpOut.absolutePath.toGstPath()}",
        )

        runGstLaunch(args)

        val pngBytes = tmpOut.readBytes()
        val img = ImageIO.read(ByteArrayInputStream(pngBytes))
          ?: error("ImageIO could not read GStreamer-decoded PNG")

        val converted = BufferedImage(img.width, img.height, BufferedImage.TYPE_4BYTE_ABGR)
        val g = converted.createGraphics()
        g.drawImage(img, 0, 0, null)
        g.dispose()

        val abgr = (converted.raster.dataBuffer as DataBufferByte).data
        val rgba = abgrToRgba(abgr)

        ImageIR(
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
      } finally {
        tmpIn.delete()
        tmpOut.delete()
      }
    }
}

// ---------------------------------------------------------------------------
// GStreamer Image Encoder - HEIF, AVIF
// ---------------------------------------------------------------------------

/**
 * Encodes images to HEIF and AVIF using GStreamer on the desktop.
 *
 * HEIC is treated as a synonym for HEIF (same container, both use HEVC).
 *
 * Flow: ImageIR -> PNG temp file (via ImageIO) -> gst-launch-1.0 -> target -> ByteArray
 *
 * Requires GStreamer plugins: `x265enc` for HEIF/HEIC, `av1enc` for AVIF.
 */
internal class GstImageEncoder : ImageEncoder {
  override val supportedFormats: Set<ImageFormat> = setOf(
    ImageFormat.Heif,
    ImageFormat.Heic,
    ImageFormat.Avif,
  )

  override suspend fun encode(ir: ImageIR, format: ImageFormat, options: ImageEncodeOptions, context: PipelineContext): Bytes =
    withContext(Dispatchers.IO) {
      check(GStreamerResolver.available) { "GStreamer is not available" }

      val buffer = ir.buffer as? ByteArrayPixelBuffer
        ?: error("GstImageEncoder requires ByteArrayPixelBuffer")
      require(ir.pixelFormat == PixelFormat.RGBA_8888) { "Only RGBA_8888 is supported" }
      require(format in supportedFormats) { "Unsupported format $format" }

      // Write input as PNG temp
      val tmpIn = File.createTempFile("transmute_gst_img_enc_in_", ".png")
      val ext = when (format) {
        ImageFormat.Avif -> "avif"
        else -> "heif"
      }
      val tmpOut = File.createTempFile("transmute_gst_img_enc_out_", ".$ext")

      try {
        // Create PNG from ImageIR
        val image = BufferedImage(ir.width, ir.height, BufferedImage.TYPE_4BYTE_ABGR)
        val abgr = rgbaToAbgr(buffer.data)
        val dest = (image.raster.dataBuffer as DataBufferByte).data
        System.arraycopy(abgr, 0, dest, 0, abgr.size)
        ImageIO.write(image, "png", tmpIn)

        val encoderPipeline = when (format) {
          ImageFormat.Avif -> listOf(
            "!", "pngdec",
            "!", "videoconvert",
            "!", "av1enc",
            "!", "isofmp4mux", "fragment-mode=first-moov-then-finalze",
          )
          else -> listOf( // HEIF / HEIC
            "!", "pngdec",
            "!", "videoconvert",
            "!", "x265enc",
            "!", "h265parse",
            "!", "mp4mux",
          )
        }

        val args = buildGstPipeline(
          "filesrc",
          "location=${tmpIn.absolutePath.toGstPath()}",
          *encoderPipeline.toTypedArray(),
          "!",
          "filesink",
          "location=${tmpOut.absolutePath.toGstPath()}",
        )

        runGstLaunch(args)
        tmpOut.readBytes().asBytes()
      } finally {
        tmpIn.delete()
        tmpOut.delete()
      }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun abgrToRgba(abgr: ByteArray): ByteArray {
  val out = ByteArray(abgr.size)
  var i = 0
  while (i < abgr.size) {
    out[i] = abgr[i + 3] // R
    out[i + 1] = abgr[i + 2] // G
    out[i + 2] = abgr[i + 1] // B
    out[i + 3] = abgr[i] // A
    i += 4
  }
  return out
}

private fun rgbaToAbgr(rgba: ByteArray): ByteArray {
  val out = ByteArray(rgba.size)
  var i = 0
  while (i < rgba.size) {
    out[i] = rgba[i + 3] // A
    out[i + 1] = rgba[i + 2] // B
    out[i + 2] = rgba[i + 1] // G
    out[i + 3] = rgba[i] // R
    i += 4
  }
  return out
}
