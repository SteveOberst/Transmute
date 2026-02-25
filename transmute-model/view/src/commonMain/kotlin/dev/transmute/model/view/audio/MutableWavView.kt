@file:Suppress("unused")

package dev.transmute.model.view.audio

import dev.transmute.model.core.BitsPerSample
import dev.transmute.model.core.Channels
import dev.transmute.model.core.Hertz
import dev.transmute.model.structure.audio.*
import dev.transmute.model.structure.common.RiffChunk
import dev.transmute.model.view.MutableStructureView

// ---------------------------------------------------------------------------
// MutableWavView — the mutator for Wav
// ---------------------------------------------------------------------------

/**
 * Mutable view over a [Wav].
 *
 * The [riff] chunk is exposed as a `var`; computed properties
 * ([chunks], [fmt], [sampleRate], etc.) automatically reflect the
 * current RIFF container.
 *
 * ```kotlin
 * val edited = wavFile.edit {
 *     // Replace the RIFF container (e.g. strip unknown chunks)
 *     riff = riff.copy(children = riff.children.filter { it.id.value in setOf("fmt ", "data") })
 * }
 * ```
 */
open class MutableWavView internal constructor(
    protected val source: Wav,
) : WavView, MutableStructureView<Wav> {

    // --- Mutable field ---

    override var riff: RiffChunk = source.riff

    // --- Computed accessors (re-derived from current state) ---

    private fun currentFile() = Wav(riff)

    override val chunks: List<RiffChunk> get() = currentFile().chunks
    override val fmt: WavFmtChunk? get() = currentFile().fmt
    override val fmtChunk: RiffChunk? get() = currentFile().fmtChunk
    override val dataChunk: RiffChunk? get() = currentFile().dataChunk
    override val sampleRate: Hertz? get() = currentFile().sampleRate
    override val channels: Channels? get() = currentFile().channels
    override val bitsPerSample: BitsPerSample? get() = currentFile().bitsPerSample
    override val audioFormat: WavAudioFormat? get() = currentFile().audioFormat

    // --- Build ---

    override fun build(): Wav = Wav(riff = riff)
}

// ---------------------------------------------------------------------------
// edit() extension
// ---------------------------------------------------------------------------

/**
 * Create a modified copy of this [Wav] by mutating properties
 * inside the [block].
 */
fun Wav.edit(block: MutableWavView.() -> Unit): Wav =
    MutableWavView(this).apply(block).build()
