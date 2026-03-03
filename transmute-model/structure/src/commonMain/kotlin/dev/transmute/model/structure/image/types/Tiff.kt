@file:Suppress("unused")

package dev.transmute.model.structure.image.types

import dev.transmute.model.core.BinarySerializable
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.Pixels
import dev.transmute.model.core.asBytes
import dev.transmute.model.identify.Endianness
import dev.transmute.model.core.RawMediaStructure
import kotlinx.serialization.Serializable

// --- Helpers - byte-order-aware encoding ---

private fun UShort.toBytes(order: Endianness): ByteArray = when (order) {
    Endianness.Little -> byteArrayOf(this.toByte(), (this.toInt() shr 8).toByte())
    Endianness.Big    -> byteArrayOf((this.toInt() shr 8).toByte(), this.toByte())
}

private fun UInt.toBytes(order: Endianness): ByteArray = when (order) {
    Endianness.Little -> byteArrayOf(this.toByte(), (this shr 8).toByte(), (this shr 16).toByte(), (this shr 24).toByte())
    Endianness.Big    -> byteArrayOf((this shr 24).toByte(), (this shr 16).toByte(), (this shr 8).toByte(), this.toByte())
}

// --- TIFF IFD field types ---

/**
 * TIFF field (tag value) types as defined in the TIFF 6.0 specification.
 */
@Serializable
enum class TiffFieldType(val code: UShort, val bytesPerValue: Int) {
    Byte(1u, 1),
    Ascii(2u, 1),
    Short(3u, 2),
    Long(4u, 4),
    Rational(5u, 8),
    SByte(6u, 1),
    Undefined(7u, 1),
    SShort(8u, 2),
    SLong(9u, 4),
    SRational(10u, 8),
    Float(11u, 4),
    Double(12u, 8);

    companion object {
        fun fromCode(code: UShort): TiffFieldType? = entries.find { it.code == code }
    }
}

// --- Well-known TIFF tag IDs ---

/**
 * Commonly used TIFF tag identifiers.
 */
@Serializable
enum class TiffTag(val code: UShort) {
    ImageWidth(256u),
    ImageLength(257u),
    BitsPerSample(258u),
    Compression(259u),
    PhotometricInterpretation(262u),
    StripOffsets(273u),
    SamplesPerPixel(277u),
    RowsPerStrip(278u),
    StripByteCounts(279u),
    XResolution(282u),
    YResolution(283u),
    ResolutionUnit(296u),
    TileWidth(322u),
    TileLength(323u),
    TileOffsets(324u),
    TileByteCounts(325u),
    ExifIfd(34665u),
    GpsIfd(34853u);

    companion object {
        fun fromCode(code: UShort): TiffTag? = entries.find { it.code == code }
    }
}

// --- TIFF IFD entry (12 bytes on disk) ---

/**
 * A single Image File Directory entry with its resolved value data.
 *
 * On disk each entry is 12 bytes:
 * ```
 * | tag (2 B) | type (2 B) | count (4 B) | valueOrOffset (4 B) |
 * ```
 *
 * When the total value size (count x type size) exceeds 4 bytes, the
 * last 4 bytes are an offset to elsewhere in the file.  In this model
 * the actual value bytes are always resolved into [data].
 */
@Serializable
data class TiffIfdEntry(
    /** Tag identifier (see [TiffTag]). */
    val tag: UShort,
    /** Field type code (see [TiffFieldType]). */
    val fieldType: UShort,
    /** Number of values. */
    val count: UInt,
    /** The raw 4-byte value / offset field as stored on disk. */
    val valueOrOffset: Bytes,
    /** Resolved value data (may be longer than 4 bytes). */
    val data: Bytes = valueOrOffset,
) : BinarySerializable {

    /** Resolved TIFF tag, or `null` if the code is unrecognised. */
    val resolvedTag: TiffTag? get() = TiffTag.fromCode(tag)

    /** Resolved field type, or `null` if the code is unrecognised. */
    val resolvedFieldType: TiffFieldType? get() = TiffFieldType.fromCode(fieldType)

    override fun toBytes(): Bytes {
        // Always emits the 12-byte on-disk form (value / offset not re-laid out).
        val out = ByteArray(ENTRY_SIZE)
        // Use big-endian by default; the file-level toBytes handles byte order
        out[0] = (tag.toInt() shr 8).toByte(); out[1] = tag.toByte()
        out[2] = (fieldType.toInt() shr 8).toByte(); out[3] = fieldType.toByte()
        val c = count
        out[4] = (c shr 24).toByte(); out[5] = (c shr 16).toByte()
        out[6] = (c shr 8).toByte(); out[7] = c.toByte()
        valueOrOffset.data.copyInto(out, 8, 0, minOf(4, valueOrOffset.size))
        return out.asBytes()
    }

    companion object {
        const val ENTRY_SIZE = 12
    }
}

