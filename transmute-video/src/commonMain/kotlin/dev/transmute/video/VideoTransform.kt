package dev.transmute.video

import dev.transmute.codec.pipeline.Transform

/**
 * Domain-specific extension of [Transform] for video transforms.
 *
 * All concrete video transforms implement this interface so that
 * [dev.transmute.VideoTransmuter.wouldTransmute] can ask each transform whether
 * it would actually produce a change, without resorting to an exhaustive
 * `when` dispatch in the transmuter.
 */
interface VideoTransform : Transform<VideoIR> {
  /**
   * Returns `true` if this transform would produce any change on a video
   * described by [hint].
   *
   * Conservative: if [hint] properties are `null` (unknown), the transform
   * should return `true` (assume it applies).
   */
  fun wouldTransform(hint: VideoHint): Boolean
}
