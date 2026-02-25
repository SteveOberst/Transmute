package dev.transmute.codec.pipeline

import dev.transmute.common.PipelineContext

/**
 * A single step in a media conversion transform pipeline.
 *
 * Transforms are generic over the intermediate representation (IR) type,
 * ensuring type safety (e.g., an image transform cannot accidentally receive audio data).
 */
interface Transform<IR> {
  val id: TransformId
  suspend fun apply(ir: IR, context: PipelineContext): IR
}
