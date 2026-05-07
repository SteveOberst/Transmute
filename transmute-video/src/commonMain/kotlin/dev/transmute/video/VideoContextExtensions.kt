package dev.transmute.video

import dev.transmute.common.TransmuteContext

/**
 * Keys used to store video codec registries in [TransmuteContext.extras].
 */
private const val VIDEO_DECODERS_KEY = "transmute.video.decoders"
private const val VIDEO_ENCODERS_KEY = "transmute.video.encoders"

// -- Builder extensions ---

/**
 * Provide a custom [VideoDecoderRegistry] via the context.
 *
 * Pipeline handlers that receive a [TransmuteContext] will prefer these
 * registries over the global [VideoRegistries].
 *
 * ```kotlin
 * val ctx = TransmuteContext {
 *     videoDecoders(myDecoderRegistry)
 *     videoEncoders(myEncoderRegistry)
 * }
 * ```
 */
fun TransmuteContext.Builder.videoDecoders(registry: VideoDecoderRegistry) {
  extra(VIDEO_DECODERS_KEY, registry)
}

/**
 * Provide a custom [VideoEncoderRegistry] via the context.
 *
 * @see videoDecoders
 */
fun TransmuteContext.Builder.videoEncoders(registry: VideoEncoderRegistry) {
  extra(VIDEO_ENCODERS_KEY, registry)
}

// -- Read extensions ---

/**
 * Video decoder registry stored in this context, or `null` if none was set.
 *
 * Typically populated via [TransmuteContext.Builder.videoDecoders].
 */
val TransmuteContext.videoDecoders: VideoDecoderRegistry?
  get() = service(VIDEO_DECODERS_KEY)

/**
 * Video encoder registry stored in this context, or `null` if none was set.
 *
 * Typically populated via [TransmuteContext.Builder.videoEncoders].
 */
val TransmuteContext.videoEncoders: VideoEncoderRegistry?
  get() = service(VIDEO_ENCODERS_KEY)
