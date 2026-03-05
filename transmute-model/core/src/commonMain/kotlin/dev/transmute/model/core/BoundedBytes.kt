package dev.transmute.model.core

import kotlinx.serialization.Serializable

/**
 * A view into a [Bytes] buffer bounded by a [ByteRange].
 * Carries both the raw bytes and the range they were read from.
 */
@Serializable
data class BoundedBytes(val range: ByteRange, val data: Bytes)

/**
 * Creates a [BoundedBytes] from a [Bytes] instance starting at the given [offset].
 * The length is derived from the byte array size.
 */
fun Bytes.boundAt(offset: ByteOffset): BoundedBytes = BoundedBytes(
  range = ByteRange(offset, ByteLength(this.size.toLong())),
  data = this,
)
