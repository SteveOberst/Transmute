package dev.transmute.core.pipeline

import kotlin.jvm.JvmInline

/**
 * Type-safe identifiers used throughout the conversion pipeline.
 *
 * These replace raw `String` IDs to prevent mix-ups (e.g. accidentally
 * passing a stage ID where a transform ID is expected).
 */

/** Identifies a stage within a [ConversionPlan]. */
@JvmInline
value class StageId(val value: String) {
  override fun toString(): String = value
}

/** Identifies a [Transform] registered in a [TransformRegistry]. */
@JvmInline
value class TransformId(val value: String) {
  override fun toString(): String = value
}

/** Identifies a codec (decoder or encoder) in a registry. */
@JvmInline
value class CodecId(val value: String) {
  override fun toString(): String = value
}
