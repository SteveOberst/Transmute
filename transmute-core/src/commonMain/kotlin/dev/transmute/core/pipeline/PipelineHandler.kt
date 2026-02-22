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
