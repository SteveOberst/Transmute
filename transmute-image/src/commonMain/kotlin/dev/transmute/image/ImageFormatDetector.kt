package dev.transmute.image

import dev.transmute.model.core.Bytes

/**
 * Detects image format from raw bytes via registered decoders/codecs.
 *
 * The detector iterates registered decoders and returns the first non-null
 * result from `sniff(data)`.
 */
object ImageFormatDetector {

  /**
   * Sniffs the format from the first bytes of an image file.
   *
   * Requires at least 12 bytes for reliable detection (HEIF/WebP need
   * the RIFF/ftyp box). Returns [ImageFormat.Unknown] for unrecognised data.
   */
  fun detect(data: Bytes): ImageFormat {
    // Check built-in magic byte patterns first (format-aware, decoder-independent).
    detectBuiltIn(data)?.let { return it }

    // Fall back to registered decoders for additional/custom formats.
    ImageRegistries.installDefaultsIfEmpty()
    for (decoder in ImageRegistries.decoders.allDecoders) {
      decoder.sniff(data)?.let { return it }
    }
    return ImageFormat.Unknown
  }

  /**
   * Built-in magic byte detection for all standard image formats.
   *
   * This ensures format identification works regardless of which decoders
   * are registered (e.g. HEIF/AVIF detection without the GStreamer module).
   */
  private fun detectBuiltIn(data: Bytes): ImageFormat? {
    if (data.size < 4) return null
    val b = data.data

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
        (b[0] == 0x4D.toByte() && b[1] == 0x4D.toByte() && b[2] == 0x00.toByte() && b[3] == 0x2A.toByte())) {
      return ImageFormat.Tiff
    }

    // WebP: RIFF....WEBP
    if (data.size >= 12 &&
        b[0] == 0x52.toByte() && b[1] == 0x49.toByte() && b[2] == 0x46.toByte() && b[3] == 0x46.toByte() &&
        b[8] == 0x57.toByte() && b[9] == 0x45.toByte() && b[10] == 0x42.toByte() && b[11] == 0x50.toByte()) {
      return ImageFormat.Webp
    }

    // ISO BMFF ftyp box -> HEIC / HEIF / AVIF
    if (data.size >= 12 &&
        b[4] == 0x66.toByte() && b[5] == 0x74.toByte() && b[6] == 0x79.toByte() && b[7] == 0x70.toByte()) {
      val brand = String(byteArrayOf(b[8], b[9], b[10], b[11]))
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
    ImageFormat.Tiff, ImageFormat.Unknown -> false
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
