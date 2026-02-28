@file:Suppress("unused")

package dev.transmute.model.structure.image

import dev.transmute.model.core.BinarySerializable
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes
import dev.transmute.model.identify.FourCC
import dev.transmute.model.core.RawMediaStructure
import kotlinx.serialization.Serializable

// --- Helpers — big-endian encoding ---

private fun UInt.toBigEndianBytes(): ByteArray = byteArrayOf(
    (this shr 24).toByte(),
    (this shr 16).toByte(),
    (this shr 8).toByte(),
    this.toByte(),
)

private fun UShort.toBigEndianBytes(): ByteArray = byteArrayOf(
    (this.toInt() shr 8).toByte(),
    this.toByte(),
)

private fun FourCC.toByteArray(): ByteArray =
    value.encodeToByteArray()   // always 4 ASCII bytes

// --- PNG chunk — the fundamental structural unit ---

/**
 * A single PNG chunk as it appears on disk.
 *
 * ```
 * | length (4 B, big-endian) | type (4 B) | data (length B) | crc (4 B) |
 * ```
 *
 * [length] counts only the [data] field; it does **not** include the
 * type or CRC bytes.  [crc] is a CRC-32 computed over [type] + [data].
 */
@Serializable
data class PngChunk(
    /** Data-field length in bytes (big-endian UInt32 on disk). */
    val length: UInt,
    /** 4-byte ASCII chunk type code (e.g. `IHDR`, `IDAT`, `IEND`). */
    val type: FourCC,
    /** Raw chunk data — exactly [length] bytes. */
    val data: Bytes,
    /** CRC-32 over [type] + [data]. */
    val crc: UInt,
) : BinarySerializable {

    override fun toBytes(): Bytes {
        val out = ByteArray(4 + 4 + data.size + 4)
        val lenBytes = length.toBigEndianBytes()
        val typeBytes = type.toByteArray()
        val crcBytes = crc.toBigEndianBytes()
        lenBytes.copyInto(out, 0)
        typeBytes.copyInto(out, 4)
        data.data.copyInto(out, 8)
        crcBytes.copyInto(out, 8 + data.size)
        return out.asBytes()
    }
}

// --- Well-known chunk type tags ---

/**
 * Well-known PNG chunk types defined by the PNG specification and
 * common extensions (APNG).
 */
@Serializable
enum class PngChunkType(val tag: String) {
    // Critical
    IHDR("IHDR"),
    PLTE("PLTE"),
    IDAT("IDAT"),
    IEND("IEND"),

    // Ancillary – color space
    cHRM("cHRM"),
    gAMA("gAMA"),
    iCCP("iCCP"),
    sBIT("sBIT"),
    sRGB("sRGB"),

    // Ancillary – transparency & background
    bKGD("bKGD"),
    hIST("hIST"),
    tRNS("tRNS"),

    // Ancillary – layout
    pHYs("pHYs"),
    sPLT("sPLT"),

    // Ancillary – time
    tIME("tIME"),

    // Ancillary – text
    iTXt("iTXt"),
    tEXt("tEXt"),
    zTXt("zTXt"),

    // APNG extension
    acTL("acTL"),
    fcTL("fcTL"),
    fdAT("fdAT");

    /** Critical chunks have an uppercase first letter in their tag. */
    val isCritical: Boolean get() = tag[0].isUpperCase()

    /** Public (registered) chunks have an uppercase second letter. */
    val isPublic: Boolean get() = tag[1].isUpperCase()

    val fourCC: FourCC get() = FourCC(tag)

    companion object {
        fun fromTag(tag: String): PngChunkType? = entries.find { it.tag == tag }
    }
}

// --- IHDR — Image Header (13 bytes) ---

/**
 * PNG color type (1 byte in the IHDR chunk).
 */
@Serializable
enum class PngColorType(val code: Int) {
    Grayscale(0),
    Rgb(2),
    Indexed(3),
    GrayscaleAlpha(4),
    RgbAlpha(6);

    val channelCount: Int
        get() = when (this) {
            Grayscale -> 1
            Rgb -> 3
            Indexed -> 1
            GrayscaleAlpha -> 2
            RgbAlpha -> 4
        }

    val hasAlpha: Boolean
        get() = this == GrayscaleAlpha || this == RgbAlpha

    companion object {
        fun fromCode(code: Int): PngColorType? = entries.find { it.code == code }
    }
}

/**
 * PNG interlace method (1 byte in the IHDR chunk).
 */
@Serializable
enum class PngInterlaceMethod(val code: Int) {
    None(0),
    Adam7(1);

    companion object {
        fun fromCode(code: Int): PngInterlaceMethod? = entries.find { it.code == code }
    }
}

/**
 * Parsed IHDR chunk data — always the first chunk, always 13 bytes.
 *
 * ```
 * | width (4 B) | height (4 B) | bitDepth (1 B) | colorType (1 B) |
 * | compressionMethod (1 B) | filterMethod (1 B) | interlaceMethod (1 B) |
 * ```
 */
