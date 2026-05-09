package dev.transmute.model.structure.common

import dev.transmute.model.core.BinarySerializable
import dev.transmute.model.core.ByteRange
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Ogg logical stream serial number.
 */
@Serializable
@JvmInline
value class OggSerialNumber(val value: Int) {
  override fun toString(): String = "OggSerial($value)"
}

/**
 * Ogg codec identifier string (e.g. "vorbis", "opus").
 */
@Serializable
@JvmInline
value class OggCodecId(val value: String) {
  override fun toString(): String = value
}

/**
 * Reference to a single Ogg page within the file (lightweight, no data).
 */
@Serializable
data class OggPageRef(val serialNumber: OggSerialNumber, val pageSequence: Int, val range: ByteRange)

/**
 * Logical stream within an Ogg container (lightweight reference).
 */
@Serializable
data class OggStreamRef(val serialNumber: OggSerialNumber, val codecId: OggCodecId? = null, val pages: List<OggPageRef> = emptyList())

// --- Helpers - little-endian encoding ---

private fun UInt.toLittleEndianBytes(): ByteArray = byteArrayOf(
  this.toByte(),
  (this shr 8).toByte(),
  (this shr 16).toByte(),
  (this shr 24).toByte(),
)

private fun Long.toLittleEndianBytes(): ByteArray = byteArrayOf(
  this.toByte(),
  (this shr 8).toByte(),
  (this shr 16).toByte(),
  (this shr 24).toByte(),
  (this shr 32).toByte(),
  (this shr 40).toByte(),
  (this shr 48).toByte(),
  (this shr 56).toByte(),
)

private fun Int.toLittleEndianBytes(): ByteArray = toUInt().toLittleEndianBytes()

// --- Ogg page with payload data ---

/**
 * A single Ogg page with its full payload data.
 *
 * ```
 * | "OggS" (4 B) | version (1 B) | headerType (1 B) | granulePosition (8 B LE) |
 * | serialNumber (4 B LE) | pageSequence (4 B LE) | crc (4 B LE) |
 * | segmentCount (1 B) | segmentTable (segmentCount B) | pageData ... |
 * ```
 *
 * Header-type flags:
 * - `0x01` - continuation of previous packet
 * - `0x02` - beginning of stream (BOS)
 * - `0x04` - end of stream (EOS)
 */
@Serializable
data class OggPage(
  /** Ogg stream structure version (always 0). */
  val version: UByte = 0u,
  /** Header type flags (BOS / EOS / continuation). */
  val headerType: UByte,
  /** Granule position (codec-specific). */
  val granulePosition: Long,
  /** Logical stream serial number. */
  val serialNumber: OggSerialNumber,
  /** Page sequence number within the logical stream. */
  val pageSequence: UInt,
  /** CRC-32 checksum of the entire page. */
  val crc: UInt,
  /** Segment table (one byte per segment giving its length). */
  val segmentTable: Bytes,
  /** Concatenated segment data. */
  val data: Bytes,
) : BinarySerializable {

  override fun toBytes(): Bytes {
    val segCount = segmentTable.size
    val headerSize = 27 + segCount
    val out = ByteArray(headerSize + data.size)
    // capture pattern
    "OggS".encodeToByteArray().copyInto(out, 0)
    out[4] = version.toByte()
    out[5] = headerType.toByte()
    granulePosition.toLittleEndianBytes().copyInto(out, 6)
    serialNumber.value.toLittleEndianBytes().copyInto(out, 14)
    pageSequence.toLittleEndianBytes().copyInto(out, 18)
    crc.toLittleEndianBytes().copyInto(out, 22)
    out[26] = segCount.toByte()
    segmentTable.data.copyInto(out, 27)
    data.data.copyInto(out, headerSize)
    return out.asBytes()
  }

  companion object {
    /** The 4-byte Ogg page sync pattern: `OggS`. */
    val CAPTURE_PATTERN: ByteArray = "OggS".encodeToByteArray()
  }
}
