package dev.transmute.image.codecs.jvm

import dev.transmute.core.TransmuteContext
import dev.transmute.core.FfmpegResolver
import dev.transmute.core.ImageFormat
import dev.transmute.image.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * FFmpeg-based image codecs for formats not supported by Java ImageIO.
 *
 * Handles HEIF, HEIC, and AVIF via FFmpeg subprocess:
 * - **Decode**: FFmpeg converts to PNG temp file → ImageIO reads PNG
 * - **Encode**: ImageIO writes input to PNG temp file → FFmpeg converts to target format
 */

// ---------------------------------------------------------------------------
// FFmpeg Image Decoder - HEIF, HEIC, AVIF
// ---------------------------------------------------------------------------

/**
 * Decodes HEIF, HEIC, and AVIF images using FFmpeg on the desktop.
 *
 * Flow: ByteArray → temp file → ffmpeg → PNG temp file → ImageIO → ImageIR
 */
class FfmpegImageDecoder : ImageDecoder {
  override val supportedFormats: Set<ImageFormat> = setOf(
    ImageFormat.HEIF,
    ImageFormat.HEIC,
    ImageFormat.AVIF,
  )

  override fun sniff(data: ByteArray): ImageFormat? {
    if (data.size < 12) return null
    // ISO BMFF ftyp box
    if (data[4] == 0x66.toByte() && data[5] == 0x74.toByte() &&
      data[6] == 0x79.toByte() && data[7] == 0x70.toByte()) {
      val brand = data.sliceArray(8 until 12).decodeToString()
      return when {
        brand == "heic" || brand == "heix" -> ImageFormat.HEIC
        brand == "mif1" || brand == "msf1" -> ImageFormat.HEIF
        brand == "hevc" || brand == "hevx" -> ImageFormat.HEIC
        brand == "avif" || brand == "avis" -> ImageFormat.AVIF
        else -> null
      }
    }
    return null
  }

  override suspend fun decode(
    source: ByteArray,
    options: ImageDecodeOptions,
    context: TransmuteContext,
  ): ImageIR = withContext(Dispatchers.IO) {
    check(FfmpegResolver.available) { "FFmpeg is not available" }

    val format = ImageFormatDetector.detect(source)
    val ext = when (format) {
      ImageFormat.HEIF -> "heif"
      ImageFormat.HEIC -> "heic"
      ImageFormat.AVIF -> "avif"
      else -> "bin"
    }

    val tmpIn = File.createTempFile("transmute_img_in_", ".$ext")
    val tmpOut = File.createTempFile("transmute_img_out_", ".png")
    try {
      tmpIn.writeBytes(source)

      val cmd = listOf(
        FfmpegResolver.ffmpegPath,
        "-y", "-loglevel", "error",
        "-i", tmpIn.absolutePath,
        "-frames:v", "1",
        "-f", "image2",
        tmpOut.absolutePath,
      )

      val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
      val output = process.inputStream.bufferedReader().readText()
      check(process.waitFor() == 0) {
        "FFmpeg image decode failed: ${output.takeLast(500)}"
      }

      val pngBytes = tmpOut.readBytes()
      val img = ImageIO.read(ByteArrayInputStream(pngBytes))
        ?: error("ImageIO could not read FFmpeg-decoded PNG")

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
// FFmpeg Image Encoder - HEIF, AVIF
// ---------------------------------------------------------------------------

/**
 * Encodes images to HEIF and AVIF using FFmpeg on the desktop.
 *
 * HEIC is treated as a synonym for HEIF (same container, both use HEVC).
 *
 * Flow: ImageIR → PNG temp file (via ImageIO) → ffmpeg → target format → ByteArray
 */
class FfmpegImageEncoder : ImageEncoder {
  override val supportedFormats: Set<ImageFormat> = setOf(
    ImageFormat.HEIF,
    ImageFormat.HEIC,
    ImageFormat.AVIF,
  )

  override suspend fun encode(
    ir: ImageIR,
    format: ImageFormat,
    options: ImageEncodeOptions,
    context: TransmuteContext,
  ): ByteArray = withContext(Dispatchers.IO) {
    check(FfmpegResolver.available) { "FFmpeg is not available" }

    val buffer = ir.buffer as? ByteArrayPixelBuffer
      ?: error("FfmpegImageEncoder requires ByteArrayPixelBuffer")
    require(ir.pixelFormat == PixelFormat.RGBA_8888) { "Only RGBA_8888 is supported" }

    require(format in supportedFormats) { "Unsupported format $format" }

    // FFmpeg encoder options can be added later; for now we use a stable default.
    val quality = 0.85f

    // Write input as PNG temp
    val tmpIn = File.createTempFile("transmute_img_enc_in_", ".png")
    val ext = when (format) {
      ImageFormat.AVIF -> "avif"
      else -> "heif"
    }
    val tmpOut = File.createTempFile("transmute_img_enc_out_", ".$ext")

    try {
      // Create PNG from ImageIR
      val image = BufferedImage(ir.width, ir.height, BufferedImage.TYPE_4BYTE_ABGR)
      val abgr = rgbaToAbgr(buffer.data)
      val dest = (image.raster.dataBuffer as DataBufferByte).data
      System.arraycopy(abgr, 0, dest, 0, abgr.size)
      ImageIO.write(image, "png", tmpIn)

      // Map quality to CRF (lower CRF = higher quality)
      // quality 1.0 → CRF 10, quality 0.0 → CRF 51
      val crf = (51 - (quality * 41).toInt()).coerceIn(0, 51)

      val cmd = buildList {
        add(FfmpegResolver.ffmpegPath)
        add("-y"); add("-loglevel"); add("error")
        add("-i"); add(tmpIn.absolutePath)

        when (format) {
          ImageFormat.AVIF -> {
            add("-c:v"); add("libaom-av1")
            add("-crf"); add(crf.toString())
            add("-still-picture"); add("1")
            add("-f"); add("avif")
          }

          else -> { // HEIF / HEIC
            add("-c:v"); add("libx265")
            add("-crf"); add(crf.toString())
            add("-tag:v"); add("hvc1")
            add("-f"); add("hevc")
          }
        }

        add(tmpOut.absolutePath)
      }

      val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
      val output = process.inputStream.bufferedReader().readText()
      check(process.waitFor() == 0) {
        "FFmpeg image encode to $format failed: ${output.takeLast(500)}"
      }

      tmpOut.readBytes()
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
    out[i] = abgr[i + 3]     // R
    out[i + 1] = abgr[i + 2] // G
    out[i + 2] = abgr[i + 1] // B
    out[i + 3] = abgr[i]     // A
    i += 4
  }
  return out
}

private fun rgbaToAbgr(rgba: ByteArray): ByteArray {
  val out = ByteArray(rgba.size)
  var i = 0
  while (i < rgba.size) {
    out[i] = rgba[i + 3]     // A
    out[i + 1] = rgba[i + 2] // B
    out[i + 2] = rgba[i + 1] // G
    out[i + 3] = rgba[i]     // R
    i += 4
  }
  return out
}
