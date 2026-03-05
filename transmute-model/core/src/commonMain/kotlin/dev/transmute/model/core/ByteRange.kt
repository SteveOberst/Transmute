package dev.transmute.model.core

import kotlinx.serialization.Serializable

/**
 * Byte offset within a media file. Zero-based absolute position.
 */
@Serializable
@JvmInline
value class ByteOffset(val value: Long) : Comparable<ByteOffset> {
  init {
    if (value < 0) {
      throw InvalidByteRangeException("ByteOffset must be non-negative, was $value")
    }
  }

  override fun compareTo(other: ByteOffset): Int = value.compareTo(other.value)

  override fun toString(): String = "ByteOffset($value)"
}

/**
 * Length in bytes. Always non-negative.
 */
@Serializable
@JvmInline
value class ByteLength(val value: Long) : Comparable<ByteLength> {
  init {
    if (value < 0) {
      throw InvalidByteRangeException("ByteLength must be non-negative, was $value")
    }
  }

  override fun compareTo(other: ByteLength): Int = value.compareTo(other.value)

  override fun toString(): String = "ByteLength($value)"
}

/**
 * A contiguous byte range within a media file, defined by [offset] and [length].
 */
@Serializable
data class ByteRange(val offset: ByteOffset, val length: ByteLength) {
  /** Exclusive end offset. */
  val end: ByteOffset get() = ByteOffset(offset.value + length.value)

  override fun toString(): String = "ByteRange(offset=${offset.value}, length=${length.value})"
}
