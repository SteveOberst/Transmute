package dev.transmute.image

import dev.transmute.core.DecodeOptions
import dev.transmute.core.ImageFormat

/**
 * Sealed hierarchy of image decoding options.
 *
 * Most decoders accept [DefaultImageDecodeOptions]. Format-specific
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
 * Default decode options — no special configuration.
 */
data class DefaultImageDecodeOptions(
  override val acceptedInputFormats: Set<ImageFormat> = emptySet(),
) : ImageDecodeOptions
