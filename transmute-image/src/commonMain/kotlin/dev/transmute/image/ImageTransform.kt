package dev.transmute.image

import dev.transmute.codec.pipeline.Transform

/**
 * Domain-specific extension of [Transform] for image transforms.
 *
 * All concrete image transforms implement this interface so that
 * [dev.transmute.ImageTransmuter.wouldTransmute] can ask each transform whether
 * it would actually produce a change, without resorting to an exhaustive
 * `when` dispatch in the transmuter.
 */
interface ImageTransform : Transform<ImageIR> {
  /**
   * Returns `true` if this transform would produce any change on an image
   * described by [hint].
   *
   * Conservative: if [hint] properties are `null` (unknown), the transform
   * should return `true` (assume it applies).
   */
  fun wouldTransform(hint: ImageHint): Boolean
}
