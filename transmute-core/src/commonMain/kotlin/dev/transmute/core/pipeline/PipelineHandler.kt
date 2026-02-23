package dev.transmute.core.pipeline

import dev.transmute.core.TransmuteContext

/**
 * A single typed step in a conversion pipeline.
 *
 * All pipeline components (byte handlers, decoders, IR transforms, encoders, post-encode
 * steps, etc.) can be expressed through this common interface.
 */
fun interface PipelineHandler<IN, OUT> {
  suspend fun handle(value: IN, context: TransmuteContext): OUT
}

/**
 * Compose two handlers into a single handler.
 *
 * This is the recommended way to build decode/encode pipelines when you want to primarily chain
 * handler classes without writing lambdas:
 *
 * ```kotlin
 * val handler = ImageCodecs.Decode.DEFAULT + MyAuditHandler() + MyMetricsHandler()
 * ```
 */
operator fun <A, B, C> PipelineHandler<A, B>.plus(next: PipelineHandler<B, C>): PipelineHandler<A, C> =
  PipelineHandler { value, ctx ->
    val mid = handle(value, ctx)
    next.handle(mid, ctx)
  }

/**
 * Tap-style handler that executes [block] and returns the original value unchanged.
 */
fun <T> tap(block: suspend (T, TransmuteContext) -> Unit): PipelineHandler<T, T> =
  PipelineHandler { value, ctx ->
    block(value, ctx)
    value
  }
