@file:Suppress("unused")

package dev.transmute.model.structure.image

import dev.transmute.model.core.BinarySerializable
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.Pixels
import dev.transmute.model.core.asBytes
import dev.transmute.model.structure.MediaStructure
import kotlinx.serialization.Serializable
import kotlin.math.abs

// --- Helpers — little-endian encoding ---

private fun UInt.toLittleEndianBytes(): ByteArray = byteArrayOf(
    this.toByte(),
    (this shr 8).toByte(),
    (this shr 16).toByte(),
    (this shr 24).toByte(),
)

private fun Int.toLittleEndianBytes(): ByteArray = toUInt().toLittleEndianBytes()

private fun UShort.toLittleEndianBytes(): ByteArray = byteArrayOf(
    this.toByte(),
    (this.toInt() shr 8).toByte(),
)

// --- BMP compression methods ---

/**
 * BMP compression method identifier.
 *
 * Stored as a little-endian UInt32 at offset 16 in the DIB header.
 */
@Serializable
enum class BmpCompression(val code: UInt) {
    /** No compression (BI_RGB). */
    Rgb(0u),
    /** RLE 8-bit (BI_RLE8). Only valid when bits-per-pixel = 8. */
    Rle8(1u),
    /** RLE 4-bit (BI_RLE4). Only valid when bits-per-pixel = 4. */
    Rle4(2u),
    /** Bit-field masks (BI_BITFIELDS). */
    Bitfields(3u),
    /** JPEG compression (BI_JPEG). */
    Jpeg(4u),
    /** PNG compression (BI_PNG). */
    Png(5u),
    /** Alpha bit-field masks (BI_ALPHABITFIELDS). */
    AlphaBitfields(6u);

    companion object {
        /** Resolve a raw compression code to a [BmpCompression], or `null` if unknown. */
        fun fromCode(code: UInt): BmpCompression? = entries.find { it.code == code }
    }
}

// --- BMP file header (14 bytes) ---

/**
 * The 14-byte BMP file header that appears at the very start of every BMP file.
 *
 * ```
 * | signature (2 B, "BM") | fileSize (4 B LE) | reserved1 (2 B) | reserved2 (2 B) | dataOffset (4 B LE) |
 * ```
 */
@Serializable
data class BmpFileHeader(
    /** Always `0x4D42` ("BM" in little-endian). */
    val signature: UShort = SIGNATURE,
    /** Total file size in bytes. */
    val fileSize: UInt,
    /** Reserved; must be zero. */
    val reserved1: UShort = 0u,
    /** Reserved; must be zero. */
    val reserved2: UShort = 0u,
    /** Byte offset from the start of the file to the pixel data. */
    val dataOffset: UInt,
) : BinarySerializable {

    override fun toBytes(): Bytes {
        val out = ByteArray(SIZE)
        signature.toLittleEndianBytes().copyInto(out, 0)
        fileSize.toLittleEndianBytes().copyInto(out, 2)
        reserved1.toLittleEndianBytes().copyInto(out, 6)
        reserved2.toLittleEndianBytes().copyInto(out, 8)
        dataOffset.toLittleEndianBytes().copyInto(out, 10)
        return out.asBytes()
    }

    companion object {
        /** Fixed size of the BMP file header in bytes. */
        const val SIZE = 14
        /** The BMP signature word (`0x4D42`, "BM"). */
        val SIGNATURE: UShort = 0x4D42u.toUShort()
    }
}

// --- DIB header (BITMAPINFOHEADER — 40 bytes minimum) ---

/**
 * The DIB (Device-Independent Bitmap) header that immediately follows
 * the [BmpFileHeader].
 *
 * This models the common BITMAPINFOHEADER layout (40 bytes). Larger
 * variants (BITMAPV4HEADER = 108 bytes, BITMAPV5HEADER = 124 bytes)
 * store the additional fields in [extraHeaderData].
 */
@Serializable
data class BmpDibHeader(
    /** Header size in bytes: 40 (INFO), 108 (V4), or 124 (V5). */
    val headerSize: UInt,
    /** Image width in pixels (signed). */
    val width: Int,
    /** Image height in pixels (signed). Negative means top-down row order. */
    val height: Int,
    /** Number of colour planes; always 1. */
    val planes: UShort = 1u,
    /** Bits per pixel: 1, 4, 8, 16, 24, or 32. */
    val bitsPerPixel: UShort,
    /** Compression method (see [BmpCompression]). */
    val compression: UInt = 0u,
    /** Image data size in bytes (may be 0 for [BmpCompression.Rgb]). */
    val imageSize: UInt = 0u,
    /** Horizontal resolution in pixels per metre. */
    val xPixelsPerMeter: Int = 0,
    /** Vertical resolution in pixels per metre. */
    val yPixelsPerMeter: Int = 0,
    /** Number of colours in the colour table (0 = max for bit depth). */
    val colorsUsed: UInt = 0u,
    /** Number of important colours (0 = all). */
    val colorsImportant: UInt = 0u,
    /** Extra header bytes beyond the base 40 (V4/V5 headers). */
    val extraHeaderData: Bytes = Bytes(ByteArray(0)),
) : BinarySerializable {

    override fun toBytes(): Bytes {
        val out = ByteArray(BITMAPINFOHEADER_SIZE + extraHeaderData.size)
        headerSize.toLittleEndianBytes().copyInto(out, 0)
        width.toLittleEndianBytes().copyInto(out, 4)
        height.toLittleEndianBytes().copyInto(out, 8)
        planes.toLittleEndianBytes().copyInto(out, 12)
        bitsPerPixel.toLittleEndianBytes().copyInto(out, 14)
        compression.toLittleEndianBytes().copyInto(out, 16)
        imageSize.toLittleEndianBytes().copyInto(out, 20)
        xPixelsPerMeter.toLittleEndianBytes().copyInto(out, 24)
        yPixelsPerMeter.toLittleEndianBytes().copyInto(out, 28)
        colorsUsed.toLittleEndianBytes().copyInto(out, 32)
        colorsImportant.toLittleEndianBytes().copyInto(out, 36)
        if (extraHeaderData.isNotEmpty()) {
            extraHeaderData.data.copyInto(out, BITMAPINFOHEADER_SIZE)
        }
        return out.asBytes()
    }

    companion object {
        /** Size of the standard BITMAPINFOHEADER (bytes). */
        const val BITMAPINFOHEADER_SIZE = 40
    }
}

