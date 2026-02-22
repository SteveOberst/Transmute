package dev.transmute.video

import dev.transmute.core.EncodeOptions
import dev.transmute.core.MetadataPolicy
import dev.transmute.core.OutputFormat
import dev.transmute.core.VideoFormat

/**
 * Sealed hierarchy of video encoding options.
 *
 * Most video formats use [CanonicalVideoEncodeOptions] until
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
   * Output-format selection for *dynamic-output* transmuters.
   *
   * Use [OutputFormat.ORIGINAL] to fall back to the input format.
   * Fixed-output transmuters ignore this value (and may validate it).
   */
  val outputFormat: OutputFormat<VideoFormat>
}

/**
 * Default encode options for video formats with no special configuration.
 */
data class CanonicalVideoEncodeOptions(
  override val metadataPolicy: MetadataPolicy = MetadataPolicy.STRIP_ALL,
  override val outputFormat: OutputFormat<VideoFormat> = OutputFormat.ORIGINAL,
) : VideoEncodeOptions
