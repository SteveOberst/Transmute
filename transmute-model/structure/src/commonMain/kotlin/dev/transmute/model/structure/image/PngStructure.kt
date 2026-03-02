@file:Suppress("unused")

package dev.transmute.model.structure.image

import dev.transmute.model.core.MediaStructure
import dev.transmute.model.core.asBytes
import dev.transmute.model.identify.FourCC
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

// ---------------------------------------------------------------------------
// Summary type for unknown / unrecognised chunks
// ---------------------------------------------------------------------------

/**
 * A summary of a PNG chunk whose type is not natively understood by
 * the structure model.  The raw [data] is intentionally omitted;
 * the original bytes are preserved in [PngStructure.rawChunks] and
 * are used during round-trip reconstruction via [PngStructure.toRaw].
 */
@Serializable
data class PngUnknownChunkSummary(
    /** The 4-character ASCII chunk type tag, e.g. `"tEXt"`. */
    val type: String,
    /** Length of the chunk data field in bytes. */
    val length: Int,
    /** Zero-based index into the original [PngRaw.chunks] list. */
    val chunkIndex: Int,
)

// ---------------------------------------------------------------------------
// PngStructure - the serialisable, JSON-friendly view of a PNG file
// ---------------------------------------------------------------------------

/**
 * A structured, JSON-serialisable representation of a PNG file.
 *
 * Unlike [PngRaw] - which mirrors the binary on-disk layout - this
 * class exposes all well-known chunks as typed, named fields.  Blob-heavy
 * data (IDAT compressed image data) is replaced by summary statistics
 * ([idatCount], [idatTotalBytes]).
 *
 * The [rawChunks] field is marked [@Transient] and therefore excluded from
 * JSON serialisation.  It is populated when a [PngStructure] is created from
 * a [PngRaw] via [PngRaw.toStructure], and is used by [Editor.build] to
 * preserve original IDAT and unknown-chunk bytes during round-trip encoding.
 *
 * ### Creating and editing
 * ```kotlin
 * val structure: PngStructure = rawPng.toStructure()
 * val edited: PngStructure = structure.edit {
 *     time = PngTime(year = 2026u, month = 1u, day = 1u,
 *                    hour = 0u, minute = 0u, second = 0u)
 * }
 * val output: PngRaw = edited.toRaw()
 * ```
 */
