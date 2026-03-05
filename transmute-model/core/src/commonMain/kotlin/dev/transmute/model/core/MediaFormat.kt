@file:Suppress("unused")

package dev.transmute.model.core

/**
 * Typed format descriptor for media files.
 *
 * Domain modules define their own sealed format hierarchies:
 *
 * - `dev.transmute.image.ImageFormat : MediaFormat<ImageDecodeOptions, ImageEncodeOptions>`
 * - `dev.transmute.audio.AudioFormat : MediaFormat<AudioDecodeOptions, AudioEncodeOptions>`
 * - `dev.transmute.video.VideoFormat : MediaFormat<VideoDecodeOptions, VideoEncodeOptions>`
 *
 * Each format entry provides a human-readable [label], its [mimeType],
 * default file [extension], and optional [containerFamily].
 */
interface MediaFormat<out D : DecodeOptions, out E : EncodeOptions> {
  /** Human-readable format name (e.g. "MP3", "JPEG"). */
  val label: String

  /** MIME type string (e.g. "audio/mpeg"). */
  val mimeType: String

  /** Default file extension without dot (e.g. "mp3"). */
  val extension: String

  /** Container family this format belongs to, or `null` for standalone formats. */
  val containerFamily: ContainerFamily?
    get() = null
}

/**
 * Domain-agnostic unknown format marker for APIs that need a single "unknown".
 *
 * Domain-specific format hierarchies may also define their own Unknown object.
 */
data object UnknownFormat : MediaFormat<NoDecodeOptions, NoEncodeOptions> {
  override val label: String = "Unknown"
  override val mimeType: String = "application/octet-stream"
  override val extension: String = "bin"
  override val containerFamily: ContainerFamily? = null
}
