@file:Suppress("unused")

package dev.transmute.model.structure.common

import kotlinx.serialization.Serializable

/**
 * JSON-safe, recursive ISO BMFF box tree.
 *
 * This preserves the full nested box hierarchy while intentionally omitting
 * raw payload bytes (which can be very large, e.g. `mdat`).
 */
@Serializable
data class IsoBmffBoxTree(
    /** 4-character box type (e.g. `ftyp`, `moov`, `mdat`). */
    val type: String,
    /** Payload byte count for this box (may be non-zero even when [children] are present). */
    val dataSizeBytes: Long,
    /** `true` if this box used 64-bit extended size on disk. */
    val hasLargeSize: Boolean,
    /** Nested child boxes (empty for leaves). */
    val children: List<IsoBmffBoxTree> = emptyList(),
)

/** Convert a parsed [IsoBmffBox] into a JSON-safe tree representation. */
fun IsoBmffBox.toTree(): IsoBmffBoxTree =
    IsoBmffBoxTree(
        type = type.value,
        dataSizeBytes = data.size.toLong(),
        hasLargeSize = largeSize != null,
        children = children.map { it.toTree() },
    )
