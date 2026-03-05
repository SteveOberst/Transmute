@file:Suppress("unused")

package dev.transmute.model.structure.common

import dev.transmute.model.core.ByteLength
import kotlinx.serialization.Serializable

/**
 * JSON-safe summary of an Ogg page, preserving file order while omitting page payload bytes.
 */
@Serializable
data class OggPageSummary(
  val serialNumber: OggSerialNumber,
  val pageSequence: UInt,
  /** Header type flags (BOS / EOS / continuation). */
  val headerType: UByte,
  /** Granule position (codec-specific). */
  val granulePosition: Long,
  /** Segment table byte count. */
  val segmentCount: Int,
  /** Total concatenated segment data size in bytes. */
  val dataBytes: ByteLength,
)

fun OggPage.toSummary(): OggPageSummary = OggPageSummary(
  serialNumber = serialNumber,
  pageSequence = pageSequence,
  headerType = headerType,
  granulePosition = granulePosition,
  segmentCount = segmentTable.size,
  dataBytes = ByteLength(data.size.toLong()),
)
