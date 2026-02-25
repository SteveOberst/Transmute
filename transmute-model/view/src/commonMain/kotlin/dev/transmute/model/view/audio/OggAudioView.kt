@file:Suppress("unused")

package dev.transmute.model.view.audio

import dev.transmute.model.core.Channels
import dev.transmute.model.core.Hertz
import dev.transmute.model.structure.audio.*
import dev.transmute.model.structure.common.OggPage
import dev.transmute.model.structure.common.OggSerialNumber
import dev.transmute.model.view.StructureView

// ---------------------------------------------------------------------------
// OggAudioView — read-only view contract for Ogg Vorbis files
// ---------------------------------------------------------------------------

/**
 * Read-only view over an [OggAudio].
 *
 * | Tier | Class | Use case |
 * |------|-------|----------|
 * | **Immutable** | via [OggAudio.view] | Read-only inspection |
 * | **Mutable** | [MutableOggAudioView] | In-memory rebuild |
 */
interface OggAudioView : StructureView<OggAudio> {

    /** All Ogg pages in stream order. */
    val pages: List<OggPage>

    // --- Computed accessors ---

    /** Distinct stream serial numbers found in the pages. */
    val streamSerialNumbers: List<OggSerialNumber>

    /** Parsed Vorbis identification header. */
    val vorbisIdentification: VorbisIdentification?

    /** Sample rate from the Vorbis identification header. */
    val sampleRate: Hertz?

    /** Channel count from the Vorbis identification header. */
    val channels: Channels?
}

// ---------------------------------------------------------------------------
// ImmutableOggAudioView
// ---------------------------------------------------------------------------

private class ImmutableOggAudioView(private val file: OggAudio) : OggAudioView {
    override val pages get() = file.pages
    override val streamSerialNumbers get() = file.streamSerialNumbers
    override val vorbisIdentification get() = file.vorbisIdentification
    override val sampleRate get() = file.sampleRate
    override val channels get() = file.channels
}

/**
 * Obtain a read-only [OggAudioView] over this file.
 */
fun OggAudio.inspect(): OggAudioView = ImmutableOggAudioView(this)
