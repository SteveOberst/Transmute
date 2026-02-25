@file:Suppress("unused")

package dev.transmute.model.view.image

import dev.transmute.model.core.BinarySerializable
import dev.transmute.model.core.asBytes
import dev.transmute.model.identify.FourCC
import dev.transmute.model.structure.image.*
import dev.transmute.model.view.MutableStructureView

// ---------------------------------------------------------------------------
// CRC-32 (ISO 3309 / PNG spec) — internal to the view module
// ---------------------------------------------------------------------------

private val crcTable: IntArray = IntArray(256) { n ->
    var c = n
    repeat(8) {
        c = if (c and 1 != 0) (0xEDB88320.toInt() xor (c ushr 1)) else (c ushr 1)
    }
    c
}

internal fun crc32(data: ByteArray): UInt {
    var crc = 0xFFFFFFFF.toInt()
    for (b in data) crc = crcTable[(crc xor b.toInt()) and 0xFF] xor (crc ushr 8)
    return (crc xor 0xFFFFFFFF.toInt()).toUInt()
}

/** Build a [PngChunk] from a type tag and raw data, computing the CRC automatically. */
internal fun buildChunk(type: String, data: ByteArray): PngChunk {
    val typeBytes = type.encodeToByteArray()
    return PngChunk(
        length = data.size.toUInt(),
        type = FourCC(type),
        data = data.asBytes(),
        crc = crc32(typeBytes + data),
    )
}

// ---------------------------------------------------------------------------
// MutablePngView — the "mutator" for Png
// ---------------------------------------------------------------------------

/**
 * Mutable view over a [Png].
 *
 * Exposes every well-known chunk as a `var` property initialised from
 * the source file.  After mutation, [build] reassembles the chunk list
 * with correct lengths and CRCs and returns a new, immutable [Png].
 *
 * This is the base class for all mutable PNG views.
 * [StreamingPngView] extends this class to add surgical channel writes
 * while inheriting the full property surface and [build] logic.
 *
 * This class is **not** constructed directly — use the [edit] extension
 * on [Png] instead:
 *
 * ```kotlin
 * val edited = pngFile.edit {
 *     ihdr = ihdr.copy(width = 1920u, height = 1080u)
 *     time = PngTime(year = 2026u, month = 2u, day = 24u,
 *                    hour = 12u, minute = 0u, second = 0u)
 * }
 * ```
 */