// --- TIFF IFD ---

/**
 * A single Image File Directory (IFD).
 *
 * ```
 * | entryCount (2 B) | entry1 (12 B) | ... | entryn (12 B) | nextIfdOffset (4 B) |
 * ```
 */
@Serializable
data class TiffIfd(
    /** Offset of this IFD from the start of the file, in bytes. */
    val offset: UInt,
    val entries: List<TiffIfdEntry>,
    val nextIfdOffset: UInt = 0u,
)

// --- TIFF file - complete on-disk representation ---

/**
 * Canonical representation of a TIFF file as written to disk.
 *
 * ```
 * | byteOrder (2 B) | magic 42 (2 B) | firstIfdOffset (4 B) | IFDs & data ... |
 * ```
 *
 * Image strip and tile data is stored in [imageData].  Any remaining
 * bytes that are not part of the header, IFDs or image data are
 * captured in [extraData] for round-trip fidelity.
 */
@Serializable
data class TiffRaw(
    /** Byte order: II (little-endian) or MM (big-endian). */
    val byteOrder: Endianness,
    /** Offset of the first IFD (from start of file). */
    val firstIfdOffset: UInt,
    /** All IFDs in the file (IFD 0, IFD 1, Exif IFD, GPS IFD, ...). */
    val ifds: List<TiffIfd> = emptyList(),
    /** Concatenated image strip / tile data. */
    val imageData: Bytes = Bytes(ByteArray(0)),
    /** Any extra data regions for round-trip fidelity. */
    val extraData: Bytes = Bytes(ByteArray(0)),
) : RawMediaStructure {

    // --- Binary serialization ---

    override fun toBytes(): Bytes {
        // Header: byte order (2) + magic (2) + firstIfdOffset (4) = 8 bytes
        val boBytes = when (byteOrder) {
            Endianness.Little -> byteArrayOf(0x49, 0x49) // "II"
            Endianness.Big    -> byteArrayOf(0x4D, 0x4D) // "MM"
        }
        val magic = 42.toUShort().toBytes(byteOrder)
        val ifdOff = firstIfdOffset.toBytes(byteOrder)

        // IFD blocks
        val ifdParts = ifds.map { ifd ->
            val count = ifd.entries.size.toUShort().toBytes(byteOrder)
            val entryBytes = ifd.entries.map { entry ->
                val buf = ByteArray(TiffIfdEntry.ENTRY_SIZE)
                entry.tag.toBytes(byteOrder).copyInto(buf, 0)
                entry.fieldType.toBytes(byteOrder).copyInto(buf, 2)
                entry.count.toBytes(byteOrder).copyInto(buf, 4)
                entry.valueOrOffset.data.copyInto(buf, 8, 0, minOf(4, entry.valueOrOffset.size))
                buf
            }
            val nextOff = ifd.nextIfdOffset.toBytes(byteOrder)
            val total = count.size + entryBytes.sumOf { it.size } + nextOff.size
            val buf = ByteArray(total)
            var pos = 0
            count.copyInto(buf, pos); pos += count.size
            for (eb in entryBytes) { eb.copyInto(buf, pos); pos += eb.size }
            nextOff.copyInto(buf, pos)
            buf
        }

        val headerSize = 8
        val ifdTotal = ifdParts.sumOf { it.size }
        val totalSize = headerSize + ifdTotal + imageData.size + extraData.size
        val out = ByteArray(totalSize)
        var pos = 0
        boBytes.copyInto(out, pos); pos += 2
        magic.copyInto(out, pos); pos += 2
        ifdOff.copyInto(out, pos); pos += 4
        for (part in ifdParts) { part.copyInto(out, pos); pos += part.size }
        imageData.data.copyInto(out, pos); pos += imageData.size
        extraData.data.copyInto(out, pos)
        return out.asBytes()
    }

    companion object {
        /** TIFF magic number (42). */
        const val MAGIC: UShort = 42u
        /** Little-endian byte-order mark. */
        val BYTE_ORDER_LE: ByteArray = byteArrayOf(0x49, 0x49)  // "II"
        /** Big-endian byte-order mark. */
        val BYTE_ORDER_BE: ByteArray = byteArrayOf(0x4D, 0x4D)  // "MM"
    }
}

