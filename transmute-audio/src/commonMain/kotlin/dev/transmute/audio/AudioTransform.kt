package dev.transmute.audio

import dev.transmute.codec.pipeline.Transform

/**
 * Domain-specific extension of [Transform] for audio transforms.
 *
 * All concrete audio transforms implement this interface so that
 * [dev.transmute.AudioTransmuter.wouldTransmute] can ask each transform whether
 * it would actually produce a change, without resorting to an exhaustive
 * `when` dispatch in the transmuter.
 */
interface AudioTransform : Transform<AudioIR> {
    /**
     * Returns `true` if this transform would produce any change on an audio
     * track described by [hint].
     *
     * Conservative: if [hint] properties are `null` (unknown), the transform
     * should return `true` (assume it applies).
     */
    fun wouldTransform(hint: AudioHint): Boolean
}