@Serializable
data class PngIhdr(
    /** Image width in pixels (big-endian UInt32). */
    val width: UInt,
    /** Image height in pixels (big-endian UInt32). */
    val height: UInt,
    /** Bit depth: 1, 2, 4, 8, or 16. */
    val bitDepth: UByte,
    /** Color type. */
    val colorType: PngColorType,
    /** Compression method (always 0 = deflate/inflate). */
    val compressionMethod: UByte,
    /** Filter method (always 0 = adaptive filtering). */
    val filterMethod: UByte,
    /** Interlace method. */
    val interlaceMethod: PngInterlaceMethod,
) : BinarySerializable {

    /** Bits per pixel = bitDepth × channels. */
    val bitsPerPixel: Int get() = bitDepth.toInt() * colorType.channelCount

    /**
     * Serializes to the 13-byte IHDR data payload (not the full chunk).
     */
    override fun toBytes(): Bytes {
        val out = ByteArray(13)
        width.toBigEndianBytes().copyInto(out, 0)
        height.toBigEndianBytes().copyInto(out, 4)
        out[8] = bitDepth.toByte()
        out[9] = colorType.code.toByte()
        out[10] = compressionMethod.toByte()
        out[11] = filterMethod.toByte()
        out[12] = interlaceMethod.code.toByte()
        return out.asBytes()
    }
}

// --- PLTE — Palette (3 × N bytes, N ≤ 256) ---

/**
 * A single palette entry: 3 bytes (R, G, B).
 */
@Serializable
data class PngPlteEntry(
    val red: UByte,
    val green: UByte,
    val blue: UByte,
) : BinarySerializable {
    override fun toBytes(): Bytes = byteArrayOf(red.toByte(), green.toByte(), blue.toByte()).asBytes()
}

/**
 * Parsed PLTE chunk data.
 */
@Serializable
data class PngPlte(
    val entries: List<PngPlteEntry>,
) : BinarySerializable {
    override fun toBytes(): Bytes {
        val out = ByteArray(entries.size * 3)
        entries.forEachIndexed { i, e ->
            out[i * 3] = e.red.toByte()
            out[i * 3 + 1] = e.green.toByte()
            out[i * 3 + 2] = e.blue.toByte()
        }
        return out.asBytes()
    }
}

// --- IDAT — Image Data ---

/**
 * Parsed IDAT chunk data — compressed (deflate) image data.
 *
 * A PNG file may contain multiple IDAT chunks whose data must be
 * concatenated to form the complete compressed datastream. Each
 * [PngIdat] corresponds to exactly one IDAT chunk on disk.
 */
@Serializable
data class PngIdat(
    /** Compressed image data from a single IDAT chunk. */
    val compressedData: Bytes,
) : BinarySerializable {
    override fun toBytes(): Bytes = Bytes(compressedData.data.copyOf())
}

// --- IEND — Image Trailer ---

/**
 * The IEND chunk marks the end of the PNG datastream.
 * It carries zero bytes of data.
 */
@Serializable
object PngIend : BinarySerializable {
    override fun toBytes(): Bytes = Bytes(ByteArray(0))
}

// --- tRNS — Transparency ---

/**
 * Parsed tRNS chunk data.
 *
 * Interpretation depends on the IHDR color type:
 * - Grayscale: [greySample] is set (2 bytes).
 * - RGB: [redSample], [greenSample], [blueSample] are set (6 bytes).
 * - Indexed: [alphaEntries] lists alpha values for palette indices.
 */
@Serializable
data class PngTrns(
    val greySample: UShort? = null,
    val redSample: UShort? = null,
    val greenSample: UShort? = null,
    val blueSample: UShort? = null,
    val alphaEntries: List<UByte>? = null,
) : BinarySerializable {
    override fun toBytes(): Bytes = when {
        alphaEntries != null -> ByteArray(alphaEntries.size) { alphaEntries[it].toByte() }
        greySample != null -> greySample.toBigEndianBytes()
        redSample != null -> {
            val out = ByteArray(6)
            redSample.toBigEndianBytes().copyInto(out, 0)
            (greenSample ?: 0u.toUShort()).toBigEndianBytes().copyInto(out, 2)
            (blueSample ?: 0u.toUShort()).toBigEndianBytes().copyInto(out, 4)
            out
        }
        else -> ByteArray(0)
    }.asBytes()
}

// --- gAMA — Image Gamma (4 bytes) ---

/**
 * Parsed gAMA chunk data.
 *
 * ```
 * | gamma (4 B, UInt32) |
 * ```
 *
 * The value is the gamma times 100 000 (e.g. 45 455 → gamma ≈ 1/2.2).
 */
@Serializable
data class PngGama(
    /** Gamma × 100 000 as stored on disk. */
    val gamma: UInt,
) : BinarySerializable {
    /** Decoded gamma value as a floating-point number. */
    val gammaValue: Double get() = gamma.toDouble() / 100_000.0

    override fun toBytes(): Bytes = gamma.toBigEndianBytes().asBytes()
}

