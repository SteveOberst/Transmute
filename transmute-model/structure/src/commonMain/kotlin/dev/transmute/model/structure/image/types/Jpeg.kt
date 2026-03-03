@file:Suppress("unused")

package dev.transmute.model.structure.image.types

import dev.transmute.model.core.BinarySerializable
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.Pixels
import dev.transmute.model.core.asBytes
import dev.transmute.model.core.RawMediaStructure
import kotlinx.serialization.Serializable

// --- Helpers - big-endian encoding ---

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

        /** `true` when the marker code is an SOF (Start of Frame) marker. */
        fun isSof(code: UByte): Boolean {
            val c = code.toInt()
            return c in 0xC0..0xCF && c != 0xC4 && c != 0xC8 && c != 0xCC
        }
    }
}

// --- JPEG segment - the fundamental structural unit ---

/**
 * A single JPEG segment (marker + payload) as it appears on disk.
 *
 * **Payload segments** (most markers):
 * ```
 * | 0xFF | marker (1 B) | length (2 B BE, includes itself) | data (length  2 B) |
 * ```
 *
 * **Standalone markers** (SOI, EOI, RST0-RST7): no length or data.
 */
@Serializable
data class JpegSegment(
    /** Marker-type byte (the byte after `0xFF`). */
    val marker: UByte,
    /** Segment payload (excluding the marker and the 2-byte length field). */
    val data: Bytes = Bytes(ByteArray(0)),
) : BinarySerializable {

    override fun toBytes(): Bytes {
        val isStandalone = JpegMarkerType.isStandalone(marker)
        if (isStandalone) {
            return byteArrayOf(0xFF.toByte(), marker.toByte()).asBytes()
        }
        val lengthVal = (data.size + 2).toUShort()
        val lenBytes = lengthVal.toBigEndianBytes()
        val segSize = 2 + 2 + data.size
        val out = ByteArray(segSize)
        out[0] = 0xFF.toByte()
        out[1] = marker.toByte()
        lenBytes.copyInto(out, 2)
        data.data.copyInto(out, 4)
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
 * Parsed Start-of-Frame data (SOF0 / SOF2 / ...).
 *
 * Per ITU-T T.81 B.2.2:
 * ```
 * | precision (1 B) | height (2 B BE) | width (2 B BE) | Nf (1 B) |
 * | { Ci (1 B) | Hi:Vi (1 B) | Tqi (1 B) } x Nf |
 * ```
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

/**
 * Component reference in a Start-of-Scan header (ITU-T T.81 B.2.3).
 */
@Serializable
data class JpegSosComponent(
    /** Component selector (matches [JpegComponent.id]). */
    val componentSelector: Int,
    /** DC entropy coding table selector. */
    val dcTableSelector: Int,
    /** AC entropy coding table selector. */
    val acTableSelector: Int,
)

/**
 * Parsed Start-of-Scan header data (ITU-T T.81 B.2.3).
 *
 * ```
 * | Ns (1 B) | { Csj (1 B) | Tdj:Taj (1 B) } x Ns |
 * | Ss (1 B) | Se (1 B) | Ah:Al (1 B) |
 * ```
 */
@Serializable
data class JpegSosData(
    /** Components included in this scan. */
    val components: List<JpegSosComponent>,
    /** Start of spectral/predictor selection. */
    val spectralSelectionStart: Int,
    /** End of spectral selection. */
    val spectralSelectionEnd: Int,
    /** Successive approximation bit position high. */
    val successiveApproxHigh: Int,
    /** Successive approximation bit position low. */
    val successiveApproxLow: Int,
)

// --- JPEG scan (ITU-T T.81 B.2.3) ---

/**
 * A single scan within a JPEG frame.
 *
 * Per ITU-T T.81, a scan consists of an SOS header followed by
 * entropy-coded data.  In progressive JPEG, a frame contains
 * multiple scans; in baseline, exactly one.
 *
 * Table/misc segments (DHT, DQT, DRI, COM, APP) that appear between
 * this scan and the next are captured in [interScanSegments].
 */
@Serializable
data class JpegScan(
    /** The SOS marker segment (header only, no entropy data). */
    val sosSegment: JpegSegment,
    /** Parsed SOS header data. Null only if the header is malformed. */
    val sosData: JpegSosData?,
    /** Entropy-coded data following the SOS header. */
    val entropy: Bytes = Bytes(ByteArray(0)),
    /** Table/misc segments after this scan's entropy data and before the next scan (or EOI). */
    val interScanSegments: List<JpegSegment> = emptyList(),
)

// --- JPEG frame (ITU-T T.81 B.2.2) ---

/**
 * A JPEG frame as defined by ITU-T T.81.
 *
 * A frame begins with a Start-of-Frame marker (SOF0-SOF15, excluding
 * SOF4/SOF8/SOF12) and contains one or more [scans].
 *
 * Table/misc segments (DQT, DHT, DRI, etc.) that appear after the SOF
 * marker and before the first SOS are captured in [tableSegments].
 *
 * ```
 * SOFn
 *   +- [DQT, DHT, DRI, COM, APP ...]   <- tableSegments
 *   +- Scan 1 (SOS + entropy)
 *   +- [inter-scan tables]
 *   +- Scan 2 (SOS + entropy)          <- progressive only
 *   +- ...
 * ```
 */
@Serializable
data class JpegFrame(
    /** SOF marker byte (e.g. 0xC0 for baseline, 0xC2 for progressive). */
    val sofMarker: UByte,
    /** The raw SOF segment. */
    val sofSegment: JpegSegment,
    /** Parsed Start-of-Frame header data. Null only if the SOF is malformed. */
    val sofData: JpegSofData?,
    /** Table/misc segments between SOF and first SOS. */
    val tableSegments: List<JpegSegment> = emptyList(),
    /** Scans within this frame (baseline = 1, progressive = many). */
    val scans: List<JpegScan> = emptyList(),
)

// --- JPEG file - complete on-disk representation (ITU-T T.81) ---

/**
 * Canonical representation of a JPEG file following the ITU-T T.81
 * specification hierarchy.
 *
 * ```
 * SOI
 *   +- [APP0, APP1 ..., COM, DQT, ...]   <- headerSegments (before SOF)
 *   +- Frame (SOFn)
 *        +- [DQT, DHT, DRI ...]         <- frame.tableSegments
 *        +- Scan 1 (SOS + entropy)
 *        +- [inter-scan tables]
 *        +- Scan 2 ...
 * EOI
 * ```
 */
@Serializable
data class JpegRaw(
    /**
     * Segments before the frame: SOI, followed by APP markers, COM,
     * and any table/misc markers that precede the SOF.
     */
    val headerSegments: List<JpegSegment> = emptyList(),
    /**
     * The JPEG frame (SOF + scans). Null for truncated or
     * metadata-only JPEG files that contain no frame.
     */
    val frame: JpegFrame? = null,
    /**
     * Trailing segments after the last scan (typically just EOI).
     */
    val trailerSegments: List<JpegSegment> = emptyList(),
) : RawMediaStructure {

    // --- Binary serialization ---

    override fun toBytes(): Bytes {
        val parts = mutableListOf<ByteArray>()
        for (seg in headerSegments) parts += seg.toBytes().data
        frame?.let { f ->
            parts += f.sofSegment.toBytes().data
            for (seg in f.tableSegments) parts += seg.toBytes().data
            for (scan in f.scans) {
                parts += scan.sosSegment.toBytes().data
                if (scan.entropy.isNotEmpty()) parts += scan.entropy.data
                for (seg in scan.interScanSegments) parts += seg.toBytes().data
            }
        }
        for (seg in trailerSegments) parts += seg.toBytes().data
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

/**
 * Flattened list of all segments in file order.
 *
 * This reconstructs the original flat segment sequence from the
 * hierarchical model, useful for metadata extraction and iteration.
 */
val JpegRaw.segments: List<JpegSegment>
    get() = buildList {
        addAll(headerSegments)
        frame?.let { f ->
            add(f.sofSegment)
            addAll(f.tableSegments)
            for (scan in f.scans) {
                // Re-attach entropy to the SOS segment for back-compat
                add(scan.sosSegment)
                addAll(scan.interScanSegments)
            }
        }
        addAll(trailerSegments)
    }

// --- Typed extension accessors ---

/** Image width from the frame's SOF data, or `null` if no frame found. */
val JpegRaw.width: Pixels?
    get() = frame?.sofData?.let { Pixels(it.width.toInt()) }

/** Image height from the frame's SOF data, or `null` if no frame found. */
val JpegRaw.height: Pixels?
    get() = frame?.sofData?.let { Pixels(it.height.toInt()) }

/** Parsed SOF data from the frame. */
val JpegRaw.sofData: JpegSofData?
    get() = frame?.sofData

/** Parsed JFIF APP0 header, or `null` if no JFIF header is present. */
val JpegRaw.jfifHeader: JpegJfifHeader?
    get() {
        val seg = headerSegments.firstOrNull { it.marker.toInt() == 0xE0 } ?: return null
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
val JpegRaw.comments: List<String>
    get() = segments
        .filter { it.marker.toInt() == 0xFE }
        .map { it.data.data.decodeToString() }

// --- Internal parsers ---

/** Parse SOF header bytes into [JpegSofData]. */
fun parseSofData(data: ByteArray): JpegSofData? {
    if (data.size < 6) return null
    val precision = data[0].toInt() and 0xFF
    val h = ((data[1].toUInt() and 0xFFu) shl 8 or (data[2].toUInt() and 0xFFu)).toUShort()
    val w = ((data[3].toUInt() and 0xFFu) shl 8 or (data[4].toUInt() and 0xFFu)).toUShort()
    val nComp = data[5].toInt() and 0xFF
    val components = (0 until nComp).mapNotNull { i ->
        val off = 6 + i * 3
        if (off + 2 >= data.size) return@mapNotNull null
        val compId = data[off].toInt() and 0xFF
        val sampling = data[off + 1].toInt() and 0xFF
        JpegComponent(
            id = compId,
            horizontalSampling = (sampling shr 4) and 0x0F,
            verticalSampling = sampling and 0x0F,
            quantizationTableId = data[off + 2].toInt() and 0xFF,
        )
    }
    return JpegSofData(precision, h, w, components)
}

/** Parse SOS header bytes into [JpegSosData]. */
fun parseSosData(data: ByteArray): JpegSosData? {
    if (data.isEmpty()) return null
    val ns = data[0].toInt() and 0xFF
    if (data.size < 1 + ns * 2 + 3) return null
    val components = (0 until ns).map { j ->
        val off = 1 + j * 2
        val cs = data[off].toInt() and 0xFF
        val tdTa = data[off + 1].toInt() and 0xFF
        JpegSosComponent(
            componentSelector = cs,
            dcTableSelector = (tdTa shr 4) and 0x0F,
            acTableSelector = tdTa and 0x0F,
        )
    }
    val tail = 1 + ns * 2
    return JpegSosData(
        components = components,
        spectralSelectionStart = data[tail].toInt() and 0xFF,
        spectralSelectionEnd = data[tail + 1].toInt() and 0xFF,
        successiveApproxHigh = (data[tail + 2].toInt() and 0xFF) shr 4,
        successiveApproxLow = data[tail + 2].toInt() and 0x0F,
    )
}
