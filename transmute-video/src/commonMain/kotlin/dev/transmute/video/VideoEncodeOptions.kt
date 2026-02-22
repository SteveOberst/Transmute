package dev.transmute.video

import dev.transmute.core.EncodeOptions
import dev.transmute.core.MetadataPolicy
import dev.transmute.core.VideoFormat

/**
 * Sealed hierarchy of video encoding options.
 *
 * Most video formats use [DefaultVideoEncodeOptions] until
 * format-specific knobs (bitrate, resolution, codec profile, etc.) are needed.
 */
sealed interface VideoEncodeOptions : EncodeOptions {
  /**
   * Controls whether metadata (container tags, rotation, GPS, etc.) should be preserved or stripped during encoding.
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
  val outputFormat: VideoFormat?
}

/**
 * Default encode options for video formats with no special configuration.
 */
data class DefaultVideoEncodeOptions(
  override val metadataPolicy: MetadataPolicy = MetadataPolicy.STRIP_ALL,
  override val outputFormat: VideoFormat? = null,
) : VideoEncodeOptions
