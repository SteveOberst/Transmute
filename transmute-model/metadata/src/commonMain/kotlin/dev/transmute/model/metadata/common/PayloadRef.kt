@file:Suppress("unused")

package dev.transmute.model.metadata.common

import kotlinx.serialization.Serializable

/**
 * A contiguous byte slice within an enclosing metadata payload.
 *
 * Semantics:
 * - [offset] is zero-based, relative to the start of the *enclosing* metadata block
 *   (e.g. start of a TIFF stream, start of an ID3 tag payload, start of an ICC profile).
 * - [length] is the number of bytes in the slice.
 *
 * This type is intentionally **not** [dev.transmute.model.core.ByteRange]:
 * `ByteRange` is defined as an absolute range within a media file, while metadata
 * is often parsed from an extracted byte buffer with no stable file offset.
 */
@Serializable
data class ByteSlice(
  val offset: ULong,
  val length: ULong,
)

/**
 * Reference to an opaque payload that must be preserved for round-tripping.
 *
 * - Use [slice] when the bytes are present in (or can be copied from) the original
 *   metadata block without re-encoding.
 * - Use only [sizeBytes] when the parser can measure the payload but does not have
 *   a stable way to address it (or chooses not to retain addressing information).
 */
@Serializable
data class PayloadRef(
  val sizeBytes: ULong,
  val slice: ByteSlice? = null,
)

