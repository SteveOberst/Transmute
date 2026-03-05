package dev.transmute.model.structure.common

import dev.transmute.model.core.BinarySerializable
import dev.transmute.model.core.ByteRange
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes
import dev.transmute.model.identify.FourCC
import kotlinx.serialization.Serializable

/**
 * Reference to a single ISO Base Media File Format box within the file
 * (lightweight, no data).
 */
@Serializable
data class IsoBmffBoxRef(val type: FourCC, val range: ByteRange, val children: List<IsoBmffBoxRef> = emptyList())

// --- Helpers - big-endian encoding ---

private fun UInt.toBigEndianBytes(): ByteArray = byteArrayOf(
  (this shr 24).toByte(),
  (this shr 16).toByte(),
  (this shr 8).toByte(),
  this.toByte(),
)

private fun ULong.toBigEndianBytes(): ByteArray = byteArrayOf(
  (this shr 56).toByte(),
  (this shr 48).toByte(),
  (this shr 40).toByte(),
  (this shr 32).toByte(),
  (this shr 24).toByte(),
  (this shr 16).toByte(),
  (this shr 8).toByte(),
  this.toByte(),
)

private fun FourCC.toByteArray(): ByteArray = value.encodeToByteArray() // always 4 ASCII bytes

// --- ISO BMFF box with payload data ---

/**
 * A single ISO Base Media File Format box with its full payload data.
 *
 * Standard box layout:
 * ```
 * | size (4 B BE) | type (4 B) | payload (size  8 bytes) |
 * ```
 *
 * When [size] on disk is 1, an 8-byte [largeSize] field follows the type:
 * ```
 * | 00 00 00 01 | type (4 B) | largeSize (8 B BE) | payload ... |
 * ```
 *
 * For container boxes, [children] holds the parsed sub-boxes and [data]
 * is empty.  For leaf boxes, [data] holds the raw payload.
 */
@Serializable
data class IsoBmffBox(
  /** 4-byte box type code (e.g. `ftyp`, `moov`, `mdat`). */
  val type: FourCC,
  /** Raw payload data. Empty for container boxes. */
  val data: Bytes = Bytes(ByteArray(0)),
  /** Parsed sub-boxes. Empty for leaf boxes. */
  val children: List<IsoBmffBox> = emptyList(),
  /** Non-null when the box uses 64-bit extended size. */
  val largeSize: ULong? = null,
) : BinarySerializable {

  override fun toBytes(): Bytes {
    val typeBytes = type.toByteArray()
    val payload = if (children.isNotEmpty()) {
      val parts = children.map { it.toBytes().data }
      val total = parts.sumOf { it.size }
      val buf = ByteArray(total)
      var pos = 0
      for (part in parts) {
        part.copyInto(buf, pos)
        pos += part.size
      }
      buf
    } else {
      data.data
    }
    return if (largeSize != null) {
      val totalSize = (16 + payload.size).toULong()
      val out = ByteArray(16 + payload.size)
      1u.toBigEndianBytes().copyInto(out, 0) // size = 1 signals extended size
      typeBytes.copyInto(out, 4)
      totalSize.toBigEndianBytes().copyInto(out, 8)
      payload.copyInto(out, 16)
      out.asBytes()
    } else {
      val totalSize = (8 + payload.size).toUInt()
      val out = ByteArray(8 + payload.size)
      totalSize.toBigEndianBytes().copyInto(out, 0)
      typeBytes.copyInto(out, 4)
      payload.copyInto(out, 8)
      out.asBytes()
    }
  }
}
