@file:Suppress("unused")

package dev.transmute.model.metadata.exif

import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.metadata.common.PayloadRef
import kotlinx.serialization.Serializable

/**
 * EXIF metadata represented as a TIFF/IFD hierarchy.
 *
 * This model is explicitly **write-aware**:
 * - IFD pointer relationships are modeled as nested [ExifIfd] trees (no flattening).
 * - Unknown tags can be preserved via [PayloadRef] without embedding raw bytes.
 * - Offsets/counts use unsigned types matching the on-disk TIFF representation.
 */
@Serializable
data class ExifMetadata(
  /** TIFF byte order marker (II/MM). */
  val byteOrder: ExifByteOrder,
  /** TIFF magic value; must be 42 for EXIF/TIFF. */
  val magic: UShort = 42u,
  /** Offset of IFD0 from the start of the TIFF stream. */
  val firstIfdOffset: UInt,
  /** The primary image IFD (IFD0), including nested Exif/GPS/Interop sub-IFDs. */
  val ifd0: ExifIfd? = null,
  /**
   * IFDs that were present in the TIFF stream but not reachable from [ifd0] by
   * following the standard pointer graph (corruption, vendor extensions, etc.).
   */
  val orphanIfds: List<ExifIfd> = emptyList(),
  /**
   * Reference to the original TIFF byte stream this model was parsed from, when available.
   * Useful for byte-identical round-trips when no edits are applied.
   */
  val original: PayloadRef? = null,
) : MediaMetadata

@Deprecated("Use ifd0.entries.exifIfd?.target", ReplaceWith("ifd0?.entries?.exifIfd?.target"))
val ExifMetadata.exifIfd: ExifIfd? get() = ifd0?.entries?.exifIfd?.target

@Deprecated("Use ifd0.entries.gpsIfd?.target", ReplaceWith("ifd0?.entries?.gpsIfd?.target"))
val ExifMetadata.gpsIfd: ExifIfd? get() = ifd0?.entries?.gpsIfd?.target

@Deprecated("Use exifIfd?.entries?.interoperabilityIfd?.target", ReplaceWith("exifIfd?.entries?.interoperabilityIfd?.target"))
val ExifMetadata.interopIfd: ExifIfd? get() = exifIfd?.entries?.interoperabilityIfd?.target

@Deprecated("Use ifd0?.nextIfd", ReplaceWith("ifd0?.nextIfd"))
val ExifMetadata.ifd1: ExifIfd? get() = ifd0?.nextIfd

// -- Byte order ---------------------------------------------------------------

@Serializable
enum class ExifByteOrder { LITTLE_ENDIAN, BIG_ENDIAN }

// -- IFD (Image File Directory) ----------------------------------------------

/**
 * A single TIFF Image File Directory.
 *
 * On disk:
 * `| entryCount (u16) | entries (12B each) | nextIfdOffset (u32) |`
 */
@Serializable
data class ExifIfd(
  /** Absolute offset of this IFD from the start of the TIFF stream. */
  val offset: UInt,
  /**
   * Parsed entries with structural pointers extracted into typed slots.
   *
   * All remaining non-structural entries (including unknown tags) are in [ExifIfdEntries.extra].
   */
  val entries: ExifIfdEntries,
  /** Next IFD pointer from this directory (often IFD1 for thumbnails from IFD0). */
  val nextIfdOffset: UInt = 0u,
  /** Parsed next IFD, if [nextIfdOffset] was non-zero and the target IFD existed. */
  val nextIfd: ExifIfd? = null,
)

/**
 * "Typed slots + extra list" wrapper for an IFD entry set.
 *
 * Pointer tags are structural and are elevated into typed links so consumers can
 * traverse the real on-disk hierarchy without scanning a flat entry list.
 */
@Serializable
data class ExifIfdEntries(
  /** Tag 0x8769 (34665): IFD0 -> Exif SubIFD. */
  val exifIfd: ExifIfdLink? = null,
  /** Tag 0x8825 (34853): IFD0 -> GPS IFD. */
  val gpsIfd: ExifIfdLink? = null,
  /** Tag 0xA005 (40965): Exif SubIFD -> Interoperability IFD. */
  val interoperabilityIfd: ExifIfdLink? = null,
  /** Tag 0x014A (330): SubIFDs (one or more offsets). */
  val subIfds: List<ExifIfdLink> = emptyList(),
  /**
   * Thumbnail reference (primarily IFD1):
   * - 0x0201 JPEGInterchangeFormat
   * - 0x0202 JPEGInterchangeFormatLength
   */
  val thumbnail: ExifThumbnailRef? = null,
  /** All other entries (including unknown/vendor-specific tags). Preserves original order. */
  val extra: List<ExifEntry> = emptyList(),
)

/**
 * A structural pointer tag linking to another IFD.
 *
 * The [pointerEntry] is preserved (tag/type/count/value) and the link target is nested.
 */