// --- Colour-table entry (RGBQUAD — 4 bytes) ---

/**
 * A single colour-table entry (RGBQUAD) as stored in BMP files.
 *
 * ```
 * | blue (1 B) | green (1 B) | red (1 B) | reserved (1 B) |
 * ```
 */
@Serializable
data class BmpColorEntry(
    val blue: UByte,
    val green: UByte,
    val red: UByte,
    val reserved: UByte = 0u,
) : BinarySerializable {

    override fun toBytes(): Bytes =
        byteArrayOf(blue.toByte(), green.toByte(), red.toByte(), reserved.toByte()).asBytes()

    companion object {
        /** Size of one RGBQUAD entry (bytes). */
        const val SIZE = 4
    }
}

// --- BMP file — complete on-disk representation ---

/**
 * Canonical representation of a BMP file as written to disk.
 *
 * ```
 * | BmpFileHeader (14 B) | BmpDibHeader (40+ B) | Colour Table | Gap | Pixel Data |
 * ```
 *
 * The [gapData] field captures any bytes between the colour table and
 * the pixel data (e.g. ICC profile data in V5 bitmaps).  It is empty
 * for the vast majority of BMP files.
 */
@Serializable
data class Bmp(
    /** The 14-byte BMP file header. */
    val fileHeader: BmpFileHeader,
    /** The DIB (info) header. */
    val dibHeader: BmpDibHeader,
    /** Colour-table entries (RGBQUAD array). Empty for 16/24/32-bit images. */
    val colorTable: List<BmpColorEntry> = emptyList(),
    /** Bytes between the colour table and pixel data (usually empty). */
    val gapData: Bytes = Bytes(ByteArray(0)),
    /** Raw pixel data (rows padded to 4-byte boundaries). */
    val pixelData: Bytes,
) : MediaStructure {

    // --- Binary serialization ---

    override fun toBytes(): Bytes {
        val fh = fileHeader.toBytes()
        val dh = dibHeader.toBytes()
        val ctSize = colorTable.size * BmpColorEntry.SIZE
        val totalSize = fh.size + dh.size + ctSize + gapData.size + pixelData.size
        val out = ByteArray(totalSize)
        var pos = 0
        fh.data.copyInto(out, pos); pos += fh.size
        dh.data.copyInto(out, pos); pos += dh.size
        for (entry in colorTable) {
            val eb = entry.toBytes()
            eb.data.copyInto(out, pos)
            pos += BmpColorEntry.SIZE
        }
        if (gapData.isNotEmpty()) {
            gapData.data.copyInto(out, pos); pos += gapData.size
        }
        pixelData.data.copyInto(out, pos)
        return out.asBytes()
    }

    companion object {
        /** The 2-byte BMP file signature: `BM` (0x42 0x4D). */
        val SIGNATURE: ByteArray = byteArrayOf(0x42, 0x4D)
    }
}

// --- Typed extension accessors ---

/** Image width in pixels (always positive). */
val Bmp.width: Pixels get() = Pixels(abs(dibHeader.width))

/** Image height in pixels (always positive). */
val Bmp.height: Pixels get() = Pixels(abs(dibHeader.height))

/** `true` when rows are stored top-to-bottom (negative DIB height). */
val Bmp.isTopDown: Boolean get() = dibHeader.height < 0

/** Bits per pixel (1, 4, 8, 16, 24, or 32). */
val Bmp.bitsPerPixel: Int get() = dibHeader.bitsPerPixel.toInt()

/** Resolved compression method, or `null` for unknown codes. */
val Bmp.compression: BmpCompression? get() = BmpCompression.fromCode(dibHeader.compression)

/**
 * Number of bytes per pixel row, including padding to a 4-byte boundary.
 */
val Bmp.rowStride: Int
    get() {
        val rawRowBytes = (abs(dibHeader.width) * dibHeader.bitsPerPixel.toInt() + 7) / 8
        return (rawRowBytes + 3) and 0x7FFFFFFC // round up to multiple of 4
    }