// --- cHRM — Primary Chromaticities and White Point (32 bytes) ---

/**
 * Parsed cHRM chunk data — eight UInt32 values, each × 100 000.
 *
 * ```
 * | whitePointX (4 B) | whitePointY (4 B) |
 * | redX (4 B) | redY (4 B) | greenX (4 B) | greenY (4 B) |
 * | blueX (4 B) | blueY (4 B) |
 * ```
 */
@Serializable
data class PngChrm(
    val whitePointX: UInt,
    val whitePointY: UInt,
    val redX: UInt,
    val redY: UInt,
    val greenX: UInt,
    val greenY: UInt,
    val blueX: UInt,
    val blueY: UInt,
) : BinarySerializable {
    override fun toBytes(): Bytes {
        val out = ByteArray(32)
        var offset = 0
        for (v in listOf(whitePointX, whitePointY, redX, redY, greenX, greenY, blueX, blueY)) {
            v.toBigEndianBytes().copyInto(out, offset)
            offset += 4
        }
        return out.asBytes()
    }
}

// --- sRGB — Standard RGB Colour Space (1 byte) ---

/**
 * sRGB rendering intent.
 */
@Serializable
enum class PngRenderingIntent(val code: Int) {
    Perceptual(0),
    RelativeColorimetric(1),
    Saturation(2),
    AbsoluteColorimetric(3);

    companion object {
        fun fromCode(code: Int): PngRenderingIntent? = entries.find { it.code == code }
    }
}

/**
 * Parsed sRGB chunk data.
 *
 * ```
 * | renderingIntent (1 B) |
 * ```
 */
@Serializable
data class PngSrgb(
    val renderingIntent: PngRenderingIntent,
) : BinarySerializable {
    override fun toBytes(): Bytes = byteArrayOf(renderingIntent.code.toByte()).asBytes()
}

// --- iCCP — Embedded ICC Profile ---

/**
 * Parsed iCCP chunk data.
 *
 * ```
 * | profileName (1–79 B, Latin-1) | null (1 B) | compressionMethod (1 B) | compressedProfile (rest) |
 * ```
 */
@Serializable
data class PngIccp(
    val profileName: String,
    val compressionMethod: UByte,
    val compressedProfile: Bytes,
) : BinarySerializable {
    override fun toBytes(): Bytes {
        val name = profileName.encodeToByteArray()
        val out = ByteArray(name.size + 1 + 1 + compressedProfile.size)
        name.copyInto(out, 0)
        out[name.size] = 0 // null separator
        out[name.size + 1] = compressionMethod.toByte()
        compressedProfile.data.copyInto(out, name.size + 2)
        return out.asBytes()
    }
}

// --- pHYs — Physical Pixel Dimensions (9 bytes) ---

/**
 * Parsed pHYs chunk data.
 *
 * ```
 * | pixelsPerUnitX (4 B) | pixelsPerUnitY (4 B) | unitSpecifier (1 B) |
 * ```
 */
@Serializable
data class PngPhys(
    val pixelsPerUnitX: UInt,
    val pixelsPerUnitY: UInt,
    /** 0 = unit unknown, 1 = metre. */
    val unitSpecifier: UByte,
) : BinarySerializable {
    val isMetric: Boolean get() = unitSpecifier == 1.toUByte()

    override fun toBytes(): Bytes {
        val out = ByteArray(9)
        pixelsPerUnitX.toBigEndianBytes().copyInto(out, 0)
        pixelsPerUnitY.toBigEndianBytes().copyInto(out, 4)
        out[8] = unitSpecifier.toByte()
        return out.asBytes()
    }
}

// --- tIME — Image Last-Modification Time (7 bytes) ---

/**
 * Parsed tIME chunk data.
 *
 * ```
 * | year (2 B) | month (1 B) | day (1 B) | hour (1 B) | minute (1 B) | second (1 B) |
 * ```
 */
@Serializable
data class PngTime(
    val year: UShort,
    val month: UByte,
    val day: UByte,
    val hour: UByte,
    val minute: UByte,
    val second: UByte,
) : BinarySerializable {
    override fun toBytes(): Bytes {
        val out = ByteArray(7)
        year.toBigEndianBytes().copyInto(out, 0)
        out[2] = month.toByte()
        out[3] = day.toByte()
        out[4] = hour.toByte()
        out[5] = minute.toByte()
        out[6] = second.toByte()
        return out.asBytes()
    }
}

// --- tEXt — Textual Data ---

/**
 * Parsed tEXt chunk data.
 *
 * ```
 * | keyword (1–79 B, Latin-1) | null (1 B) | text (rest, Latin-1) |
 * ```
 */
@Serializable
data class PngTextChunk(
    val keyword: String,
    val text: String,
) : BinarySerializable {
    override fun toBytes(): Bytes {
        val kw = keyword.encodeToByteArray()
        val txt = text.encodeToByteArray()
        val out = ByteArray(kw.size + 1 + txt.size)
        kw.copyInto(out, 0)
        out[kw.size] = 0 // null separator
        txt.copyInto(out, kw.size + 1)
        return out.asBytes()
    }
}