@Serializable
data class PngStructure(
    /** Image dimensions, bit-depth, colour type, interlace method. */
    val ihdr: PngIhdr,
    /** Colour palette (indexed-colour images only). */
    val plte: PngPlte? = null,
    /** Transparency values. */
    val trns: PngTrns? = null,
    /** Primary chromaticities. */
    val chrm: PngChrm? = null,
    /** Image gamma. */
    val gama: PngGama? = null,
    /** Embedded ICC profile (compressed bytes included). */
    val iccp: PngIccp? = null,
    /** sRGB rendering intent. */
    val srgb: PngSrgb? = null,
    /** Background colour. */
    val bkgd: PngBkgd? = null,
    /** Physical pixel dimensions. */
    val phys: PngPhys? = null,
    /** Significant bits. */
    val sbit: PngSbit? = null,
    /** Palette histogram. */
    val hist: PngHist? = null,
    /** APNG animation control. */
    val actl: PngActl? = null,
    /** tEXt (Latin-1 text) chunks. */
    val text: List<PngTextChunk> = emptyList(),
    /** zTXt (compressed text) chunks (compressed bytes included). */
    val ztxt: List<PngZtxt> = emptyList(),
    /** iTXt (international / UTF-8 text) chunks. */
    val itxt: List<PngItxt> = emptyList(),
    /** sPLT (suggested palette) chunks. */
    val splt: List<PngSplt> = emptyList(),
    /** APNG fcTL (frame-control) chunks. */
    val fctl: List<PngFctl> = emptyList(),
    /** Last-modification time. */
    val time: PngTime? = null,
    /** Number of IDAT chunks (compressed image data). */
    val idatCount: Int,
    /** Total compressed image bytes across all IDAT chunks. */
    val idatTotalBytes: Long,
    /** Summaries of unrecognised / extension chunks. */
    val unknownChunks: List<PngUnknownChunkSummary> = emptyList(),
    /**
     * Original [PngChunk] list from the source [PngRaw].
     * Excluded from JSON serialisation; required for [toRaw] / [Editor.build].
     */
    @Transient
    internal val rawChunks: List<PngChunk> = emptyList(),
) : MediaStructure {

    // -----------------------------------------------------------------------
    // Editor - mutable surface for chunk-level modifications
    // -----------------------------------------------------------------------

    /**
     * Mutable editor for a [PngStructure].
     *
     * Exposes every well-known chunk as a `var` property initialised from
     * the source structure.  Call [build] to produce a new, immutable [PngRaw]
     * with correctly computed CRCs.
     *
     * Obtain an editor via the [edit] extension function:
     * ```kotlin
     * val edited = structure.edit { gama = PngGama(45455u) }
     * ```
     */
    class Editor internal constructor(private val source: PngStructure) {
        var ihdr: PngIhdr = source.ihdr
        var plte: PngPlte? = source.plte
        var trns: PngTrns? = source.trns
        var chrm: PngChrm? = source.chrm
        var gama: PngGama? = source.gama
        var iccp: PngIccp? = source.iccp
        var srgb: PngSrgb? = source.srgb
        var bkgd: PngBkgd? = source.bkgd
        var phys: PngPhys? = source.phys
        var sbit: PngSbit? = source.sbit
        var hist: PngHist? = source.hist
        var actl: PngActl? = source.actl
        var text: List<PngTextChunk> = source.text
        var ztxt: List<PngZtxt> = source.ztxt
        var itxt: List<PngItxt> = source.itxt
        var splt: List<PngSplt> = source.splt
        var fctl: List<PngFctl> = source.fctl
        var time: PngTime? = source.time

        /** IDAT chunks extracted from the original [PngRaw] (read-only). */
        private val rawIdat: List<PngIdat> = source.rawChunks
            .filter { it.type.value == "IDAT" }
            .map { PngIdat(it.data) }

        /**
         * Reassemble this editor's state into a new [PngRaw] with correct
         * chunk order and recomputed CRCs.
         *
         * Chunk ordering follows the PNG specification:
         * IHDR -> colour-management -> PLTE -> tRNS/hIST/bKGD/sBIT -> pHYs ->
         * sPLT -> text -> acTL -> fcTL/IDAT -> tIME -> unknown -> IEND.
         *
         * Unknown chunks from the original file are preserved in place.
         *
         * > **Note**: IDAT bytes come from the original [PngRaw] passed to
         * > [PngRaw.toStructure].  If this [PngStructure] was deserialised
         * > from JSON (i.e. [PngStructure.rawChunks] is empty), [build] will
         * > produce a PNG with no image data - useful for metadata-only
         * > workflows but not for rendering.
         */
        fun build(): PngRaw {
            val chunks = mutableListOf<PngChunk>()

            // IHDR - always first
            chunks += buildPngChunk("IHDR", ihdr.toBytes().data)

            // Colour-management (before PLTE)
            chrm?.let { chunks += buildPngChunk("cHRM", it.toBytes().data) }
            gama?.let { chunks += buildPngChunk("gAMA", it.toBytes().data) }
            iccp?.let { chunks += buildPngChunk("iCCP", it.toBytes().data) }
            sbit?.let { chunks += buildPngChunk("sBIT", it.toBytes().data) }
            srgb?.let { chunks += buildPngChunk("sRGB", it.toBytes().data) }

            // PLTE
            plte?.let { chunks += buildPngChunk("PLTE", it.toBytes().data) }

            // Chunks that follow PLTE
            bkgd?.let { chunks += buildPngChunk("bKGD", it.toBytes().data) }
            hist?.let { chunks += buildPngChunk("hIST", it.toBytes().data) }
            trns?.let { chunks += buildPngChunk("tRNS", it.toBytes().data) }

            // Physical dimensions
            phys?.let { chunks += buildPngChunk("pHYs", it.toBytes().data) }

            // Suggested palettes
            for (s in splt) chunks += buildPngChunk("sPLT", s.toBytes().data)

            // Text chunks
            for (t in text) chunks += buildPngChunk("tEXt", t.toBytes().data)
            for (z in ztxt) chunks += buildPngChunk("zTXt", z.toBytes().data)
            for (i in itxt) chunks += buildPngChunk("iTXt", i.toBytes().data)

            // APNG animation control
            actl?.let { chunks += buildPngChunk("acTL", it.toBytes().data) }

            // Interleave fcTL and IDAT (APNG) or just IDAT (static PNG)
            if (fctl.isNotEmpty()) {
                val fcIter = fctl.iterator()
                if (fcIter.hasNext()) chunks += buildPngChunk("fcTL", fcIter.next().toBytes().data)
                for (idat in rawIdat) chunks += buildPngChunk("IDAT", idat.compressedData.data)
                while (fcIter.hasNext()) chunks += buildPngChunk("fcTL", fcIter.next().toBytes().data)
            } else {
                for (idat in rawIdat) chunks += buildPngChunk("IDAT", idat.compressedData.data)
            }

            // Modification time
            time?.let { chunks += buildPngChunk("tIME", it.toBytes().data) }

            // Preserve unknown chunks from original file
            for (chunk in source.rawChunks) {
                if (chunk.type.value !in KNOWN_CHUNK_TYPES) {
                    chunks += chunk
                }
            }

            // IEND - always last
            chunks += buildPngChunk("IEND", ByteArray(0))

            return PngRaw(
                signature = PngRaw.SIGNATURE.asBytes(),
                chunks = chunks,
            )
        }
    }

    companion object {
        /**
         * Set of chunk type tags that [Editor] manages explicitly.
         * Any chunk type not in this set is treated as unknown and
         * preserved verbatim from the original file during [Editor.build].
         */
        val KNOWN_CHUNK_TYPES: Set<String> = setOf(
            "IHDR", "PLTE", "IDAT", "IEND",
            "cHRM", "gAMA", "iCCP", "sBIT", "sRGB",
            "bKGD", "hIST", "tRNS", "pHYs", "sPLT",
            "tEXt", "zTXt", "iTXt", "tIME",
            "acTL", "fcTL", "fdAT",
        )
    }
}

