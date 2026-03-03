@file:Suppress("unused")

package dev.transmute.model.metadata.exif

import dev.transmute.model.core.MediaMetadata
import kotlinx.serialization.Serializable

// -- Top-level model ----------------------------------------------------------

/**
 * EXIF metadata as a typed TIFF/IFD hierarchy.
 *
 * Models the on-disk structure faithfully:
 * - A TIFF header (byte order, magic 42)
 * - IFD0 (primary image directory)
 *   - ExifIFD sub-directory (camera settings)
 *   - GPS IFD sub-directory
 *   - Interoperability IFD sub-directory
 * - IFD1 (thumbnail directory, if present)
 *
 * Arbitrary payload data (e.g. embedded thumbnails, MakerNote blobs) is
 * summarised by size only - never inlined as raw bytes.
 */
@Serializable
data class ExifMetadata(
    val byteOrder: ExifByteOrder,
    val ifd0: ExifIfd? = null,
    val exifIfd: ExifIfd? = null,
    val gpsIfd: ExifIfd? = null,
    val interopIfd: ExifIfd? = null,
    val ifd1: ExifIfd? = null,
    val thumbnailOffset: Long? = null,
    val thumbnailLength: Long? = null,
) : MediaMetadata

// -- Byte order ---------------------------------------------------------------

@Serializable
enum class ExifByteOrder {
    LITTLE_ENDIAN,
    BIG_ENDIAN,
}

// -- IFD (Image File Directory) -----------------------------------------------

@Serializable
data class ExifIfd(
    val entries: List<ExifEntry>,
)

// -- IFD Entry ----------------------------------------------------------------

@Serializable
data class ExifEntry(
    /** Raw 16-bit tag number. */
    val tag: Int,
    /** Human-readable tag name if known (`null` for vendor-specific tags). */
    val tagName: String? = null,
    /** TIFF field type. */
    val type: ExifFieldType,
    /** Number of values (the TIFF "count" field). */
    val count: Long,
    /** Decoded value(s). */
    val value: ExifValue,
)

// -- TIFF field types ---------------------------------------------------------

@Serializable
enum class ExifFieldType(val code: Int) {
    BYTE(1),
    ASCII(2),
    SHORT(3),
    LONG(4),
    RATIONAL(5),
    SBYTE(6),
    UNDEFINED(7),
    SSHORT(8),
    SLONG(9),
    SRATIONAL(10),
    FLOAT(11),
    DOUBLE(12),
    /** Placeholder for types not in the TIFF 6.0 / EXIF 2.32 spec. */
    UNKNOWN(0);

    companion object {
        fun fromCode(code: Int): ExifFieldType =
            entries.find { it.code == code } ?: UNKNOWN
    }
}

// -- Value hierarchy ----------------------------------------------------------

/**
 * Typed EXIF entry value, mirroring TIFF field semantics.
 *
 * Large opaque payloads (MakerNote, JPEG thumbnails) are represented as
 * [Blob] with a size only.
 */
@Serializable
sealed class ExifValue {
    /** ASCII string value (TIFF type 2). */
    @Serializable
    data class Text(val value: String) : ExifValue()

    /** One or more integer values (TIFF types 1, 3, 4, 6, 8, 9). */
    @Serializable
    data class Integers(val values: List<Long>) : ExifValue()

    /** One or more rational values (TIFF types 5, 10). */
    @Serializable
    data class Rationals(val values: List<ExifRational>) : ExifValue()

    /** One or more floating-point values (TIFF types 11, 12). */
    @Serializable
    data class Floats(val values: List<Double>) : ExifValue()

    /**
     * Opaque binary data summarised by size (MakerNote, thumbnails, etc.).
     * Raw bytes are never included.
     */
    @Serializable
    data class Blob(val sizeBytes: Long) : ExifValue()
}

@Serializable
data class ExifRational(
    val numerator: Long,
    val denominator: Long,
) {
    override fun toString(): String =
        if (denominator == 1L) "$numerator" else "$numerator/$denominator"
}
