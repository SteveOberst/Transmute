package dev.transmute.model.structure.common

import dev.transmute.model.core.BinarySerializable
import dev.transmute.model.core.ByteRange
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes
import dev.transmute.model.identify.EbmlId
import kotlinx.serialization.Serializable

/**
 * Reference to a single EBML element within the file (lightweight, no data).
 */
@Serializable
data class EbmlElementRef(val id: EbmlId, val range: ByteRange, val children: List<EbmlElementRef> = emptyList())

// --- EBML VINT encoding ---

/**
 * Encode a non-negative [Long] as an EBML variable-length integer (VINT).
 *
 * The VINT uses 1-8 bytes.  The leading bits of the first byte indicate
 * the total byte count (1xxxxxxx = 1 byte, 01xxxxxx = 2 bytes, ...).
 */
private fun Long.toEbmlVint(): ByteArray {
  val v = this
  return when {
    v <= 0x7E -> byteArrayOf((v or 0x80).toByte())
    v <= 0x3FFE -> byteArrayOf(((v shr 8) or 0x40).toByte(), v.toByte())
    v <= 0x1FFFFE -> byteArrayOf(((v shr 16) or 0x20).toByte(), (v shr 8).toByte(), v.toByte())
    v <= 0x0FFFFFFE -> byteArrayOf(((v shr 24) or 0x10).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
    v <= 0x07FFFFFFFE -> byteArrayOf(
      ((v shr 32) or 0x08).toByte(),
      (v shr 24).toByte(),
      (v shr 16).toByte(),
      (v shr 8).toByte(),
      v.toByte(),
    )
    v <= 0x03FFFFFFFFFE -> byteArrayOf(
      ((v shr 40) or 0x04).toByte(),
      (v shr 32).toByte(),
      (v shr 24).toByte(),
      (v shr 16).toByte(),
      (
        v shr
          8
        ).toByte(),
      v.toByte(),
    )
    v <= 0x01FFFFFFFFFFFE -> byteArrayOf(
      ((v shr 48) or 0x02).toByte(),
      (v shr 40).toByte(),
      (v shr 32).toByte(),
      (v shr 24).toByte(),
      (
        v shr
          16
        ).toByte(),
      (v shr 8).toByte(),
      v.toByte(),
    )
    else -> byteArrayOf(
      ((v shr 56) or 0x01).toByte(),
      (v shr 48).toByte(),
      (v shr 40).toByte(),
      (v shr 32).toByte(),
      (
        v shr
          24
        ).toByte(),
      (v shr 16).toByte(),
      (v shr 8).toByte(),
      v.toByte(),
    )
  }
}

/**
 * Encode an [EbmlId] as its raw VINT representation without the
 * size-marker bits stripped - i.e. the on-disk bytes.
 */
private fun EbmlId.toBytes(): ByteArray {
  val v = value
  return when {
    v <= 0xFF -> byteArrayOf(v.toByte())
    v <= 0xFFFF -> byteArrayOf((v shr 8).toByte(), v.toByte())
    v <= 0xFFFFFF -> byteArrayOf((v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
    else -> byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
  }
}

// --- EBML element with payload data ---

/**
 * A single EBML element with its full payload data.
 *
 * ```
 * | id (VINT) | size (VINT) | payload ... |
 * ```
 *
 * For master elements, [children] holds the parsed sub-elements and
 * [data] is empty.  For leaf elements, [data] holds the raw payload.
 */
@Serializable
data class EbmlElement(
  /** EBML element identifier (already includes class bits). */
  val id: EbmlId,
  /** Raw payload data. Empty for master elements. */
  val data: Bytes = Bytes(ByteArray(0)),
  /** Parsed sub-elements. Empty for leaf elements. */
  val children: List<EbmlElement> = emptyList(),
) : BinarySerializable {

  override fun toBytes(): Bytes {
    val idBytes = id.toBytes()
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
    val sizeVint = payload.size.toLong().toEbmlVint()
    val out = ByteArray(idBytes.size + sizeVint.size + payload.size)
    idBytes.copyInto(out, 0)
    sizeVint.copyInto(out, idBytes.size)
    payload.copyInto(out, idBytes.size + sizeVint.size)
    return out.asBytes()
  }
}
