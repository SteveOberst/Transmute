package dev.transmute.core.pipeline

import dev.transmute.core.FormatTag
import dev.transmute.core.MediaFormat

/** Encoded bytes with a type-level [formatTag]. */
data class EncodedBytes<F : MediaFormat, OUT : FormatTag<F>>(
  val formatTag: OUT,
  val bytes: ByteArray,
) {
  val format: F get() = formatTag.format

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is EncodedBytes<*, *>) return false
    if (formatTag != other.formatTag) return false
    if (!bytes.contentEquals(other.bytes)) return false
    return true
  }

  override fun hashCode(): Int {
    var result = formatTag.hashCode()
    result = 31 * result + bytes.contentHashCode()
    return result
  }
}

/**
 * A encode pipeline: IR in, some OUT out (often ByteArray or [EncodedBytes]).
 */
typealias EncodePipeline<IR, OUT> = Pipeline<IR, OUT>
