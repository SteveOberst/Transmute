package dev.transmute.audio

import dev.transmute.core.AudioFormat
import dev.transmute.core.EncodeOptions
import dev.transmute.core.MetadataPolicy

/**
 * Sealed hierarchy of audio encoding options.
 *
 * Most audio formats use [DefaultAudioEncodeOptions] until
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
   * Optional output-format override for *dynamic-output* transmuters.
   *
   * When `null`, the default behavior is "same as input" (or whatever the encode pipeline selects).
   * Fixed-output transmuters ignore this value.
   */
  val outputFormat: AudioFormat?
}

/**
 * Default encode options for audio formats with no special configuration.
 */
data class DefaultAudioEncodeOptions(
  override val metadataPolicy: MetadataPolicy = MetadataPolicy.STRIP_ALL,
  override val outputFormat: AudioFormat? = null,
) : AudioEncodeOptions