@Serializable
data class ExifIfdLink(
  val pointerEntry: ExifEntry,
  val targetOffset: UInt,
  val target: ExifIfd? = null,
)

/**
 * Reference to thumbnail data stored elsewhere in the TIFF stream.
 *
 * The thumbnail bytes are not embedded; writers can copy from [payload] when present,
 * or regenerate from scratch when edits require rewriting.
 */
@Serializable
data class ExifThumbnailRef(
  val jpegInterchangeFormat: ExifEntry? = null,
  val jpegInterchangeFormatLength: ExifEntry? = null,
  val payload: PayloadRef? = null,
)

// -- IFD Entry ----------------------------------------------------------------

@Serializable
data class ExifEntry(
  /** Raw 16-bit tag number. */
  val tag: UShort,
  /** Human-readable tag name if known, else null. */
  val tagName: String? = null,
  /** TIFF field type. */
  val type: ExifFieldType,
  /** Number of values (the TIFF "count" field). */
  val count: UInt,
  /** Decoded value(s) or opaque payload references. */
  val value: ExifValue,
  /**
   * Storage reference for the on-disk bytes backing this entry's value, when available.
   *
   * For inline values (value fits within the entry's 4-byte value field) this will
   * typically reference 4 bytes (including padding).
   */
  val stored: PayloadRef? = null,
)

// -- TIFF field types ---------------------------------------------------------

@Serializable
enum class ExifFieldType(val code: UShort, val bytesPerValue: UInt) {
  BYTE(1u, 1u),
  ASCII(2u, 1u),
  SHORT(3u, 2u),
  LONG(4u, 4u),
  RATIONAL(5u, 8u),
  SBYTE(6u, 1u),
  UNDEFINED(7u, 1u),
  SSHORT(8u, 2u),
  SLONG(9u, 4u),
  SRATIONAL(10u, 8u),
  FLOAT(11u, 4u),
  DOUBLE(12u, 8u),

  /** Placeholder for types not in the TIFF 6.0 / EXIF spec. */
  UNKNOWN(0u, 1u),
  ;

  companion object {
    fun fromCode(code: UShort): ExifFieldType = entries.find { it.code == code } ?: UNKNOWN
  }
}

// -- Value hierarchy ----------------------------------------------------------

/**
 * Typed EXIF entry value matching TIFF field semantics.
 *
 * This is designed to preserve unknown/opaque content:
 * - [Undefined] carries a [PayloadRef] so unknown binary values can be round-tripped.
 * - [Unknown] preserves non-standard field types by retaining both the type code and payload reference.
 */
@Serializable
sealed class ExifValue {
  /** ASCII string value (TIFF type 2). */
  @Serializable
  data class Ascii(val value: String) : ExifValue()

  /** Unsigned byte values (TIFF type 1). */
  @Serializable
  data class UBytes(val values: List<UByte>) : ExifValue()

  /** Signed byte values (TIFF type 6). */
  @Serializable
  data class SBytes(val values: List<Byte>) : ExifValue()

  /** Unsigned short values (TIFF type 3). */
  @Serializable
  data class UShorts(val values: List<UShort>) : ExifValue()

  /** Signed short values (TIFF type 8). */
  @Serializable
  data class Shorts(val values: List<Short>) : ExifValue()

  /** Unsigned long values (TIFF type 4). */
  @Serializable
  data class UInts(val values: List<UInt>) : ExifValue()

  /** Signed long values (TIFF type 9). */
  @Serializable
  data class Ints(val values: List<Int>) : ExifValue()

  /** Unsigned rationals (TIFF type 5). */
  @Serializable
  data class URationals(val values: List<ExifURational>) : ExifValue()

  /** Signed rationals (TIFF type 10). */
  @Serializable
  data class SRationals(val values: List<ExifSRational>) : ExifValue()

  /** IEEE 754 float values (TIFF type 11). */
  @Serializable
  data class Floats(val values: List<Float>) : ExifValue()

  /** IEEE 754 double values (TIFF type 12). */
  @Serializable
  data class Doubles(val values: List<Double>) : ExifValue()

  /** Opaque binary payload (TIFF type 7). */
  @Serializable
  data class Undefined(val payload: PayloadRef) : ExifValue()

  /** Non-standard field type; preserves the raw type code and payload. */
  @Serializable
  data class Unknown(val fieldTypeCode: UShort, val payload: PayloadRef) : ExifValue()
}

@Serializable
data class ExifURational(val numerator: UInt, val denominator: UInt) {
  init {
    require(denominator != 0u) { "ExifURational denominator must not be zero" }
  }
}

@Serializable
data class ExifSRational(val numerator: Int, val denominator: Int) {
  init {
    require(denominator != 0) { "ExifSRational denominator must not be zero" }
  }
}