// ---------------------------------------------------------------------------
// CRC-32 (ISO 3309 / PNG spec) - file-private
// ---------------------------------------------------------------------------

private val pngCrcTable: IntArray = IntArray(256) { n ->
    var c = n
    repeat(8) { c = if (c and 1 != 0) (0xEDB88320.toInt() xor (c ushr 1)) else (c ushr 1) }
    c
}

private fun pngCrc32(data: ByteArray): UInt {
    var crc = 0xFFFFFFFF.toInt()
    for (b in data) crc = pngCrcTable[(crc xor b.toInt()) and 0xFF] xor (crc ushr 8)
    return (crc xor 0xFFFFFFFF.toInt()).toUInt()
}

/** Build a [PngChunk] from raw type tag + data bytes, computing CRC automatically. */
private fun buildPngChunk(type: String, data: ByteArray): PngChunk {
    val typeBytes = type.encodeToByteArray()
    return PngChunk(
        length = data.size.toUInt(),
        type = FourCC(type),
        data = data.asBytes(),
        crc = pngCrc32(typeBytes + data),
    )
}

// ---------------------------------------------------------------------------
// Public extension API
// ---------------------------------------------------------------------------

/**
 * Mutate this [PngStructure] and return a new [PngStructure] incorporating
 * all changes made inside [block].
 *
 * The [block] receives a [PngStructure.Editor] whose var-properties are
 * pre-populated from the receiver.  When the block returns, [Editor.build]
 * is called and the resulting [PngRaw] is immediately converted back via
 * [PngRaw.toStructure], so the return value is a fully-updated structure
 * (with correct [PngStructure.idatCount], [PngStructure.unknownChunks], etc.).
 */
fun PngStructure.edit(block: PngStructure.Editor.() -> Unit): PngStructure {
    val editor = PngStructure.Editor(this)
    editor.block()
    return editor.build().toStructure()
}

/**
 * Convert this [PngStructure] back to a binary-faithful [PngRaw].
 *
 * Requires that this structure was originally produced by [PngRaw.toStructure]
 * (i.e. [PngStructure.rawChunks] is populated).  If the structure originated
 * from JSON deserialisation the resulting PNG will have no image data.
 */
fun PngStructure.toRaw(): PngRaw =
    PngStructure.Editor(this).build()

/**
 * Parse this [PngRaw] into a [PngStructure].
 *
 * All well-known chunks are decoded into typed fields.  IDAT chunks are
 * summarised by count and total byte size ([PngStructure.idatCount],
 * [PngStructure.idatTotalBytes]); their compressed bytes are retained in
 * [PngStructure.rawChunks] for lossless round-trip via [PngStructure.toRaw].
 */
fun PngRaw.toStructure(): PngStructure {
    var idatCount = 0
    var idatTotalBytes = 0L
    val unknownChunks = mutableListOf<PngUnknownChunkSummary>()

    chunks.forEachIndexed { idx, chunk ->
        when (chunk.type.value) {
            "IDAT" -> {
                idatCount++
                idatTotalBytes += chunk.data.size.toLong()
            }
            !in PngStructure.KNOWN_CHUNK_TYPES -> {
                unknownChunks += PngUnknownChunkSummary(
                    type = chunk.type.value,
                    length = chunk.data.size,
                    chunkIndex = idx,
                )
            }
        }
    }

    return PngStructure(
        ihdr = ihdr,
        plte = plte,
        trns = trns,
        chrm = chrm,
        gama = gama,
        iccp = iccp,
        srgb = srgb,
        bkgd = bkgd,
        phys = phys,
        sbit = sbit,
        hist = hist,
        actl = actl,
        text = textChunks,
        ztxt = ztxtChunks,
        itxt = itxtChunks,
        splt = spltChunks,
        fctl = fctlChunks,
        time = time,
        idatCount = idatCount,
        idatTotalBytes = idatTotalBytes,
        unknownChunks = unknownChunks,
        rawChunks = chunks,
    )
}