// --- zTXt — Compressed Textual Data ---

/**
 * Parsed zTXt chunk data.
 *
 * ```
 * | keyword (1–79 B, Latin-1) | null (1 B) | compressionMethod (1 B) | compressedText (rest) |
 * ```
 */
@Serializable
data class PngZtxt(
    val keyword: String,
    val compressionMethod: UByte,
    val compressedText: Bytes,
) : BinarySerializable {
    override fun toBytes(): Bytes {
        val kw = keyword.encodeToByteArray()
        val out = ByteArray(kw.size + 1 + 1 + compressedText.size)
        kw.copyInto(out, 0)
        out[kw.size] = 0 // null separator
        out[kw.size + 1] = compressionMethod.toByte()
        compressedText.data.copyInto(out, kw.size + 2)
        return out.asBytes()
    }
}

// --- iTXt — International Textual Data ---

/**
 * Parsed iTXt chunk data.
 *
 * ```
 * | keyword (1–79 B) | null | compressionFlag (1 B) | compressionMethod (1 B) |
 * | languageTag (ASCII) | null | translatedKeyword (UTF-8) | null | text (UTF-8) |
 * ```
 */
@Serializable
data class PngItxt(
    val keyword: String,
    val compressionFlag: UByte,
    val compressionMethod: UByte,
    val languageTag: String,
    val translatedKeyword: String,
    val text: String,
) : BinarySerializable {
    override fun toBytes(): Bytes {
        val kw = keyword.encodeToByteArray()
        val lang = languageTag.encodeToByteArray()
        val trKw = translatedKeyword.encodeToByteArray()
        val txt = text.encodeToByteArray()
        val size = kw.size + 1 + 1 + 1 + lang.size + 1 + trKw.size + 1 + txt.size
        val out = ByteArray(size)
        var pos = 0
        kw.copyInto(out, pos); pos += kw.size
        out[pos++] = 0 // null
        out[pos++] = compressionFlag.toByte()
        out[pos++] = compressionMethod.toByte()
        lang.copyInto(out, pos); pos += lang.size
        out[pos++] = 0 // null
        trKw.copyInto(out, pos); pos += trKw.size
        out[pos++] = 0 // null
        txt.copyInto(out, pos)
        return out.asBytes()
    }
}

// --- sBIT — Significant Bits ---

/**
 * Parsed sBIT chunk data.
 *
 * Number of entries depends on color type:
 * - Grayscale: 1 byte
 * - RGB / Indexed: 3 bytes
 * - Grayscale+Alpha: 2 bytes
 * - RGBA: 4 bytes
 */
@Serializable
data class PngSbit(
    val significantBits: List<UByte>,
) : BinarySerializable {
    override fun toBytes(): Bytes = ByteArray(significantBits.size) { significantBits[it].toByte() }.asBytes()
}

// --- bKGD — Background Colour ---

/**
 * Parsed bKGD chunk data.
 *
 * Interpretation depends on color type:
 * - Indexed: [paletteIndex] (1 byte)
 * - Grayscale / GrayscaleAlpha: [grey] (2 bytes)
 * - RGB / RGBA: [red], [green], [blue] (6 bytes)
 */
@Serializable
data class PngBkgd(
    val paletteIndex: UByte? = null,
    val grey: UShort? = null,
    val red: UShort? = null,
    val green: UShort? = null,
    val blue: UShort? = null,
) : BinarySerializable {
    override fun toBytes(): Bytes = when {
        paletteIndex != null -> byteArrayOf(paletteIndex.toByte())
        grey != null -> grey.toBigEndianBytes()
        red != null -> {
            val out = ByteArray(6)
            red.toBigEndianBytes().copyInto(out, 0)
            (green ?: 0u.toUShort()).toBigEndianBytes().copyInto(out, 2)
            (blue ?: 0u.toUShort()).toBigEndianBytes().copyInto(out, 4)
            out
        }
        else -> ByteArray(0)
    }.asBytes()
}

// --- hIST — Palette Histogram ---

/**
 * Parsed hIST chunk data — one UShort frequency per palette entry.
 */
@Serializable
data class PngHist(
    val frequencies: List<UShort>,
) : BinarySerializable {
    override fun toBytes(): Bytes {
        val out = ByteArray(frequencies.size * 2)
        frequencies.forEachIndexed { i, f ->
            f.toBigEndianBytes().copyInto(out, i * 2)
        }
        return out.asBytes()
    }
}

// --- sPLT — Suggested Palette ---

/**
 * A single entry in a suggested palette.
 */
@Serializable
data class PngSpltEntry(
    val red: UShort,
    val green: UShort,
    val blue: UShort,
    val alpha: UShort,
    val frequency: UShort,
)

/**
 * Parsed sPLT chunk data.
 *
 * ```
 * | paletteName (Latin-1) | null (1 B) | sampleDepth (1 B) | entries… |
 * ```
 */
