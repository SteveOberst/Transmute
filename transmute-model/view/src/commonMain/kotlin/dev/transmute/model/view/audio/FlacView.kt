@file:Suppress("unused")

package dev.transmute.model.view.audio

import dev.transmute.model.core.BitsPerSample
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.Channels
import dev.transmute.model.core.Hertz
import dev.transmute.model.structure.audio.*
import dev.transmute.model.view.StructureView

// ---------------------------------------------------------------------------
// FlacView — read-only view contract for FLAC files
// ---------------------------------------------------------------------------

/**
 * Read-only view over a [Flac].
 *
 * | Tier | Class | Use case |
 * |------|-------|----------|
 * | **Immutable** | via [Flac.view] | Read-only inspection |
 * | **Mutable** | [MutableFlacView] | In-memory rebuild |
 */
interface FlacView : StructureView<Flac> {

    // --- Constructor fields ---

    /** Metadata blocks in file order. */
    val metadataBlocks: List<FlacMetadataBlock>

    /** Raw audio frame data. */
    val audioData: Bytes

    // --- Computed accessors ---

    /** The STREAMINFO block. */
    val streamInfoBlock: FlacMetadataBlock?

    /** Parsed STREAMINFO data. */
    val streamInfo: FlacStreamInfo?

    /** Sample rate from STREAMINFO. */
    val sampleRate: Hertz?

    /** Number of audio channels from STREAMINFO. */
    val channels: Channels?

    /** Bits per sample from STREAMINFO. */
    val bitsPerSample: BitsPerSample?

    /** Vorbis comment block, if present. */
    val vorbisCommentBlock: FlacMetadataBlock?

    /** Picture block(s), if present. */
    val pictureBlocks: List<FlacMetadataBlock>
}

// ---------------------------------------------------------------------------
// ImmutableFlacView
// ---------------------------------------------------------------------------

private class ImmutableFlacView(private val file: Flac) : FlacView {
    override val metadataBlocks get() = file.metadataBlocks
    override val audioData get() = file.audioData
    override val streamInfoBlock get() = file.streamInfoBlock
    override val streamInfo get() = file.streamInfo
    override val sampleRate get() = file.sampleRate
    override val channels get() = file.channels
    override val bitsPerSample get() = file.bitsPerSample
    override val vorbisCommentBlock get() = file.vorbisCommentBlock
    override val pictureBlocks get() = file.pictureBlocks
}

/**
 * Obtain a read-only [FlacView] over this file.
 */
fun Flac.inspect(): FlacView = ImmutableFlacView(this)
