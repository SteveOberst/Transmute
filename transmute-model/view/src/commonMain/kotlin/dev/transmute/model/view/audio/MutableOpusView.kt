@file:Suppress("unused")

package dev.transmute.model.view.audio

import dev.transmute.model.core.Channels
import dev.transmute.model.core.Hertz
import dev.transmute.model.structure.audio.*
import dev.transmute.model.structure.common.OggPage
import dev.transmute.model.structure.common.OggSerialNumber
import dev.transmute.model.view.MutableStructureView

// ---------------------------------------------------------------------------
// MutableOpusView — the mutator for Opus
// ---------------------------------------------------------------------------

/**
 * Mutable view over an [Opus].
 *
 * The [pages] list is exposed as a `var`; computed properties
 * ([opusIdentification], [sampleRate], etc.) automatically reflect
 * the current page list.
 *
 * ```kotlin
 * val edited = opusFile.edit {
 *     pages = pages.drop(1)  // remove first page
 * }
 * ```
 */
open class MutableOpusView internal constructor(
    protected val source: Opus,
) : OpusView, MutableStructureView<Opus> {

    // --- Mutable field ---

    override var pages: List<OggPage> = source.pages

    // --- Computed accessors (re-derived from current state) ---

    private fun currentFile() = Opus(pages)

    override val streamSerialNumbers: List<OggSerialNumber> get() = currentFile().streamSerialNumbers
    override val opusIdentification: OpusIdentification? get() = currentFile().opusIdentification
    override val sampleRate: Hertz get() = currentFile().sampleRate
    override val channels: Channels? get() = currentFile().channels
    override val preSkipSamples: Int get() = currentFile().preSkipSamples
    override val outputGain: Short get() = currentFile().outputGain

    // --- Build ---

    override fun build(): Opus = Opus(pages = pages)
}

// ---------------------------------------------------------------------------
// edit() extension
// ---------------------------------------------------------------------------

/**
 * Create a modified copy of this [Opus] by mutating properties
 * inside the [block].
 */
fun Opus.edit(block: MutableOpusView.() -> Unit): Opus =
    MutableOpusView(this).apply(block).build()