@Serializable
data class PngSplt(
    val paletteName: String,
    val sampleDepth: UByte,
    val entries: List<PngSpltEntry>,
) : BinarySerializable {
    override fun toBytes(): Bytes {
        val name = paletteName.encodeToByteArray()
        val bytesPerEntry = if (sampleDepth.toInt() == 8) 6 else 10
        val out = ByteArray(name.size + 1 + 1 + entries.size * bytesPerEntry)
        var pos = 0
        name.copyInto(out, pos); pos += name.size
        out[pos++] = 0 // null
        out[pos++] = sampleDepth.toByte()
        for (e in entries) {
            if (sampleDepth.toInt() == 8) {
                out[pos++] = e.red.toByte()
                out[pos++] = e.green.toByte()
                out[pos++] = e.blue.toByte()
                out[pos++] = e.alpha.toByte()
            } else {
                e.red.toBigEndianBytes().copyInto(out, pos); pos += 2
                e.green.toBigEndianBytes().copyInto(out, pos); pos += 2
                e.blue.toBigEndianBytes().copyInto(out, pos); pos += 2
                e.alpha.toBigEndianBytes().copyInto(out, pos); pos += 2
            }
            e.frequency.toBigEndianBytes().copyInto(out, pos); pos += 2
        }
        return out.asBytes()
    }
}

// --- APNG — acTL: Animation Control (8 bytes) ---

/**
 * Parsed acTL (Animation Control) chunk data.
 *
 * ```
 * | numFrames (4 B) | numPlays (4 B) |
 * ```
 */
@Serializable
data class PngActl(
    /** Total number of frames in the animation. */
    val numFrames: UInt,
    /** Number of times to loop (0 = infinite). */
    val numPlays: UInt,
) : BinarySerializable {
    override fun toBytes(): Bytes {
        val out = ByteArray(8)
        numFrames.toBigEndianBytes().copyInto(out, 0)
        numPlays.toBigEndianBytes().copyInto(out, 4)
        return out.asBytes()
    }
}

// --- APNG — fcTL: Frame Control (26 bytes) ---

/**
 * APNG dispose operation.
 */
@Serializable
enum class PngDisposeOp(val code: Int) {
    None(0),
    Background(1),
    Previous(2);

    companion object {
        fun fromCode(code: Int): PngDisposeOp? = entries.find { it.code == code }
    }
}

/**
 * APNG blend operation.
 */
@Serializable
enum class PngBlendOp(val code: Int) {
    Source(0),
    Over(1);

    companion object {
        fun fromCode(code: Int): PngBlendOp? = entries.find { it.code == code }
    }
}

/**
 * Parsed fcTL (Frame Control) chunk data.
 *
 * ```
 * | sequenceNumber (4 B) | width (4 B) | height (4 B) |
 * | xOffset (4 B) | yOffset (4 B) |
 * | delayNum (2 B) | delayDen (2 B) |
 * | disposeOp (1 B) | blendOp (1 B) |
 * ```
 */
@Serializable
data class PngFctl(
    val sequenceNumber: UInt,
    val width: UInt,
    val height: UInt,
    val xOffset: UInt,
    val yOffset: UInt,
    val delayNum: UShort,
    val delayDen: UShort,
    val disposeOp: PngDisposeOp,
    val blendOp: PngBlendOp,
) : BinarySerializable {
    override fun toBytes(): Bytes {
        val out = ByteArray(26)
        var pos = 0
        sequenceNumber.toBigEndianBytes().copyInto(out, pos); pos += 4
        width.toBigEndianBytes().copyInto(out, pos); pos += 4
        height.toBigEndianBytes().copyInto(out, pos); pos += 4
        xOffset.toBigEndianBytes().copyInto(out, pos); pos += 4
        yOffset.toBigEndianBytes().copyInto(out, pos); pos += 4
        delayNum.toBigEndianBytes().copyInto(out, pos); pos += 2
        delayDen.toBigEndianBytes().copyInto(out, pos); pos += 2
        out[pos++] = disposeOp.code.toByte()
        out[pos] = blendOp.code.toByte()
        return out.asBytes()
    }
}

// --- Png — canonical 1:1 representation of a PNG file on disk ---

/**
 * Canonical representation of a PNG file as it exists on disk.
 *
 * A PNG file is laid out as:
 * ```
 * | signature (8 B) | chunk₁ | chunk₂ | … | chunkₙ (IEND) |
 * ```
 *
 * The [chunks] list preserves the exact order from the file.
 * The first chunk is always IHDR and the last is always IEND.
 *
 * Use the typed accessor properties ([ihdr], [plte], [idatChunks], etc.)
 * to obtain parsed interpretations of well-known chunk types.
 */
