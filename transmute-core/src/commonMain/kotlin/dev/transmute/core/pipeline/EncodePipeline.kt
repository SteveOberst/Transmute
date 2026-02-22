package dev.transmute.core.pipeline

import dev.transmute.core.Bytes
import dev.transmute.core.MediaFormat

/** Encoded bytes tagged with the resolved [format]. */
data class EncodedBytes<F : MediaFormat<*, *>>(
  val format: F,
  val bytes: Bytes,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is EncodedBytes<*>) return false
    if (format != other.format) return false
    if (!bytes.data.contentEquals(other.bytes.data)) return false
    return true
  }

  override fun hashCode(): Int {
    var result = format.hashCode()
    result = 31 * result + bytes.data.contentHashCode()
    return result
  }
}

/**
 * A encode pipeline: IR in, some OUT out (often ByteArray or [EncodedBytes]).
 */
typealias EncodePipeline<IR, OUT> = Pipeline<IR, OUT>
