@file:Suppress("unused")

package dev.transmute.model.view.audio

import dev.transmute.model.core.BitsPerSample
import dev.transmute.model.core.Channels
import dev.transmute.model.core.Hertz
import dev.transmute.model.structure.audio.*
import dev.transmute.model.structure.common.RiffChunk
import dev.transmute.model.view.StructureView

// ---------------------------------------------------------------------------
// WavView — read-only view contract for WAV files
// ---------------------------------------------------------------------------

/**
 * Read-only view over a [Wav].
 *
 * | Tier | Class | Use case |
 * |------|-------|----------|
 * | **Immutable** | via [Wav.view] | Read-only inspection |
 * | **Mutable** | [MutableWavView] | In-memory rebuild |
 */
interface WavView : StructureView<Wav> {

    /** The top-level RIFF container chunk. */
    val riff: RiffChunk

    /** Sub-chunks inside the RIFF container. */
    val chunks: List<RiffChunk>

    /** Parsed `fmt ` data. */
    val fmt: WavFmtChunk?

    /** The raw `fmt ` chunk. */
    val fmtChunk: RiffChunk?

    /** The raw `data` chunk. */
    val dataChunk: RiffChunk?

    /** Sample rate from the `fmt ` chunk. */
    val sampleRate: Hertz?

    /** Channel count from the `fmt ` chunk. */
    val channels: Channels?

    /** Bits per sample from the `fmt ` chunk. */
    val bitsPerSample: BitsPerSample?

    /** Resolved audio format. */
    val audioFormat: WavAudioFormat?
}

// ---------------------------------------------------------------------------
// ImmutableWavView
// ---------------------------------------------------------------------------

private class ImmutableWavView(private val file: Wav) : WavView {
    override val riff get() = file.riff
    override val chunks get() = file.chunks
    override val fmt get() = file.fmt
    override val fmtChunk get() = file.fmtChunk
    override val dataChunk get() = file.dataChunk
    override val sampleRate get() = file.sampleRate
    override val channels get() = file.channels
    override val bitsPerSample get() = file.bitsPerSample
    override val audioFormat get() = file.audioFormat
}

/**
 * Obtain a read-only [WavView] over this file.
 */
fun Wav.inspect(): WavView = ImmutableWavView(this)