@Serializable
data class PngRaw(
    /** The 8-byte PNG signature. */
    val signature: Bytes,
    /** Ordered sequence of chunks exactly as they appear on disk. */
    val chunks: List<PngChunk>,
) : RawMediaStructure {

    // --- Binary serialization ---

    /**
     * Produces the exact bytes of a valid PNG file:
     * signature || chunk(1) || chunk(2) || … || chunk(n)
     */
    override fun toBytes(): Bytes {
        val totalSize = signature.size + chunks.sumOf { 4 + 4 + it.data.size + 4 }
        val out = ByteArray(totalSize)
        signature.data.copyInto(out, 0)
        var pos = signature.size
        for (chunk in chunks) {
            val chunkBytes = chunk.toBytes()
            chunkBytes.data.copyInto(out, pos)
            pos += chunkBytes.size
        }
        return out.asBytes()
    }

    companion object {
        /** The standard 8-byte PNG file signature: `\x89PNG\r\n\x1a\n`. */
        val SIGNATURE: ByteArray = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
    }
}

// --- Typed extension accessors ---

/** The IHDR chunk data (always present, always first). */
val PngRaw.ihdr: PngIhdr
    get() {
        val c = chunks.first { it.type.value == "IHDR" }
        val d = c.data.data
        return PngIhdr(
            width = ((d[0].toUInt() and 0xFFu) shl 24) or
                    ((d[1].toUInt() and 0xFFu) shl 16) or
                    ((d[2].toUInt() and 0xFFu) shl 8) or
                    (d[3].toUInt() and 0xFFu),
            height = ((d[4].toUInt() and 0xFFu) shl 24) or
                    ((d[5].toUInt() and 0xFFu) shl 16) or
                    ((d[6].toUInt() and 0xFFu) shl 8) or
                    (d[7].toUInt() and 0xFFu),
            bitDepth = d[8].toUByte(),
            colorType = PngColorType.fromCode(d[9].toInt())
                ?: error("Unknown PNG color type: ${d[9]}"),
            compressionMethod = d[10].toUByte(),
            filterMethod = d[11].toUByte(),
            interlaceMethod = PngInterlaceMethod.fromCode(d[12].toInt())
                ?: error("Unknown PNG interlace method: ${d[12]}"),
        )
    }

/** The PLTE chunk data, or `null` if not present. */
val PngRaw.plte: PngPlte?
    get() {
        val c = chunks.firstOrNull { it.type.value == "PLTE" } ?: return null
        val d = c.data.data
        val entries = (0 until d.size / 3).map { i ->
            PngPlteEntry(
                red = d[i * 3].toUByte(),
                green = d[i * 3 + 1].toUByte(),
                blue = d[i * 3 + 2].toUByte(),
            )
        }
        return PngPlte(entries)
    }

/** All IDAT chunks in file order. */
val PngRaw.idatChunks: List<PngIdat>
    get() = chunks
        .filter { it.type.value == "IDAT" }
        .map { PngIdat(it.data) }

/**
 * Concatenated compressed image data from all IDAT chunks.
 * This is the full deflate datastream that, when decompressed,
 * yields the filtered scanlines.
 */
val PngRaw.compressedImageData: Bytes
    get() {
        val parts = chunks.filter { it.type.value == "IDAT" }
        val total = parts.sumOf { it.data.size }
        val out = ByteArray(total)
        var pos = 0
        for (part in parts) {
            part.data.data.copyInto(out, pos)
            pos += part.data.size
        }
        return out.asBytes()
    }

/** The IEND marker (always present, always last). Returns [PngIend]. */
@Suppress("unused")
val PngRaw.iend: PngIend get() = PngIend

/** The gAMA chunk data, or `null` if not present. */
val PngRaw.gama: PngGama?
    get() {
        val c = chunks.firstOrNull { it.type.value == "gAMA" } ?: return null
        val d = c.data.data
        val gamma = ((d[0].toUInt() and 0xFFu) shl 24) or
                ((d[1].toUInt() and 0xFFu) shl 16) or
                ((d[2].toUInt() and 0xFFu) shl 8) or
                (d[3].toUInt() and 0xFFu)
        return PngGama(gamma)
    }

/** The cHRM chunk data, or `null` if not present. */
val PngRaw.chrm: PngChrm?
    get() {
        val c = chunks.firstOrNull { it.type.value == "cHRM" } ?: return null
        val d = c.data.data
        fun readU32(offset: Int): UInt =
            ((d[offset].toUInt() and 0xFFu) shl 24) or
                    ((d[offset + 1].toUInt() and 0xFFu) shl 16) or
                    ((d[offset + 2].toUInt() and 0xFFu) shl 8) or
                    (d[offset + 3].toUInt() and 0xFFu)
        return PngChrm(
            whitePointX = readU32(0), whitePointY = readU32(4),
            redX = readU32(8), redY = readU32(12),
            greenX = readU32(16), greenY = readU32(20),
            blueX = readU32(24), blueY = readU32(28),
        )
    }

/** The sRGB chunk data, or `null` if not present. */
val PngRaw.srgb: PngSrgb?
    get() {
        val c = chunks.firstOrNull { it.type.value == "sRGB" } ?: return null
        return PngSrgb(
            PngRenderingIntent.fromCode(c.data.data[0].toInt())
                ?: error("Unknown sRGB rendering intent: ${c.data.data[0]}")
        )
    }

