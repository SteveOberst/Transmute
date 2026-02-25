@file:Suppress("unused")

package dev.transmute.model.view.audio

import dev.transmute.model.core.Channels
import dev.transmute.model.core.Hertz
import dev.transmute.model.structure.audio.*
import dev.transmute.model.structure.common.OggPage
import dev.transmute.model.structure.common.OggSerialNumber
import dev.transmute.model.view.StructureView

// ---------------------------------------------------------------------------
// OpusView — read-only view contract for Opus files
// ---------------------------------------------------------------------------

/**
 * Read-only view over an [Opus].
 *
 * | Tier | Class | Use case |
 * |------|-------|----------|
 * | **Immutable** | via [Opus.view] | Read-only inspection |
 * | **Mutable** | [MutableOpusView] | In-memory rebuild |
 */
interface OpusView : StructureView<Opus> {

    /** All Ogg pages in stream order. */
    val pages: List<OggPage>

    // --- Computed accessors ---

    /** Distinct stream serial numbers found in the pages. */
    val streamSerialNumbers: List<OggSerialNumber>

    /** Parsed Opus identification header. */
    val opusIdentification: OpusIdentification?

    /** Sample rate (defaults to 48000 Hz per Opus spec). */
    val sampleRate: Hertz

    /** Channel count from the Opus identification header. */
    val channels: Channels?

    /** Pre-skip samples. */
    val preSkipSamples: Int

    /** Output gain (Q7.8 dB). */
    val outputGain: Short
}

// ---------------------------------------------------------------------------
// ImmutableOpusView
// ---------------------------------------------------------------------------

private class ImmutableOpusView(private val file: Opus) : OpusView {
    override val pages get() = file.pages
    override val streamSerialNumbers get() = file.streamSerialNumbers
    override val opusIdentification get() = file.opusIdentification
    override val sampleRate get() = file.sampleRate
    override val channels get() = file.channels
    override val preSkipSamples get() = file.preSkipSamples
    override val outputGain get() = file.outputGain
}

/**
 * Obtain a read-only [OpusView] over this file.
 */
fun Opus.inspect(): OpusView = ImmutableOpusView(this)
