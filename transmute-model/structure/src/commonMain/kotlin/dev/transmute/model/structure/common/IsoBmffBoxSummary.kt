@file:Suppress("unused")

package dev.transmute.model.structure.common

import kotlinx.serialization.Serializable

/**
 * A lightweight summary of an ISO BMFF box, suitable for use in
 * [dev.transmute.model.core.MediaStructure] implementations.
 *
 * The raw payload bytes are omitted; only the type code and size
 * are retained.  Child box types are summarised by their type-code
 * strings rather than recursively expanded.
 */
@Serializable
data class IsoBmffBoxSummary(
  /** 4-character box type code, e.g. `"ftyp"`, `"moov"`, `"mdat"`. */
  val type: String,
  /** Total payload size in bytes (excluding the 8-byte box header). */
  val dataSizeBytes: Long,
  /** Type codes of immediate children (for container boxes). */
  val childTypes: List<String> = emptyList(),
)