/** The iCCP chunk data, or `null` if not present. */
val PngRaw.iccp: PngIccp?
    get() {
        val c = chunks.firstOrNull { it.type.value == "iCCP" } ?: return null
        val d = c.data.data
        val nullIdx = d.indexOf(0)
        val name = d.decodeToString(0, nullIdx)
        val method = d[nullIdx + 1].toUByte()
        val profile = d.copyOfRange(nullIdx + 2, d.size).asBytes()
        return PngIccp(name, method, profile)
    }

/** The pHYs chunk data, or `null` if not present. */
val PngRaw.phys: PngPhys?
    get() {
        val c = chunks.firstOrNull { it.type.value == "pHYs" } ?: return null
        val d = c.data.data
        fun readU32(offset: Int): UInt =
            ((d[offset].toUInt() and 0xFFu) shl 24) or
                    ((d[offset + 1].toUInt() and 0xFFu) shl 16) or
                    ((d[offset + 2].toUInt() and 0xFFu) shl 8) or
                    (d[offset + 3].toUInt() and 0xFFu)
        return PngPhys(readU32(0), readU32(4), d[8].toUByte())
    }

/** The tIME chunk data, or `null` if not present. */
val PngRaw.time: PngTime?
    get() {
        val c = chunks.firstOrNull { it.type.value == "tIME" } ?: return null
        val d = c.data.data
        return PngTime(
            year = ((d[0].toUInt() and 0xFFu) shl 8 or (d[1].toUInt() and 0xFFu)).toUShort(),
            month = d[2].toUByte(), day = d[3].toUByte(),
            hour = d[4].toUByte(), minute = d[5].toUByte(), second = d[6].toUByte(),
        )
    }

/** All tEXt chunks. */
val PngRaw.textChunks: List<PngTextChunk>
    get() = chunks.filter { it.type.value == "tEXt" }.map { c ->
        val d = c.data.data
        val nullIdx = d.indexOf(0)
        PngTextChunk(
            keyword = d.decodeToString(0, nullIdx),
            text = d.decodeToString(nullIdx + 1, d.size),
        )
    }

/** All zTXt (compressed text) chunks. */
val PngRaw.ztxtChunks: List<PngZtxt>
    get() = chunks.filter { it.type.value == "zTXt" }.map { c ->
        val d = c.data.data
        val nullIdx = d.indexOf(0)
        PngZtxt(
            keyword = d.decodeToString(0, nullIdx),
            compressionMethod = d[nullIdx + 1].toUByte(),
            compressedText = d.copyOfRange(nullIdx + 2, d.size).asBytes(),
        )
    }

/** All iTXt (international text) chunks. */
val PngRaw.itxtChunks: List<PngItxt>
    get() = chunks.filter { it.type.value == "iTXt" }.map { c ->
        val d = c.data.data
        fun nextNull(from: Int): Int {
            for (i in from until d.size) if (d[i] == 0.toByte()) return i
            error("iTXt chunk: null separator not found")
        }
        val kwEnd = nextNull(0)
        val keyword = d.decodeToString(0, kwEnd)
        val compressionFlag = d[kwEnd + 1].toUByte()
        val compressionMethod = d[kwEnd + 2].toUByte()
        val langStart = kwEnd + 3
        val langEnd = nextNull(langStart)
        val languageTag = d.decodeToString(langStart, langEnd)
        val trKwStart = langEnd + 1
        val trKwEnd = nextNull(trKwStart)
        val translatedKeyword = d.decodeToString(trKwStart, trKwEnd)
        val text = d.decodeToString(trKwEnd + 1, d.size)
        PngItxt(keyword, compressionFlag, compressionMethod, languageTag, translatedKeyword, text)
    }

/** The tRNS chunk data, or `null` if not present. */
val PngRaw.trns: PngTrns?
    get() {
        val c = chunks.firstOrNull { it.type.value == "tRNS" } ?: return null
        val d = c.data.data
        val colorType = ihdr.colorType
        return when (colorType) {
            PngColorType.Grayscale -> PngTrns(
                greySample = ((d[0].toUInt() and 0xFFu) shl 8 or (d[1].toUInt() and 0xFFu)).toUShort()
            )
            PngColorType.Rgb -> {
                fun readU16(offset: Int): UShort =
                    ((d[offset].toUInt() and 0xFFu) shl 8 or (d[offset + 1].toUInt() and 0xFFu)).toUShort()
                PngTrns(redSample = readU16(0), greenSample = readU16(2), blueSample = readU16(4))
            }
            PngColorType.Indexed -> PngTrns(alphaEntries = d.map { it.toUByte() })
            else -> null
        }
    }

/** The sBIT chunk data, or `null` if not present. */
val PngRaw.sbit: PngSbit?
    get() {
        val c = chunks.firstOrNull { it.type.value == "sBIT" } ?: return null
        return PngSbit(c.data.data.map { it.toUByte() })
    }

