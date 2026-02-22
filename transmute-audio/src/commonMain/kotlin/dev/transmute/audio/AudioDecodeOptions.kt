package dev.transmute.audio

import dev.transmute.core.DecodeOptions

/**
 * Sealed hierarchy of audio decoding options.
 *
 * Most decoders accept [CanonicalAudioDecodeOptions].
 */
sealed interface AudioDecodeOptions : DecodeOptions {
  /**
   * Optional set of input formats that are expected/accepted.
   *
   * - Empty set means "accept anything" (auto-detect for byte inputs).
   * - A single-entry set allows skipping detection in default byte decode pipelines.
   */
  val acceptedInputFormats: Set<AudioFormat>
}

/**
 * Default decode options — no special configuration.
 */
data class CanonicalAudioDecodeOptions(
  override val acceptedInputFormats: Set<AudioFormat> = emptySet(),
) : AudioDecodeOptions
