package dev.transmute.codec.pipeline

import dev.transmute.model.core.MediaFormat

/**
 * Result of a decode stage: decoded intermediate representation plus the resolved input format.
 *
 * This keeps runtime format selection explicit without storing it in the context.
 */
data class Decoded<F : MediaFormat<*, *>, IR>(
  val format: F,
  val ir: IR,
)
