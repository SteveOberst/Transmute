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

// ---
//  Known ISO BMFF container types
// ---

/** Plain containers: child boxes start immediately at offset 0 of the payload. */
private val PLAIN_CONTAINERS: Set<String> = setOf(
  // HEIF / AVIF / HEIC
  "iprp", "ipco", "grpl",
  // MP4 / MOV / M4A / HEIF shared
  "moov", "trak", "mdia", "minf", "stbl", "udta", "mvex", "dinf", "edts",
  // Generic
  "sinf", "schi",
)

/**
 * FullBox containers: payload starts with version(1) + flags(3) = 4 bytes,
 * child boxes begin at offset 4.
 */
private val FULL_CONTAINERS: Set<String> = setOf(
  "meta",
  "iref",
)

// `iinf` is also a FullBox container but carries an entry_count field between
// the version/flags and the `infe` child boxes; handled separately in [expandChildren].

// ---
//  toTree() entry point
// ---

/**
 * Convert a parsed [IsoBmffBox] into a JSON-safe recursive tree.
 *
 * Known container and FullBox container types are recursively expanded so that
 * the full ISO BMFF box hierarchy is visible (meta -> iinf -> infe, meta -> iprp
 * -> ipco -> ispe/hvcC, etc.) rather than appearing as opaque byte-size blobs.
 *
 * Large data-bearing boxes (`mdat`, raw codec config, etc.) remain as leaves
 * that only expose their byte count.
 */
fun IsoBmffBox.toTree(): IsoBmffBoxTree {
  if (children.isNotEmpty()) {
    // Already recursively parsed (pre-expanded model)
    return IsoBmffBoxTree(
      type = type.value,
      dataSizeBytes = data.size.toLong(),
      hasLargeSize = largeSize != null,
      children = children.map { it.toTree() },
    )
  }
  return IsoBmffBoxTree(
    type = type.value,
    dataSizeBytes = data.size.toLong(),
    hasLargeSize = largeSize != null,
    children = data.data.expandChildren(type.value),
  )
}

// ---
//  Internal helpers
// ---

/**
 * Expand child [IsoBmffBoxTree] nodes from this payload [ByteArray] given the
 * parent [boxType], applying the correct header-skip for FullBox types.
 * Returns an empty list for leaf boxes.
 */
private fun ByteArray.expandChildren(boxType: String): List<IsoBmffBoxTree> = when {
  boxType in PLAIN_CONTAINERS ->
    parseToBoxTrees(0, size)

  boxType in FULL_CONTAINERS ->
    // FullBox: skip version(1) + flags(3)
    if (size > 4) parseToBoxTrees(4, size) else emptyList()

  boxType == "iinf" -> {
    // FullBox: version(1) + flags(3) + entry_count (2 bytes for v0, 4 bytes for v1+)
    if (size >= 5) {
      val version = this[0].toInt() and 0xFF
      val skip = if (version == 0) 6 else 8
      if (size > skip) parseToBoxTrees(skip, size) else emptyList()
    } else {
      emptyList()
    }
  }

  else -> emptyList()
}

/**
 * Minimal ISO BMFF box scanner that produces [IsoBmffBoxTree] nodes directly,
 * without creating intermediate [IsoBmffBox] objects.
 *
 * Recurses into all known container types so the returned tree reflects the
 * true on-disk hierarchy.
 */
internal fun ByteArray.parseToBoxTrees(offset: Int, end: Int): List<IsoBmffBoxTree> {
  val result = mutableListOf<IsoBmffBoxTree>()
  var pos = offset
  while (pos + 8 <= end) {
    val size32 = readBE32Tree(pos)
    val boxType = decodeToString(startIndex = pos + 4, endIndex = pos + 8)

    val (hdrSize, boxSize, large) = when {
      size32 == 1u -> {
        if (pos + 16 > end) return result
        Triple(16, readBE64Tree(pos + 8), true)
      }
      size32 == 0u -> Triple(8, (end - pos).toLong(), false)
      else -> Triple(8, size32.toLong(), false)
    }

    val payloadStart = pos + hdrSize
    val payloadEnd = if (boxSize <= 0) end else minOf(pos + boxSize.toInt(), end)
    val payloadSize = maxOf(0, payloadEnd - payloadStart)

    val subChildren = if (payloadSize > 0) {
      val payload = copyOfRange(payloadStart, payloadEnd)
      payload.expandChildren(boxType)
    } else {
      emptyList()
    }

    result += IsoBmffBoxTree(
      type = boxType,
      dataSizeBytes = payloadSize.toLong(),
      hasLargeSize = large,
      children = subChildren,
    )

    pos = if (boxSize <= 0) end else minOf(pos + boxSize.toInt(), end)
  }
  return result
}

private fun ByteArray.readBE32Tree(offset: Int): UInt = ((this[offset].toInt() and 0xFF).toUInt() shl 24) or
  ((this[offset + 1].toInt() and 0xFF).toUInt() shl 16) or
  ((this[offset + 2].toInt() and 0xFF).toUInt() shl 8) or
  (this[offset + 3].toInt() and 0xFF).toUInt()

private fun ByteArray.readBE64Tree(offset: Int): Long = ((this[offset].toLong() and 0xFF) shl 56) or
  ((this[offset + 1].toLong() and 0xFF) shl 48) or
  ((this[offset + 2].toLong() and 0xFF) shl 40) or
  ((this[offset + 3].toLong() and 0xFF) shl 32) or
  ((this[offset + 4].toLong() and 0xFF) shl 24) or
  ((this[offset + 5].toLong() and 0xFF) shl 16) or
  ((this[offset + 6].toLong() and 0xFF) shl 8) or
  (this[offset + 7].toLong() and 0xFF)
