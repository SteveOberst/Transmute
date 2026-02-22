package dev.transmute.audio

import dev.transmute.core.AudioFormat
import dev.transmute.core.EncodeOptions
import dev.transmute.core.MetadataPolicy
import dev.transmute.core.OutputFormat

/**
 * Sealed hierarchy of audio encoding options.
 *
 * Most audio formats use [CanonicalAudioEncodeOptions] until
 * format-specific knobs (bitrate, sample rate, etc.) are needed.
 */
sealed interface AudioEncodeOptions : EncodeOptions {
  /**
   * Controls whether metadata (tags, artwork, etc.) should be preserved or stripped during encoding.
   *
   * This is an *encoding* concern; it is not applied as a transform step.
   */
  val metadataPolicy: MetadataPolicy

  /**
   * Output-format selection for *dynamic-output* transmuters.
   *
   * Use [OutputFormat.ORIGINAL] to fall back to the input format.
   * Fixed-output transmuters ignore this value (and may validate it).
   */
  val outputFormat: OutputFormat<AudioFormat>
}

/**
 * Default encode options for audio formats with no special configuration.
 */
data class CanonicalAudioEncodeOptions(
  override val metadataPolicy: MetadataPolicy = MetadataPolicy.STRIP_ALL,
  override val outputFormat: OutputFormat<AudioFormat> = OutputFormat.ORIGINAL,
) : AudioEncodeOptions
