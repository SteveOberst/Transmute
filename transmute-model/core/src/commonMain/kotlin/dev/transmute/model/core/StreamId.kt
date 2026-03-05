package dev.transmute.model.core

import kotlinx.serialization.Serializable

/**
 * Stream identifier. Unique within a single media file.
 */
@Serializable
@JvmInline
value class StreamId(val value: Int) {
  override fun toString(): String = "StreamId($value)"
}