open class MutablePngView internal constructor(
    protected val source: Png,
) : PngView, MutableStructureView<Png> {

    // ------------------------------------------------------------------
    // Mutable property surface — var overrides of PngView's val contract
    // ------------------------------------------------------------------

    // --- Required chunks ---

    /** Image header. Always present, always first. */
    override var ihdr: PngIhdr = source.ihdr

    // --- Optional single chunks ---

    /** Palette (for indexed-colour images). */
    override var plte: PngPlte? = source.plte

    /** Transparency info. */
    override var trns: PngTrns? = source.trns

    /** Image gamma. */
    override var gama: PngGama? = source.gama

    /** Primary chromaticities and white point. */
    override var chrm: PngChrm? = source.chrm

    /** Standard RGB colour space. */
    override var srgb: PngSrgb? = source.srgb

    /** Embedded ICC profile. */
    override var iccp: PngIccp? = source.iccp

    /** Physical pixel dimensions. */
    override var phys: PngPhys? = source.phys

    /** Last-modification time. */
    override var time: PngTime? = source.time

    /** Significant bits. */
    override var sbit: PngSbit? = source.sbit

    /** Background colour. */
    override var bkgd: PngBkgd? = source.bkgd

    /** Palette histogram. */
    override var hist: PngHist? = source.hist

    /** APNG animation control. */
    override var actl: PngActl? = source.actl

    // --- Repeatable chunks (list) ---

    /** Compressed image data chunks. */
    override var idatChunks: List<PngIdat> = source.idatChunks

    /** tEXt chunks. */
    override var textChunks: List<PngTextChunk> = source.textChunks

    /** zTXt (compressed text) chunks. */
    override var ztxtChunks: List<PngZtxt> = source.ztxtChunks

    /** iTXt (international text) chunks. */
    override var itxtChunks: List<PngItxt> = source.itxtChunks

    /** sPLT (suggested palette) chunks. */
    override var spltChunks: List<PngSplt> = source.spltChunks

    /** fcTL (APNG frame control) chunks. */
    override var fctlChunks: List<PngFctl> = source.fctlChunks

    // ------------------------------------------------------------------
    // Chunk-type utilities — dirty detection, mapping, metadata
    // ------------------------------------------------------------------

    /**
     * Resolves which chunk type maps to which current (possibly mutated)
     * [BinarySerializable] value. Returns `null` for unknown or
     * list-managed types.
     */
    internal fun currentSingleChunk(type: String): BinarySerializable? = when (type) {
        "IHDR" -> ihdr
        "PLTE" -> plte
        "tRNS" -> trns
        "gAMA" -> gama
        "cHRM" -> chrm
        "sRGB" -> srgb
        "iCCP" -> iccp
        "pHYs" -> phys
        "tIME" -> time
        "sBIT" -> sbit
        "bKGD" -> bkgd
        "hIST" -> hist
        "acTL" -> actl
        else -> null
    }

    /**
     * Resolves the original parsed value for a single-occurrence chunk.
     * Returns `null` for unknown or list-managed types.
     */
    internal fun originalSingleChunk(type: String): BinarySerializable? = when (type) {
        "IHDR" -> source.ihdr
        "PLTE" -> source.plte
        "tRNS" -> source.trns
        "gAMA" -> source.gama
        "cHRM" -> source.chrm
        "sRGB" -> source.srgb
        "iCCP" -> source.iccp
        "pHYs" -> source.phys
        "tIME" -> source.time
        "sBIT" -> source.sbit
        "bKGD" -> source.bkgd
        "hIST" -> source.hist
        "acTL" -> source.actl
        else -> null
    }

    /**
     * All single-occurrence chunk fields as (type tag, current value) pairs.
     * Used for detecting newly added chunks during streaming writes.
     */
    internal fun singleChunkFields(): List<Pair<String, BinarySerializable?>> = listOf(
        "PLTE" to plte, "tRNS" to trns,
        "gAMA" to gama, "cHRM" to chrm, "sRGB" to srgb, "iCCP" to iccp,
        "pHYs" to phys, "tIME" to time, "sBIT" to sbit, "bKGD" to bkgd,
        "hIST" to hist, "acTL" to actl,
    )

    /** Chunk types that are managed as variable-length lists. */
    internal val listChunkTypes: Set<String> =
        setOf("IDAT", "tEXt", "zTXt", "iTXt", "sPLT", "fcTL")

    /** Whether any list-based chunk group was mutated. */
    internal fun listChunksDirty(): Boolean =
        idatChunks != source.idatChunks ||
        textChunks != source.textChunks ||
        ztxtChunks != source.ztxtChunks ||
        itxtChunks != source.itxtChunks ||
        spltChunks != source.spltChunks ||
        fctlChunks != source.fctlChunks

    /**
     * Well-known chunk types the view manages explicitly.
     * Anything not in this set is treated as an "unknown" chunk
     * and preserved as-is during [build].
     */
    internal val knownChunkTypes: Set<String> = setOf(
        "IHDR", "PLTE", "IDAT", "IEND",
        "cHRM", "gAMA", "iCCP", "sBIT", "sRGB",
        "bKGD", "hIST", "tRNS", "pHYs", "sPLT",
        "tEXt", "zTXt", "iTXt", "tIME",
        "acTL", "fcTL", "fdAT",
    )

    // ------------------------------------------------------------------
    // build() — reassemble chunks into a new Png
    // ------------------------------------------------------------------

    /**
     * Produce a new [Png] incorporating all mutations.
     *
     * Chunks are emitted in the order mandated by the PNG spec:
     * IHDR → colour-management → pHYs → PLTE → tRNS / hIST / bKGD / sBIT →
     * text → sPLT → IDAT → tIME → acTL / fcTL → IEND.
     *
     * Any unknown / unrecognised chunks from the original file are
     * preserved in their original relative position.
     */
    override fun build(): Png {
        val chunks = mutableListOf<PngChunk>()

        // --- IHDR (always first) ---
        chunks += buildChunk("IHDR", ihdr.toBytes().data)

        // --- Colour-management chunks (before PLTE) ---
        chrm?.let { chunks += buildChunk("cHRM", it.toBytes().data) }
        gama?.let { chunks += buildChunk("gAMA", it.toBytes().data) }
        iccp?.let { chunks += buildChunk("iCCP", it.toBytes().data) }
        sbit?.let { chunks += buildChunk("sBIT", it.toBytes().data) }
        srgb?.let { chunks += buildChunk("sRGB", it.toBytes().data) }

        // --- PLTE ---
        plte?.let { chunks += buildChunk("PLTE", it.toBytes().data) }

        // --- Chunks that depend on PLTE ---
        bkgd?.let { chunks += buildChunk("bKGD", it.toBytes().data) }
        hist?.let { chunks += buildChunk("hIST", it.toBytes().data) }
        trns?.let { chunks += buildChunk("tRNS", it.toBytes().data) }

        // --- Physical dimensions ---
        phys?.let { chunks += buildChunk("pHYs", it.toBytes().data) }

        // --- Suggested palettes ---
        for (splt in spltChunks) chunks += buildChunk("sPLT", splt.toBytes().data)

        // --- Text chunks ---
        for (t in textChunks) chunks += buildChunk("tEXt", t.toBytes().data)
        for (z in ztxtChunks) chunks += buildChunk("zTXt", z.toBytes().data)
        for (i in itxtChunks) chunks += buildChunk("iTXt", i.toBytes().data)

        // --- APNG animation control ---
        actl?.let { chunks += buildChunk("acTL", it.toBytes().data) }

        // --- APNG frame control + IDAT ---
        // Interleave fcTL and IDAT in their natural order.
        // For non-animated PNGs there are no fcTL chunks.
        if (fctlChunks.isNotEmpty()) {
            // First fcTL + all IDAT, then remaining fcTL
            val fcIterator = fctlChunks.iterator()
            if (fcIterator.hasNext()) {
                chunks += buildChunk("fcTL", fcIterator.next().toBytes().data)
            }
            for (idat in idatChunks) chunks += buildChunk("IDAT", idat.compressedData.data)
            while (fcIterator.hasNext()) {
                chunks += buildChunk("fcTL", fcIterator.next().toBytes().data)
            }
        } else {
            for (idat in idatChunks) chunks += buildChunk("IDAT", idat.compressedData.data)
        }

        // --- Modification time ---
        time?.let { chunks += buildChunk("tIME", it.toBytes().data) }

        // --- Preserve unknown chunks from the original ---
        for (chunk in source.chunks) {
            if (chunk.type.value !in knownChunkTypes) {
                chunks += chunk
            }
        }

        // --- IEND (always last) ---
        chunks += buildChunk("IEND", ByteArray(0))

        return Png(
            signature = Png.SIGNATURE.asBytes(),
            chunks = chunks,
        )
    }
}

// ---------------------------------------------------------------------------
// edit() extension — the public API entry point
// ---------------------------------------------------------------------------

/**
 * Create a modified copy of this [Png] by mutating properties
 * inside the [block].
 *
 * The receiver inside [block] is a [MutablePngView] — every well-known
 * chunk is exposed as a `var` that you can reassign.  When the block
 * returns, the view reassembles the chunk list with correct CRCs and
 * returns a new, immutable [Png].
 *
 * ```kotlin
 * val resized = original.edit {
 *     ihdr = ihdr.copy(width = 640u, height = 480u)
 * }
 * ```
 */
fun Png.edit(block: MutablePngView.() -> Unit): Png =
    MutablePngView(this).apply(block).build()
