@file:Suppress("unused")

package dev.transmute.model.structure.image

import dev.transmute.model.core.BinarySerializable
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.Pixels
import dev.transmute.model.core.asBytes
import dev.transmute.model.structure.MediaStructure
import kotlinx.serialization.Serializable

// --- Helpers — big-endian encoding ---

private fun UShort.toBigEndianBytes(): ByteArray = byteArrayOf(
    (this.toInt() shr 8).toByte(),
    this.toByte(),
)

// --- JPEG marker types ---

/**
 * Well-known JPEG marker types.
 *
 * A JPEG marker is always `0xFF` followed by the marker-type byte.
 * Markers listed here are the ones most commonly encountered; unknown
 * markers are still preserved in the segment list.
 */
@Serializable
enum class JpegMarkerType(val code: UByte) {
    // Start / end
    SOI(0xD8u),   SOF0(0xC0u),  SOF1(0xC1u),  SOF2(0xC2u),  SOF3(0xC3u),
    SOF5(0xC5u),  SOF6(0xC6u),  SOF7(0xC7u),  SOF9(0xC9u),  SOF10(0xCAu),
    SOF11(0xCBu), SOF13(0xCDu), SOF14(0xCEu), SOF15(0xCFu),
    DHT(0xC4u),   DAC(0xCCu),
    // Restart markers
    RST0(0xD0u), RST1(0xD1u), RST2(0xD2u), RST3(0xD3u),
    RST4(0xD4u), RST5(0xD5u), RST6(0xD6u), RST7(0xD7u),
    SOI_(0xD8u), EOI(0xD9u), SOS(0xDAu), DQT(0xDBu), DNL(0xDCu),
    DRI(0xDDu), DHP(0xDEu), EXP(0xDFu),
    // Application segments
    APP0(0xE0u),  APP1(0xE1u),  APP2(0xE2u),  APP3(0xE3u),
    APP4(0xE4u),  APP5(0xE5u),  APP6(0xE6u),  APP7(0xE7u),
    APP8(0xE8u),  APP9(0xE9u),  APP10(0xEAu), APP11(0xEBu),
    APP12(0xECu), APP13(0xEDu), APP14(0xEEu), APP15(0xEFu),
    // Comment
    COM(0xFEu);

    companion object {
        fun fromCode(code: UByte): JpegMarkerType? = entries.find { it.code == code }

        /** `true` when the marker is standalone (no length / payload). */
        fun isStandalone(code: UByte): Boolean {
            val c = code.toInt()
            return c == 0xD8 || c == 0xD9 || (c in 0xD0..0xD7) || c == 0x01
        }
    }
}

// --- JPEG segment — the fundamental structural unit ---

/**
 * A single JPEG segment (marker + payload) as it appears on disk.
 *
 * **Payload segments** (most markers):
 * ```
 * | 0xFF | marker (1 B) | length (2 B BE, includes itself) | data (length − 2 B) |
 * ```
 *
 * **Standalone markers** (SOI, EOI, RST0–RST7): no length or data.
 *
 * For SOS segments, [entropy] holds the entropy-coded scan data that
 * follows the SOS header up to the next marker.
 */
@Serializable
data class JpegSegment(
    /** Marker-type byte (the byte after `0xFF`). */
    val marker: UByte,
    /** Segment payload (excluding the marker and the 2-byte length field). */
    val data: Bytes = Bytes(ByteArray(0)),
    /** Entropy-coded data that follows an SOS segment (empty for all other markers). */
    val entropy: Bytes = Bytes(ByteArray(0)),
) : BinarySerializable {

    override fun toBytes(): Bytes {
        val isStandalone = JpegMarkerType.isStandalone(marker)
        if (isStandalone && entropy.isEmpty()) {
            return byteArrayOf(0xFF.toByte(), marker.toByte()).asBytes()
        }
        val lengthVal = (data.size + 2).toUShort()
        val lenBytes = lengthVal.toBigEndianBytes()
        val segSize = 2 + 2 + data.size + entropy.size
        val out = ByteArray(segSize)
        out[0] = 0xFF.toByte()
        out[1] = marker.toByte()
        lenBytes.copyInto(out, 2)
        data.data.copyInto(out, 4)
        if (entropy.isNotEmpty()) {
            entropy.data.copyInto(out, 4 + data.size)
        }
        return out.asBytes()
    }
}

// --- Typed models for well-known segment contents ---

/**
 * JPEG image component descriptor (from an SOF marker).
 */
@Serializable
data class JpegComponent(
    /** Component identifier. */
    val id: Int,
    /** Horizontal sampling factor. */
    val horizontalSampling: Int,
    /** Vertical sampling factor. */
    val verticalSampling: Int,
    /** Quantization table selector. */
    val quantizationTableId: Int,
)