/** The bKGD chunk data, or `null` if not present. */
val PngRaw.bkgd: PngBkgd?
    get() {
        val c = chunks.firstOrNull { it.type.value == "bKGD" } ?: return null
        val d = c.data.data
        val colorType = ihdr.colorType
        return when (colorType) {
            PngColorType.Indexed -> PngBkgd(paletteIndex = d[0].toUByte())
            PngColorType.Grayscale, PngColorType.GrayscaleAlpha -> PngBkgd(
                grey = ((d[0].toUInt() and 0xFFu) shl 8 or (d[1].toUInt() and 0xFFu)).toUShort()
            )
            PngColorType.Rgb, PngColorType.RgbAlpha -> {
                fun readU16(offset: Int): UShort =
                    ((d[offset].toUInt() and 0xFFu) shl 8 or (d[offset + 1].toUInt() and 0xFFu)).toUShort()
                PngBkgd(red = readU16(0), green = readU16(2), blue = readU16(4))
            }
        }
    }

/** The hIST chunk data, or `null` if not present. */
val PngRaw.hist: PngHist?
    get() {
        val c = chunks.firstOrNull { it.type.value == "hIST" } ?: return null
        val d = c.data.data
        val freqs = (0 until d.size / 2).map { i ->
            ((d[i * 2].toUInt() and 0xFFu) shl 8 or (d[i * 2 + 1].toUInt() and 0xFFu)).toUShort()
        }
        return PngHist(freqs)
    }

/** All sPLT (suggested palette) chunks. */
val PngRaw.spltChunks: List<PngSplt>
    get() = chunks.filter { it.type.value == "sPLT" }.map { c ->
        val d = c.data.data
        val nullIdx = d.indexOf(0)
        val paletteName = d.decodeToString(0, nullIdx)
        val sampleDepth = d[nullIdx + 1].toUByte()
        val bytesPerEntry = if (sampleDepth.toInt() == 8) 6 else 10
        val entryData = d.copyOfRange(nullIdx + 2, d.size)
        val entryCount = entryData.size / bytesPerEntry
        val entries = (0 until entryCount).map { i ->
            val o = i * bytesPerEntry
            if (sampleDepth.toInt() == 8) {
                PngSpltEntry(
                    red = entryData[o].toUByte().toUShort(),
                    green = entryData[o + 1].toUByte().toUShort(),
                    blue = entryData[o + 2].toUByte().toUShort(),
                    alpha = entryData[o + 3].toUByte().toUShort(),
                    frequency = ((entryData[o + 4].toUInt() and 0xFFu) shl 8 or
                            (entryData[o + 5].toUInt() and 0xFFu)).toUShort(),
                )
            } else {
                fun readU16(off: Int): UShort =
                    ((entryData[off].toUInt() and 0xFFu) shl 8 or
                            (entryData[off + 1].toUInt() and 0xFFu)).toUShort()
                PngSpltEntry(
                    red = readU16(o),
                    green = readU16(o + 2),
                    blue = readU16(o + 4),
                    alpha = readU16(o + 6),
                    frequency = readU16(o + 8),
                )
            }
        }
        PngSplt(paletteName, sampleDepth, entries)
    }

/** The acTL (APNG Animation Control) chunk data, or `null` if not present. */
val PngRaw.actl: PngActl?
    get() {
        val c = chunks.firstOrNull { it.type.value == "acTL" } ?: return null
        val d = c.data.data
        fun readU32(offset: Int): UInt =
            ((d[offset].toUInt() and 0xFFu) shl 24) or
                    ((d[offset + 1].toUInt() and 0xFFu) shl 16) or
                    ((d[offset + 2].toUInt() and 0xFFu) shl 8) or
                    (d[offset + 3].toUInt() and 0xFFu)
        return PngActl(numFrames = readU32(0), numPlays = readU32(4))
    }

/** All fcTL (APNG Frame Control) chunk data in file order. */
val PngRaw.fctlChunks: List<PngFctl>
    get() = chunks.filter { it.type.value == "fcTL" }.map { c ->
        val d = c.data.data
        fun readU32(offset: Int): UInt =
            ((d[offset].toUInt() and 0xFFu) shl 24) or
                    ((d[offset + 1].toUInt() and 0xFFu) shl 16) or
                    ((d[offset + 2].toUInt() and 0xFFu) shl 8) or
                    (d[offset + 3].toUInt() and 0xFFu)
        fun readU16(offset: Int): UShort =
            ((d[offset].toUInt() and 0xFFu) shl 8 or (d[offset + 1].toUInt() and 0xFFu)).toUShort()
        PngFctl(
            sequenceNumber = readU32(0),
            width = readU32(4), height = readU32(8),
            xOffset = readU32(12), yOffset = readU32(16),
            delayNum = readU16(20), delayDen = readU16(22),
            disposeOp = PngDisposeOp.fromCode(d[24].toInt())
                ?: error("Unknown APNG dispose op: ${d[24]}"),
            blendOp = PngBlendOp.fromCode(d[25].toInt())
                ?: error("Unknown APNG blend op: ${d[25]}"),
        )
    }
