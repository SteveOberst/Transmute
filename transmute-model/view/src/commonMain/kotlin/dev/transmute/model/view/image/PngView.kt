@file:Suppress("unused")

package dev.transmute.model.view.image

import dev.transmute.model.structure.image.*
import dev.transmute.model.view.StructureView

// ---------------------------------------------------------------------------
// PngView — read-only view contract for PNG files
// ---------------------------------------------------------------------------

/**
 * Read-only view over a [Png].
 *
 * Declares every well-known chunk as a `val` property: code that
 * accepts a `PngView` can inspect the file but never mutate it.
 *
 * This is the common supertype for the three PNG view tiers:
 *
 * | Tier | Class | Use case |
 * |------|-------|----------|
 * | **Immutable** | via [Png.view] | Read-only inspection |
 * | **Mutable** | [MutablePngView] | In-memory rebuild |
 * | **Streaming** | [StreamingPngView] | Surgical channel writes |
 *
 * When implementing a new format, follow this pattern:
 * 1. Define an `XxxView` interface with `val` properties
 * 2. Implement `MutableXxxView` (`open class`, `var` overrides + `build()`)
 * 3. Implement `StreamingXxxView` (extends `MutableXxxView`, adds `flush()`)
 * 4. Add `XxxFile.view()` for immutable access (private wrapper class)
 */
interface PngView : StructureView<Png> {

    // --- Required chunks ---

    /** Image header. Always present, always first. */
    val ihdr: PngIhdr

    // --- Optional single chunks ---

    /** Palette (for indexed-colour images). */
    val plte: PngPlte?

    /** Transparency info. */
    val trns: PngTrns?

    /** Image gamma. */
    val gama: PngGama?

    /** Primary chromaticities and white point. */
    val chrm: PngChrm?

    /** Standard RGB colour space. */
    val srgb: PngSrgb?

    /** Embedded ICC profile. */
    val iccp: PngIccp?

    /** Physical pixel dimensions. */
    val phys: PngPhys?

    /** Last-modification time. */
    val time: PngTime?

    /** Significant bits. */
    val sbit: PngSbit?

    /** Background colour. */
    val bkgd: PngBkgd?

    /** Palette histogram. */
    val hist: PngHist?

    /** APNG animation control. */
    val actl: PngActl?

    // --- Repeatable chunks (list) ---

    /** Compressed image data chunks. */
    val idatChunks: List<PngIdat>

    /** tEXt chunks. */
    val textChunks: List<PngTextChunk>

    /** zTXt (compressed text) chunks. */
    val ztxtChunks: List<PngZtxt>

    /** iTXt (international text) chunks. */
    val itxtChunks: List<PngItxt>

    /** sPLT (suggested palette) chunks. */
    val spltChunks: List<PngSplt>

    /** fcTL (APNG frame control) chunks. */
    val fctlChunks: List<PngFctl>
}

// ---------------------------------------------------------------------------
// ImmutablePngView — zero-copy read-only wrapper around Png
// ---------------------------------------------------------------------------

/**
 * Immutable [PngView] that delegates every property to the
 * underlying [Png]'s computed accessors.
 *
 * Allocates no extra state — each read goes straight to the
 * parsed chunk data. Obtain via [Png.view].
 */
private class ImmutablePngView(private val file: Png) : PngView {
    override val ihdr: PngIhdr get() = file.ihdr
    override val plte: PngPlte? get() = file.plte
    override val trns: PngTrns? get() = file.trns
    override val gama: PngGama? get() = file.gama
    override val chrm: PngChrm? get() = file.chrm
    override val srgb: PngSrgb? get() = file.srgb
    override val iccp: PngIccp? get() = file.iccp
    override val phys: PngPhys? get() = file.phys
    override val time: PngTime? get() = file.time
    override val sbit: PngSbit? get() = file.sbit
    override val bkgd: PngBkgd? get() = file.bkgd
    override val hist: PngHist? get() = file.hist
    override val actl: PngActl? get() = file.actl
    override val idatChunks: List<PngIdat> get() = file.idatChunks
    override val textChunks: List<PngTextChunk> get() = file.textChunks
    override val ztxtChunks: List<PngZtxt> get() = file.ztxtChunks
    override val itxtChunks: List<PngItxt> get() = file.itxtChunks
    override val spltChunks: List<PngSplt> get() = file.spltChunks
    override val fctlChunks: List<PngFctl> get() = file.fctlChunks
}

// ---------------------------------------------------------------------------
// Png.view() — the public entry point for read-only access
// ---------------------------------------------------------------------------

/**
 * Obtain a read-only [PngView] over this file.
 *
 * The returned view delegates every property to this [Png]'s
 * parsed accessors with zero additional allocation.
 *
 * ```kotlin
 * val view: PngView = pngFile.view()
 * println("${view.ihdr.width} × ${view.ihdr.height}")
 * ```
 */
fun Png.view(): PngView = ImmutablePngView(this)
