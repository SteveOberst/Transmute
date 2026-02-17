package dev.transmute.image

import dev.transmute.core.ImageFormat

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
   * the RIFF/ftyp box). Returns [ImageFormat.UNKNOWN] for unrecognised data.
   */
  fun detect(data: ByteArray): ImageFormat {
    ImageRegistries.installDefaultsIfEmpty()
    for (decoder in ImageRegistries.decoders.allDecoders) {
      decoder.sniff(data)?.let { return it }
    }
    return ImageFormat.UNKNOWN
  }

  /**
   * Returns `true` if the format supports transparency (alpha channel).
   * Important for choosing the right encoder — JPEG/HEIC don't support alpha.
   */
  fun supportsAlpha(format: ImageFormat): Boolean = when (format) {
    ImageFormat.PNG, ImageFormat.WEBP, ImageFormat.GIF, ImageFormat.AVIF -> true
    ImageFormat.JPEG, ImageFormat.HEIF, ImageFormat.HEIC, ImageFormat.BMP,
    ImageFormat.TIFF, ImageFormat.UNKNOWN -> false
  }

  /**
   * Returns `true` if the format is lossy by default.
   * Useful for deciding whether re-encoding saves space.
   */
  fun isLossy(format: ImageFormat): Boolean = when (format) {
    ImageFormat.JPEG, ImageFormat.HEIF, ImageFormat.HEIC, ImageFormat.AVIF -> true
    ImageFormat.PNG, ImageFormat.GIF, ImageFormat.BMP, ImageFormat.TIFF -> false
    ImageFormat.WEBP -> true // WebP supports both, but defaults to lossy
    ImageFormat.UNKNOWN -> false
  }
}
