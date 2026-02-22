package dev.transmute.image

import dev.transmute.core.EncodeOptions
import dev.transmute.core.ImageFormat
import dev.transmute.core.MetadataPolicy

/**
 * Sealed hierarchy of image encoding options.
 *
 * Each image format with meaningful encoder knobs gets its own subtype.
 * Encoders receive [ImageEncodeOptions] and pattern-match on subtypes
 * to extract format-specific settings, falling back to sensible defaults
 * when the options type doesn't match the output format.
 */
sealed interface ImageEncodeOptions : EncodeOptions {
  /**
   * Controls whether metadata (EXIF, XMP, GPS, etc.) should be preserved or stripped during encoding.
   *
   * This is an *encoding* concern; it is not applied as a transform step.
   */
  val metadataPolicy: MetadataPolicy

  /**
   * Optional output-format override for *dynamic-output* transmuters.
   *
   * When `null`, the default behavior is "same as input" (or whatever the encode pipeline selects).
   * Fixed-output transmuters ignore this value.
   */
  val outputFormat: ImageFormat?

  companion object {
    /**
     * Returns sensible default encode options for [format].
     *
     * Useful when the caller specifies an output format but no explicit options.
     */
    fun defaultFor(format: ImageFormat): ImageEncodeOptions = when (format) {
      ImageFormat.JPEG -> JpegEncodeOptions()
      ImageFormat.PNG -> PngEncodeOptions()
      ImageFormat.WEBP -> WebPEncodeOptions()
      ImageFormat.HEIF, ImageFormat.HEIC, ImageFormat.AVIF -> HeifEncodeOptions()
      else -> DefaultImageEncodeOptions()
    }
  }
}

/**
 * JPEG-specific encoding options.
 *
 * @property quality Compression quality in `[0, 1]`. 0.85 balances visual
 *   quality against file size for most photographic content.
 */
data class JpegEncodeOptions(
  val quality: Float = 0.85f,
  override val metadataPolicy: MetadataPolicy = MetadataPolicy.STRIP_ALL,
) : ImageEncodeOptions {
  override val outputFormat: ImageFormat = ImageFormat.JPEG

  init {
    require(quality in 0f..1f) { "quality must be in [0, 1], was $quality" }
  }
}

/**
 * PNG-specific encoding options.
 *
 * PNG is lossless; the main knob is compression effort.
 *
 * @property compressionLevel zlib compression level 0–9 (0 = none, 9 = max).
 */
data class PngEncodeOptions(
  val compressionLevel: Int = 6,
  override val metadataPolicy: MetadataPolicy = MetadataPolicy.STRIP_ALL,
) : ImageEncodeOptions {
  override val outputFormat: ImageFormat = ImageFormat.PNG

  init {
    require(compressionLevel in 0..9) { "compressionLevel must be in [0, 9], was $compressionLevel" }
  }
}

/**
 * WebP-specific encoding options.
 *
 * @property quality Lossy compression quality in `[0, 1]`.
 * @property lossless When `true`, encode as lossless WebP (ignores [quality]).
 */
data class WebPEncodeOptions(
  val quality: Float = 0.80f,
  val lossless: Boolean = false,
  override val metadataPolicy: MetadataPolicy = MetadataPolicy.STRIP_ALL,
) : ImageEncodeOptions {
  override val outputFormat: ImageFormat = ImageFormat.WEBP

  init {
    require(quality in 0f..1f) { "quality must be in [0, 1], was $quality" }
  }
}

/**
 * HEIF/HEIC/AVIF encoding options.
 *
 * @property quality Lossy compression quality in `[0, 1]`.
 */
data class HeifEncodeOptions(
  val quality: Float = 0.80f,
  override val metadataPolicy: MetadataPolicy = MetadataPolicy.STRIP_ALL,
  override val outputFormat: ImageFormat = ImageFormat.HEIF,
) : ImageEncodeOptions {
  init {
    require(quality in 0f..1f) { "quality must be in [0, 1], was $quality" }
    require(outputFormat == ImageFormat.HEIF || outputFormat == ImageFormat.HEIC || outputFormat == ImageFormat.AVIF) {
      "outputFormat must be HEIF, HEIC, or AVIF, was $outputFormat"
    }
  }
}

/**
 * Fallback options for image formats with no format-specific knobs
 * (BMP, GIF, TIFF, etc.).
 */
data class DefaultImageEncodeOptions(
  override val metadataPolicy: MetadataPolicy = MetadataPolicy.STRIP_ALL,
  override val outputFormat: ImageFormat? = null,
) : ImageEncodeOptions

/**
 * Resolves [this] into the effective options to use for [outputFormat].
 *
 * In particular, [DefaultImageEncodeOptions] acts as a sentinel meaning
 * "use the library defaults for the chosen output format", while still
 * allowing callers to specify shared concerns like [metadataPolicy].
 */
fun ImageEncodeOptions.resolveFor(outputFormat: ImageFormat): ImageEncodeOptions = when (this) {
  is DefaultImageEncodeOptions ->
    ImageEncodeOptions.defaultFor(outputFormat).withMetadataPolicy(metadataPolicy)

  else -> this
}

/** Returns an equivalent options instance with [metadataPolicy] applied. */
fun ImageEncodeOptions.withMetadataPolicy(metadataPolicy: MetadataPolicy): ImageEncodeOptions = when (this) {
  is JpegEncodeOptions -> copy(metadataPolicy = metadataPolicy)
  is PngEncodeOptions -> copy(metadataPolicy = metadataPolicy)
  is WebPEncodeOptions -> copy(metadataPolicy = metadataPolicy)
  is HeifEncodeOptions -> copy(metadataPolicy = metadataPolicy)
  is DefaultImageEncodeOptions -> copy(metadataPolicy = metadataPolicy)
}

