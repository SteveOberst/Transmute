package dev.transmute.core.pipeline

import kotlin.jvm.JvmInline

/** Identifies a [Transform] in a transform pipeline. */
@JvmInline
value class TransformId(val value: String) {
  override fun toString(): String = value
}

