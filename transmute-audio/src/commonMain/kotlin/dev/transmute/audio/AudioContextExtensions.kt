package dev.transmute.audio

import dev.transmute.common.TransmuteContext

/**
 * Keys used to store audio codec registries in [TransmuteContext.extras].
 */
private const val AUDIO_DECODERS_KEY = "transmute.audio.decoders"
private const val AUDIO_ENCODERS_KEY = "transmute.audio.encoders"

// -- Builder extensions ---

/**
 * Provide a custom [AudioDecoderRegistry] via the context.
 *
 * Pipeline handlers that receive a [TransmuteContext] will prefer these
 * registries over the global [AudioRegistries].
 *
 * ```kotlin
 * val ctx = TransmuteContext {
 *     audioDecoders(myDecoderRegistry)
 *     audioEncoders(myEncoderRegistry)
 * }
 * ```
 */
fun TransmuteContext.Builder.audioDecoders(registry: AudioDecoderRegistry) {
  extra(AUDIO_DECODERS_KEY, registry)
}

/**
 * Provide a custom [AudioEncoderRegistry] via the context.
 *
 * @see audioDecoders
 */
fun TransmuteContext.Builder.audioEncoders(registry: AudioEncoderRegistry) {
  extra(AUDIO_ENCODERS_KEY, registry)
}

// -- Read extensions ---

/**
 * Audio decoder registry stored in this context, or `null` if none was set.
 *
 * Typically populated via [TransmuteContext.Builder.audioDecoders].
 */
val TransmuteContext.audioDecoders: AudioDecoderRegistry?
  get() = service(AUDIO_DECODERS_KEY)

/**
 * Audio encoder registry stored in this context, or `null` if none was set.
 *
 * Typically populated via [TransmuteContext.Builder.audioEncoders].
 */
val TransmuteContext.audioEncoders: AudioEncoderRegistry?
  get() = service(AUDIO_ENCODERS_KEY)
