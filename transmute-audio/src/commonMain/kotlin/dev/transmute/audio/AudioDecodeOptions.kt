package dev.transmute.audio

import dev.transmute.core.DecodeOptions
import dev.transmute.core.AudioFormat

/**
 * Sealed hierarchy of audio decoding options.
 *
 * Most decoders accept [DefaultAudioDecodeOptions].
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
data class DefaultAudioDecodeOptions(
  override val acceptedInputFormats: Set<AudioFormat> = emptySet(),
) : AudioDecodeOptions
