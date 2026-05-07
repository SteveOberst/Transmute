package dev.transmute.libheif

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
 * libheif-based image codecs for HEIF, HEIC, and AVIF on Desktop/JVM.
 *
 * Uses libheif CLI tools as subprocesses:
 * - **Decode**: `heif-dec` (or `heif-convert`) converts to PNG -> ImageIO reads PNG
 * - **Encode**: ImageIO writes input as PNG -> `heif-enc` converts to target format
 *
 * Both Windows and Linux are supported. The resolver (see [LibHeifResolver])
 * handles locating the correct binaries.
 */

// ---
// LibHeif Image Decoder -- HEIF, HEIC, AVIF
// ---

/**
 * Decodes HEIF, HEIC, and AVIF images using libheif CLI tools on the desktop.
 *
 * Flow: ByteArray -> temp file -> heif-dec/heif-convert -> PNG temp -> ImageIO -> ImageIR
 */
internal class LibHeifImageDecoder : ImageDecoder {
  override val supportedFormats: Set<ImageFormat> = setOf(
    ImageFormat.Heif,
    ImageFormat.Heic,
    ImageFormat.Avif,
  )

  override suspend fun decode(source: TSource, options: ImageDecodeOptions, context: PipelineContext): ImageIR =
    withContext(Dispatchers.IO) {
      check(LibHeifResolver.available) { "libheif is not available" }

      val sourceData = source.readAll()
      val format = ImageFormatDetector.detect(Bytes(sourceData))
      val ext = when (format) {
        ImageFormat.Heif -> "heif"
        ImageFormat.Heic -> "heic"
        ImageFormat.Avif -> "avif"
        else -> "bin"
      }

      val tmpIn = File.createTempFile("transmute_libheif_dec_in_", ".$ext")
      val tmpOut = File.createTempFile("transmute_libheif_dec_out_", ".png")
      try {
        tmpIn.writeBytes(sourceData)

        // Build the decode command.
        //   heif-dec (1.19+): heif-dec input.heic -o output.png
        //   heif-convert (older): heif-convert input.heic output.png
        val decoderPath = LibHeifResolver.decoderPath
        val isLegacy = decoderPath.contains("heif-convert")

        val args = if (isLegacy) {
          listOf(decoderPath, tmpIn.absolutePath, tmpOut.absolutePath)
        } else {
          listOf(decoderPath, tmpIn.absolutePath, "-o", tmpOut.absolutePath)
        }

        runLibHeifTool(args)

        val pngBytes = tmpOut.readBytes()
        check(pngBytes.isNotEmpty()) { "libheif produced empty output for ${format.label} decode" }

        val img = ImageIO.read(ByteArrayInputStream(pngBytes))
          ?: error("ImageIO could not read libheif-decoded PNG")

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

// ---
// LibHeif Image Encoder -- HEIF, HEIC, AVIF
// ---

/**
 * Encodes images to HEIF, HEIC, and AVIF using libheif CLI tools on the desktop.
 *
 * HEIC is treated as a synonym for HEIF (same container, both use HEVC codec).
 *
 * Flow: ImageIR -> PNG temp file (via ImageIO) -> heif-enc -> target -> ByteArray
 *
 * `heif-enc` supports:
 * - HEIF/HEIC output via the x265 (HEVC) encoder
 * - AVIF output via the AOM (AV1) encoder, with `--avif` flag
 */
internal class LibHeifImageEncoder : ImageEncoder {
  override val supportedFormats: Set<ImageFormat> = setOf(
    ImageFormat.Heif,
    ImageFormat.Heic,
    ImageFormat.Avif,
  )

  override suspend fun encode(ir: ImageIR, format: ImageFormat, options: ImageEncodeOptions, context: PipelineContext): Bytes =
    withContext(Dispatchers.IO) {
      check(LibHeifResolver.encoderAvailable) { "libheif encoder (heif-enc) is not available" }

      val buffer = ir.buffer as? ByteArrayPixelBuffer
        ?: error("LibHeifImageEncoder requires ByteArrayPixelBuffer")
      require(ir.pixelFormat == PixelFormat.RGBA_8888) { "Only RGBA_8888 is supported" }
      require(format in supportedFormats) { "Unsupported format $format" }

      // Write input as PNG temp
      val tmpIn = File.createTempFile("transmute_libheif_enc_in_", ".png")
      val ext = when (format) {
        ImageFormat.Avif -> "avif"
        else -> "heif"
      }
      val tmpOut = File.createTempFile("transmute_libheif_enc_out_", ".$ext")

      try {
        // Create PNG from ImageIR
        val image = BufferedImage(ir.width, ir.height, BufferedImage.TYPE_4BYTE_ABGR)
        val abgr = rgbaToAbgr(buffer.data)
        val dest = (image.raster.dataBuffer as DataBufferByte).data
        System.arraycopy(abgr, 0, dest, 0, abgr.size)
        ImageIO.write(image, "png", tmpIn)

        // Build the encode command.
        //   heif-enc [-q <quality>] [--avif] -o output.heif input.png
        val quality = when (options) {
          is HeifEncodeOptions -> (options.quality * 100).toInt().coerceIn(0, 100)
          else -> 80
        }

        val args = buildList {
          add(LibHeifResolver.encoderPath!!)
          add("-q")
          add(quality.toString())
          if (format == ImageFormat.Avif) {
            add("--avif")
          }
          add("-o")
          add(tmpOut.absolutePath)
          add(tmpIn.absolutePath)
        }

        runLibHeifTool(args)

        val outputBytes = tmpOut.readBytes()
        check(outputBytes.isNotEmpty()) { "libheif produced empty output for ${format.label} encode" }
        outputBytes.asBytes()
      } finally {
        tmpIn.delete()
        tmpOut.delete()
      }
    }
}

// ---
// Helpers
// ---

/** Run a libheif CLI tool and check for errors. */
internal fun runLibHeifTool(args: List<String>) {
  val pb = ProcessBuilder(args).redirectErrorStream(true)
  // Configure env/working-dir for bundled/provisioned binaries (Unix shared libs under ../lib).
  val binary = args.firstOrNull()
  if (binary != null) {
    LibHeifResolver.configureLibHeifProcess(pb, binary)
  }
  val process = pb.start()
  // Register a JVM shutdown hook so the child process is force-killed if the
  // JVM exits before waitFor() returns (e.g. test timeout, Ctrl-C).
  val hook = Thread({ process.destroyForcibly() }, "libheif-cleanup")
  Runtime.getRuntime().addShutdownHook(hook)
  try {
    val output = process.inputStream.bufferedReader().readText()
    check(process.waitFor() == 0) {
      "libheif tool failed (exit ${process.exitValue()}): ${output.takeLast(500)}"
    }
  } finally {
    runCatching { Runtime.getRuntime().removeShutdownHook(hook) }
    process.destroyForcibly()
  }
}

/** Convert ABGR (Java's TYPE_4BYTE_ABGR) to RGBA pixel layout. */
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

/** Convert RGBA to ABGR (Java's TYPE_4BYTE_ABGR) pixel layout. */
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
