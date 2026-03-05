@file:Suppress("unused")

package dev.transmute.model.structure.image

import dev.transmute.model.core.MediaStructure
import dev.transmute.model.core.asBytes
import dev.transmute.model.identify.FourCC
import dev.transmute.model.structure.image.types.PngActl
import dev.transmute.model.structure.image.types.PngBkgd
import dev.transmute.model.structure.image.types.PngChrm
import dev.transmute.model.structure.image.types.PngChunk
import dev.transmute.model.structure.image.types.PngFctl
import dev.transmute.model.structure.image.types.PngGama
import dev.transmute.model.structure.image.types.PngHist
import dev.transmute.model.structure.image.types.PngIccp
import dev.transmute.model.structure.image.types.PngIdat
import dev.transmute.model.structure.image.types.PngIhdr
import dev.transmute.model.structure.image.types.PngItxt
import dev.transmute.model.structure.image.types.PngPhys
import dev.transmute.model.structure.image.types.PngPlte
import dev.transmute.model.structure.image.types.PngRaw
import dev.transmute.model.structure.image.types.PngSbit
import dev.transmute.model.structure.image.types.PngSplt
import dev.transmute.model.structure.image.types.PngSrgb
import dev.transmute.model.structure.image.types.PngTextChunk
import dev.transmute.model.structure.image.types.PngTime
import dev.transmute.model.structure.image.types.PngTrns
import dev.transmute.model.structure.image.types.PngZtxt
import dev.transmute.model.structure.image.types.actl
import dev.transmute.model.structure.image.types.bkgd
import dev.transmute.model.structure.image.types.chrm
import dev.transmute.model.structure.image.types.fctlChunks
import dev.transmute.model.structure.image.types.gama
import dev.transmute.model.structure.image.types.hist
import dev.transmute.model.structure.image.types.iccp
import dev.transmute.model.structure.image.types.ihdr
import dev.transmute.model.structure.image.types.itxtChunks
import dev.transmute.model.structure.image.types.phys
import dev.transmute.model.structure.image.types.plte
import dev.transmute.model.structure.image.types.sbit
import dev.transmute.model.structure.image.types.spltChunks
import dev.transmute.model.structure.image.types.srgb
import dev.transmute.model.structure.image.types.textChunks
import dev.transmute.model.structure.image.types.time
import dev.transmute.model.structure.image.types.trns
import dev.transmute.model.structure.image.types.ztxtChunks
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
  /** Zero-based index into the original [dev.transmute.model.structure.image.types.PngRaw.chunks] list. */
  val chunkIndex: Int,
)

/**
 * Ordered, JSON-safe summary of a single PNG chunk.
 *
 * This preserves the on-disk chunk sequence while omitting payload bytes.
 */
@Serializable
data class PngChunkLayoutEntry(
  /** Zero-based index in the original [dev.transmute.model.structure.image.types.PngRaw.chunks] list. */
  val chunkIndex: Int,
  /** Chunk type tag (FourCC), e.g. `IHDR`, `IDAT`, `tEXt`. */
  val type: String,
  /** Length of the chunk data field in bytes. */
  val dataLength: Int,
)

// ---------------------------------------------------------------------------
// PngStructure - the serialisable, JSON-friendly view of a PNG file
// ---------------------------------------------------------------------------

/**
 * A structured, JSON-serialisable representation of a PNG file.
 *
 * Unlike [dev.transmute.model.structure.image.types.PngRaw] - which mirrors the binary on-disk layout - this
 * class exposes all well-known chunks as typed, named fields.  Blob-heavy
 * data (IDAT compressed image data) is replaced by summary statistics
 * ([idatCount], [idatTotalBytes]).
 *
 * The [rawChunks] field is marked [@Transient] and therefore excluded from
 * JSON serialisation.  It is populated when a [PngStructure] is created from
 * a [dev.transmute.model.structure.image.types.PngRaw] via [dev.transmute.model.structure.image.types.PngRaw.toStructure], and is used by [Editor.build] to
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
  /** All chunks in file order (payload bytes excluded). */
  val chunkLayout: List<PngChunkLayoutEntry> = emptyList(),
  /** Summaries of unrecognised / extension chunks. */
  val unknownChunks: List<PngUnknownChunkSummary> = emptyList(),
  /**
   * Original [dev.transmute.model.structure.image.types.PngChunk] list from the source [dev.transmute.model.structure.image.types.PngRaw].
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
   * the source structure.  Call [build] to produce a new, immutable [dev.transmute.model.structure.image.types.PngRaw]
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

    /** IDAT chunks extracted from the original [dev.transmute.model.structure.image.types.PngRaw] (read-only). */
    private val rawIdat: List<PngIdat> = source.rawChunks
      .filter { it.type.value == "IDAT" }
      .map { PngIdat(it.data) }

    /**
     * Reassemble this editor's state into a new [dev.transmute.model.structure.image.types.PngRaw] with correct
     * chunk order and recomputed CRCs.
     *
     * Chunk ordering follows the PNG specification:
     * IHDR -> colour-management -> PLTE -> tRNS/hIST/bKGD/sBIT -> pHYs ->
     * sPLT -> text -> acTL -> fcTL/IDAT -> tIME -> unknown -> IEND.
     *
     * Unknown chunks from the original file are preserved in place.
     *
     * > **Note**: IDAT bytes come from the original [dev.transmute.model.structure.image.types.PngRaw] passed to
     * > [dev.transmute.model.structure.image.types.PngRaw.toStructure].  If this [PngStructure] was deserialised
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
fun PngStructure.toRaw(): PngRaw = PngStructure.Editor(this).build()

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
    chunkLayout = chunks.mapIndexed { idx, chunk ->
      PngChunkLayoutEntry(
        chunkIndex = idx,
        type = chunk.type.value,
        dataLength = chunk.data.size,
      )
    },
    unknownChunks = unknownChunks,
    rawChunks = chunks,
  )
}
