@file:Suppress("unused")

package dev.transmute.model.metadata.icc

import dev.transmute.model.core.Iso8601String
import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.identify.FourCC
import dev.transmute.model.metadata.common.PayloadRef
import kotlinx.serialization.Serializable

/**
 * ICC colour profile metadata.
 *
 * Models the ICC on-disk hierarchy:
 * - 128-byte profile header (big-endian)
 * - Tag table: count + (signature, offset, size) entries
 *
 * Tag payloads are not decoded, but are preserved for round-tripping through [IccTag.payload].
 */
@Serializable
data class IccProfileMetadata(
  val header: IccHeader,
  val tags: List<IccTag>,
  /** Reference to the original ICC profile bytes, when available. */
  val original: PayloadRef? = null,
) : MediaMetadata

// -- Header ---

@Serializable
data class IccHeader(
  /** Total profile size in bytes (u32). */
  val profileSize: UInt,
  /** Preferred CMM type (4-byte signature). */
  val preferredCmm: FourCC? = null,
  /** Profile version (BCD-like packed fields in header). */
  val profileVersion: IccVersion,
  /** Device class signature (e.g. `mntr`, `prtr`). */
  val profileClass: FourCC,
  /** Data colour space signature (e.g. `RGB `, `CMYK`). */
  val colorSpace: FourCC,
  /** Profile Connection Space signature (e.g. `XYZ `, `Lab `). */
  val pcs: FourCC,
  /** Creation date/time (u16 fields) normalized to ISO 8601 when available. */
  val creationDate: Iso8601String? = null,
  /** Primary platform signature (e.g. `APPL`, `MSFT`). */
  val primaryPlatform: FourCC? = null,
  /** Rendering intent (u32). */
  val renderingIntent: IccRenderingIntent,
)

@Serializable
data class IccVersion(
  val major: UByte,
  val minor: UByte,
  val bugfix: UByte,
)

@Serializable
enum class IccRenderingIntent {
  Perceptual,
  MediaRelativeColorimetric,
  Saturation,
  ICCAbsoluteColorimetric,
  Unknown,
}

// -- Tag table ---

@Serializable
data class IccTag(
  /** 4-byte tag signature (e.g. `desc`, `rXYZ`, `gTRC`). */
  val signature: FourCC,
  /** Byte offset from the start of the profile (u32). */
  val offset: UInt,
  /** Size of the tag payload in bytes (u32). */
  val size: UInt,
  /** Opaque payload reference for round-tripping (offset/size within the ICC profile). */
  val payload: PayloadRef = PayloadRef(sizeBytes = size.toULong()),
)

