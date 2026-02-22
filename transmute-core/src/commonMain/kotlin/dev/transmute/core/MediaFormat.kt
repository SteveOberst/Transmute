package dev.transmute.core

/**
 * Typed format object with associated decode/encode option types.
 *
 * Domain modules define their own sealed format hierarchies, for example:
 *
 * - `dev.transmute.image.ImageFormat : MediaFormat<ImageDecodeOptions, ImageEncodeOptions>`
 * - `dev.transmute.audio.AudioFormat : MediaFormat<AudioDecodeOptions, AudioEncodeOptions>`
 * - `dev.transmute.video.VideoFormat : MediaFormat<VideoDecodeOptions, VideoEncodeOptions>`
 */
interface MediaFormat<out D : DecodeOptions, out E : EncodeOptions> {
  val mimeType: String
  val extension: String
}

/**
 * Domain-agnostic unknown format marker for APIs that need a single "unknown".
 *
 * Domain-specific format hierarchies may also define their own Unknown object.
 */
data object UnknownFormat : MediaFormat<NoDecodeOptions, NoEncodeOptions> {
  override val mimeType: String = "application/octet-stream"
  override val extension: String = "bin"
}
