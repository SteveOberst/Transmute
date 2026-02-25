@file:Suppress("unused")

package dev.transmute.model.view.audio

import dev.transmute.model.core.Channels
import dev.transmute.model.core.Hertz
import dev.transmute.model.structure.audio.*
import dev.transmute.model.structure.common.OggPage
import dev.transmute.model.structure.common.OggSerialNumber
import dev.transmute.model.view.MutableStructureView

// ---------------------------------------------------------------------------
// MutableOggAudioView — the mutator for OggAudio
// ---------------------------------------------------------------------------

/**
 * Mutable view over an [OggAudio].
 *
 * The [pages] list is exposed as a `var`; computed properties
 * ([vorbisIdentification], [sampleRate], etc.) automatically reflect
 * the current page list.
 *
 * ```kotlin
 * val edited = oggAudioFile.edit {
 *     pages = pages.filter { it.serialNumber == targetSerial }
 * }
 * ```
 */
open class MutableOggAudioView internal constructor(
    protected val source: OggAudio,
) : OggAudioView, MutableStructureView<OggAudio> {

    // --- Mutable field ---

    override var pages: List<OggPage> = source.pages

    // --- Computed accessors (re-derived from current state) ---

    private fun currentFile() = OggAudio(pages)

    override val streamSerialNumbers: List<OggSerialNumber> get() = currentFile().streamSerialNumbers
    override val vorbisIdentification: VorbisIdentification? get() = currentFile().vorbisIdentification
    override val sampleRate: Hertz? get() = currentFile().sampleRate
    override val channels: Channels? get() = currentFile().channels

    // --- Build ---

    override fun build(): OggAudio = OggAudio(pages = pages)
}

// ---------------------------------------------------------------------------
// edit() extension
// ---------------------------------------------------------------------------

/**
 * Create a modified copy of this [OggAudio] by mutating properties
 * inside the [block].
 */
fun OggAudio.edit(block: MutableOggAudioView.() -> Unit): OggAudio =
    MutableOggAudioView(this).apply(block).build()