// --- Typed extension accessors ---

private fun TiffRaw.tiffReadU16(d: ByteArray, off: Int): Int = when (byteOrder) {
    Endianness.Little -> (d[off].toInt() and 0xFF) or ((d[off + 1].toInt() and 0xFF) shl 8)
    Endianness.Big    -> ((d[off].toInt() and 0xFF) shl 8) or (d[off + 1].toInt() and 0xFF)
}

private fun TiffRaw.tiffReadU32(d: ByteArray, off: Int): UInt = when (byteOrder) {
    Endianness.Little ->
        (d[off].toUInt() and 0xFFu) or
        ((d[off+1].toUInt() and 0xFFu) shl 8) or
        ((d[off+2].toUInt() and 0xFFu) shl 16) or
        ((d[off+3].toUInt() and 0xFFu) shl 24)
    Endianness.Big ->
        ((d[off].toUInt() and 0xFFu) shl 24) or
        ((d[off+1].toUInt() and 0xFFu) shl 16) or
        ((d[off+2].toUInt() and 0xFFu) shl 8) or
        (d[off+3].toUInt() and 0xFFu)
}

private fun TiffRaw.findIfd0TagValue(tagCode: UShort): Int? {
    val entry = ifds.firstOrNull()?.entries?.find { it.tag == tagCode } ?: return null
    val d = entry.data.data
    return when {
        entry.fieldType == TiffFieldType.Short.code && d.size >= 2 -> tiffReadU16(d, 0)
        entry.fieldType == TiffFieldType.Long.code && d.size >= 4  -> tiffReadU32(d, 0).toInt()
        else -> null
    }
}

private fun TiffRaw.readIntValues(entry: TiffIfdEntry, ft: TiffFieldType): List<Int> {
    val d = entry.data.data
    val count = entry.count.toInt()
    return (0 until count).mapNotNull { i ->
        when (ft) {
            TiffFieldType.Short -> {
                val off = i * 2
                if (off + 1 < d.size) tiffReadU16(d, off) else null
            }
            TiffFieldType.Long -> {
                val off = i * 4
                if (off + 3 < d.size) tiffReadU32(d, off).toInt() else null
            }
            else -> null
        }
    }
}

/** Image width from IFD 0 tag 256 (ImageWidth). */
val TiffRaw.width: Pixels?
    get() = findIfd0TagValue(TiffTag.ImageWidth.code)?.let { Pixels(it) }

/** Image height from IFD 0 tag 257 (ImageLength). */
val TiffRaw.height: Pixels?
    get() = findIfd0TagValue(TiffTag.ImageLength.code)?.let { Pixels(it) }

/** Bits per sample from IFD 0 tag 258. */
val TiffRaw.bitsPerSample: List<Int>
    get() {
        val entry = ifds.firstOrNull()?.entries?.find { it.tag == TiffTag.BitsPerSample.code } ?: return emptyList()
        val ft = TiffFieldType.fromCode(entry.fieldType) ?: return emptyList()
        return readIntValues(entry, ft)
    }

/** Compression scheme from IFD 0 tag 259. */
val TiffRaw.compression: Int?
    get() = findIfd0TagValue(TiffTag.Compression.code)
