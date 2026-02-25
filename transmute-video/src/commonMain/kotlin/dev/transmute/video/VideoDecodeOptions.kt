package dev.transmute.video

import dev.transmute.model.core.DecodeOptions
import dev.transmute.codec.DecodeRange

/**
 * Sealed hierarchy of video decoding options.
 *
 * Most decoders accept [CanonicalVideoDecodeOptions].
 */
sealed interface VideoDecodeOptions : DecodeOptions {
  /**
   * Optional set of input formats that are expected/accepted.
   *
   * - Empty set means "accept anything" (auto-detect for byte inputs).
   * - A single-entry set allows skipping detection in default byte decode pipelines.
   */
  val acceptedInputFormats: Set<VideoFormat>

  val decodeRange: DecodeRange?
}

/**
 * Default decode options — no special configuration.
 */
data class CanonicalVideoDecodeOptions(
  override val acceptedInputFormats: Set<VideoFormat> = emptySet(),
  override val decodeRange: DecodeRange? = null,
) : VideoDecodeOptions
