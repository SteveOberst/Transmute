package dev.transmute.model.structure.common

import dev.transmute.model.core.BinarySerializable
import dev.transmute.model.core.ByteRange
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes
import dev.transmute.model.identify.RiffChunkId
import kotlinx.serialization.Serializable

/**
 * Reference to a single RIFF chunk within the file (lightweight, no data).
 */
@Serializable
data class RiffChunkRef(val id: RiffChunkId, val range: ByteRange, val children: List<RiffChunkRef> = emptyList())

// --- Helpers - little-endian encoding ---

private fun UInt.toLittleEndianBytes(): ByteArray = byteArrayOf(
  this.toByte(),
  (this shr 8).toByte(),
  (this shr 16).toByte(),
  (this shr 24).toByte(),
)

// --- RIFF chunk with payload data ---

/**
 * A single RIFF chunk with its full payload data.
 *
 * ```
 * | id (4 B ASCII) | size (4 B LE) | payload ... | pad? |
 * ```
 *
 * For container chunks (`RIFF` / `LIST`), [formType] holds the 4-byte
 * form- or list-type and [children] holds the sub-chunks.  [data] is
 * empty in that case.
 *
 * For leaf chunks, [data] holds the raw payload and [children] is empty.
 *
 * On disk, chunks whose payload length is odd are followed by a single
 * zero pad byte that is **not** counted in [size].
 */
@Serializable
data class RiffChunk(
  /** 4-byte ASCII chunk identifier (e.g. `RIFF`, `fmt `, `data`). */
  val id: RiffChunkId,
  /** Payload size in bytes (LE UInt32). Excludes pad byte. */
  val size: UInt,
  /** Form / list type for RIFF and LIST containers; `null` for leaf chunks. */
  val formType: RiffChunkId? = null,
  /** Raw payload data. Empty for container chunks. */
  val data: Bytes = Bytes(ByteArray(0)),
  /** Sub-chunks. Empty for leaf chunks. */
  val children: List<RiffChunk> = emptyList(),
) : BinarySerializable {

  override fun toBytes(): Bytes {
    val idBytes = id.value.encodeToByteArray()
    val sizeBytes = size.toLittleEndianBytes()
    return if (formType != null) {
      val ftBytes = formType.value.encodeToByteArray()
      val childParts = children.map { it.toBytes().data }
      val childTotal = childParts.sumOf { it.size }
      val out = ByteArray(8 + 4 + childTotal)
      idBytes.copyInto(out, 0)
      sizeBytes.copyInto(out, 4)
      ftBytes.copyInto(out, 8)
      var pos = 12
      for (part in childParts) {
        part.copyInto(out, pos)
        pos += part.size
      }
      out.asBytes()
    } else {
      val pad = data.size % 2 != 0
      val out = ByteArray(8 + data.size + if (pad) 1 else 0)
      idBytes.copyInto(out, 0)
      sizeBytes.copyInto(out, 4)
      data.data.copyInto(out, 8)
      out.asBytes()
    }
  }
}
