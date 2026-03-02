package dev.transmute.image

import dev.transmute.model.core.DecodeOptions

/**
 * Sealed hierarchy of image decoding options.
 *
 * Most decoders accept [CanonicalImageDecodeOptions]. Format-specific
 * subtypes can be added as needed (e.g. JPEG subsampling hints).
 */
sealed interface ImageDecodeOptions : DecodeOptions {
  /**
   * Optional set of input formats that are expected/accepted.
   *
   * - Empty set means "accept anything" (auto-detect for byte inputs).
   * - A single-entry set allows skipping detection in default byte decode pipelines.
   */
  val acceptedInputFormats: Set<ImageFormat>
}

/**
 * Format-agnostic decode options - no special configuration.
 */
data class CanonicalImageDecodeOptions(
  override val acceptedInputFormats: Set<ImageFormat> = emptySet(),
) : ImageDecodeOptions
