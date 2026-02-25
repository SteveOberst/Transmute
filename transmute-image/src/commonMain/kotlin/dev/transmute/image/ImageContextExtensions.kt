package dev.transmute.image

import dev.transmute.common.TransmuteContext

/**
 * Keys used to store image codec registries in [TransmuteContext.extras].
 */
private const val IMAGE_DECODERS_KEY = "transmute.image.decoders"
private const val IMAGE_ENCODERS_KEY = "transmute.image.encoders"

// -- Builder extensions -------------------------------------------------------

/**
 * Provide a custom [ImageDecoderRegistry] via the context.
 *
 * Pipeline handlers that receive a [TransmuteContext] will prefer these
 * registries over the global [ImageRegistries].
 *
 * ```kotlin
 * val ctx = TransmuteContext {
 *     imageDecoders(myDecoderRegistry)
 *     imageEncoders(myEncoderRegistry)
 * }
 * ```
 */
fun TransmuteContext.Builder.imageDecoders(registry: ImageDecoderRegistry) {
  extra(IMAGE_DECODERS_KEY, registry)
}

/**
 * Provide a custom [ImageEncoderRegistry] via the context.
 *
 * @see imageDecoders
 */
fun TransmuteContext.Builder.imageEncoders(registry: ImageEncoderRegistry) {
  extra(IMAGE_ENCODERS_KEY, registry)
}

// -- Read extensions ----------------------------------------------------------

/**
 * Image decoder registry stored in this context, or `null` if none was set.
 *
 * Typically populated via [TransmuteContext.Builder.imageDecoders].
 */
val TransmuteContext.imageDecoders: ImageDecoderRegistry?
  get() = service(IMAGE_DECODERS_KEY)

/**
 * Image encoder registry stored in this context, or `null` if none was set.
 *
 * Typically populated via [TransmuteContext.Builder.imageEncoders].
 */
val TransmuteContext.imageEncoders: ImageEncoderRegistry?
  get() = service(IMAGE_ENCODERS_KEY)
