package dev.transmute.image

import dev.transmute.core.Bytes

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
    ImageRegistries.installDefaultsIfEmpty()
    for (decoder in ImageRegistries.decoders.allDecoders) {
      decoder.sniff(data)?.let { return it }
    }
    return ImageFormat.Unknown
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