/**
 * Parsed Start-of-Frame data (SOF0 / SOF2 / …).
 */
@Serializable
data class JpegSofData(
    /** Sample precision in bits (usually 8). */
    val precision: Int,
    /** Image height in pixels. */
    val height: UShort,
    /** Image width in pixels. */
    val width: UShort,
    /** Image components. */
    val components: List<JpegComponent>,
)

/**
 * Parsed JFIF APP0 header.
 */
@Serializable
data class JpegJfifHeader(
    val majorVersion: Int,
    val minorVersion: Int,
    val densityUnits: Int,
    val xDensity: UShort,
    val yDensity: UShort,
    val thumbnailWidth: Int,
    val thumbnailHeight: Int,
)

// --- JPEG file — complete on-disk representation ---

/**
 * Canonical representation of a JPEG file as written to disk.
 *
 * The file is modelled as an ordered list of [JpegSegment]s, starting
 * with SOI and ending with EOI.
 */
@Serializable
data class Jpeg(
    /** All segments in file order (SOI first, EOI last). */
    val segments: List<JpegSegment>,
) : MediaStructure {

    // --- Binary serialization ---

    override fun toBytes(): Bytes {
        val parts = segments.map { it.toBytes().data }
        val totalSize = parts.sumOf { it.size }
        val out = ByteArray(totalSize)
        var pos = 0
        for (part in parts) { part.copyInto(out, pos); pos += part.size }
        return out.asBytes()
    }

    companion object {
        /** The SOI marker bytes that start every JPEG file. */
        val SIGNATURE: ByteArray = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
    }
}

// --- Typed extension accessors ---

/** Image width from the first SOF marker, or `null` if none found. */
val Jpeg.width: Pixels?
    get() = sofData?.let { Pixels(it.width.toInt()) }

/** Image height from the first SOF marker, or `null` if none found. */
val Jpeg.height: Pixels?
    get() = sofData?.let { Pixels(it.height.toInt()) }

/** Parsed SOF data from the first Start-of-Frame segment. */
val Jpeg.sofData: JpegSofData?
    get() {
        val sofCodes = setOf(
            0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7,
            0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF,
        )
        val seg = segments.firstOrNull { it.marker.toInt() in sofCodes } ?: return null
        val d = seg.data.data
        if (d.size < 6) return null
        val precision = d[0].toInt() and 0xFF
        val h = ((d[1].toUInt() and 0xFFu) shl 8 or (d[2].toUInt() and 0xFFu)).toUShort()
        val w = ((d[3].toUInt() and 0xFFu) shl 8 or (d[4].toUInt() and 0xFFu)).toUShort()
        val nComp = d[5].toInt() and 0xFF
        val components = (0 until nComp).mapNotNull { i ->
            val off = 6 + i * 3
            if (off + 2 >= d.size) return@mapNotNull null
            val compId = d[off].toInt() and 0xFF
            val sampling = d[off + 1].toInt() and 0xFF
            JpegComponent(
                id = compId,
                horizontalSampling = (sampling shr 4) and 0x0F,
                verticalSampling = sampling and 0x0F,
                quantizationTableId = d[off + 2].toInt() and 0xFF,
            )
        }
        return JpegSofData(precision, h, w, components)
    }

/** Parsed JFIF APP0 header, or `null` if no JFIF header is present. */
val Jpeg.jfifHeader: JpegJfifHeader?
    get() {
        val seg = segments.firstOrNull { it.marker.toInt() == 0xE0 } ?: return null
        val d = seg.data.data
        if (d.size < 14) return null
        val id = d.decodeToString(0, 5)
        if (id != "JFIF\u0000") return null
        return JpegJfifHeader(
            majorVersion = d[5].toInt() and 0xFF,
            minorVersion = d[6].toInt() and 0xFF,
            densityUnits = d[7].toInt() and 0xFF,
            xDensity = ((d[8].toUInt() and 0xFFu) shl 8 or (d[9].toUInt() and 0xFFu)).toUShort(),
            yDensity = ((d[10].toUInt() and 0xFFu) shl 8 or (d[11].toUInt() and 0xFFu)).toUShort(),
            thumbnailWidth = d[12].toInt() and 0xFF,
            thumbnailHeight = d[13].toInt() and 0xFF,
        )
    }

/** All comment (COM) segment payloads decoded as UTF-8. */
val Jpeg.comments: List<String>
    get() = segments
        .filter { it.marker.toInt() == 0xFE }
        .map { it.data.data.decodeToString() }
