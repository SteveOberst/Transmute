package dev.transmute.image

import dev.transmute.codec.MagicBytes
import dev.transmute.model.core.Bytes

/**
 * Detects image format from raw bytes using built-in magic byte detection.
 *
 * Covers all standard image formats (JPEG, PNG, GIF, BMP, TIFF, WebP,
 * HEIC, HEIF, AVIF). Returns [ImageFormat.Unknown] for unrecognised data.
 */
object ImageFormatDetector {

  /**
   * Detects the format from the first bytes of an image file.
   *
   * Requires at least 12 bytes for reliable detection (HEIF/WebP need
   * the RIFF/ftyp box). Returns [ImageFormat.Unknown] for unrecognised data.
   */
  fun detect(bytes: Bytes): ImageFormat = detectBuiltIn(bytes) ?: ImageFormat.Unknown

  /**
   * Built-in magic byte detection for all standard image formats.
   *
   * This ensures format identification works regardless of which decoders
   * are registered (e.g. HEIF/AVIF detection without the GStreamer module).
   */
  private fun detectBuiltIn(bytes: Bytes): ImageFormat? {
    if (bytes.size < 4) return null
    val b = bytes.data

    // JPEG: FF D8 FF
    if (b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte()) {
      return ImageFormat.Jpeg
    }

    // PNG: 89 50 4E 47
    if (b[0] == 0x89.toByte() && b[1] == 0x50.toByte() && b[2] == 0x4E.toByte() && b[3] == 0x47.toByte()) {
      return ImageFormat.Png
    }

    // GIF: "GIF8"
    if (b[0] == 0x47.toByte() && b[1] == 0x49.toByte() && b[2] == 0x46.toByte() && b[3] == 0x38.toByte()) {
      return ImageFormat.Gif
    }

    // BMP: "BM"
    if (b[0] == 0x42.toByte() && b[1] == 0x4D.toByte()) {
      return ImageFormat.Bmp
    }

    // TIFF: "II*\0" (little-endian) or "MM\0*" (big-endian)
    if ((b[0] == 0x49.toByte() && b[1] == 0x49.toByte() && b[2] == 0x2A.toByte() && b[3] == 0x00.toByte()) ||
      (b[0] == 0x4D.toByte() && b[1] == 0x4D.toByte() && b[2] == 0x00.toByte() && b[3] == 0x2A.toByte())
    ) {
      return ImageFormat.Tiff
    }

    // WebP: RIFF....WEBP
    if (MagicBytes.riffType(b) == "WEBP") return ImageFormat.Webp

    // ISO BMFF ftyp box -> HEIC / HEIF / AVIF
    MagicBytes.ftypBrand(b)?.let { brand ->
      return when {
        brand == "heic" || brand == "heix" || brand == "hevc" -> ImageFormat.Heic
        brand == "mif1" -> ImageFormat.Heif
        brand == "avif" || brand == "avis" -> ImageFormat.Avif
        else -> null
      }
    }

    return null
  }

  /**
   * Returns `true` if the format supports transparency (alpha channel).
   * Important for choosing the right encoder - JPEG/HEIC don't support alpha.
   */
  fun supportsAlpha(format: ImageFormat): Boolean = when (format) {
    ImageFormat.Png, ImageFormat.Webp, ImageFormat.Gif, ImageFormat.Avif -> true
    ImageFormat.Jpeg, ImageFormat.Heif, ImageFormat.Heic, ImageFormat.Bmp,
    ImageFormat.Tiff, ImageFormat.Unknown,
    -> false
  }

  /**
   * Returns `true` if the format is lossy by default.
   * Useful for deciding whether re-encoding saves space.
   */
  fun isLossy(format: ImageFormat): Boolean = when (format) {
    ImageFormat.Jpeg, ImageFormat.Heif, ImageFormat.Heic, ImageFormat.Avif -> true
    ImageFormat.Png, ImageFormat.Gif, ImageFormat.Bmp, ImageFormat.Tiff -> false
    ImageFormat.Webp -> true // WebP supports both, but defaults to lossy
    ImageFormat.Unknown -> false
  }
}
