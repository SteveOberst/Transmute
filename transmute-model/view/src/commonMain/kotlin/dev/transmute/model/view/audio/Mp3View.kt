@file:Suppress("unused")

package dev.transmute.model.view.audio

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.Channels
import dev.transmute.model.core.Hertz
import dev.transmute.model.structure.audio.*
import dev.transmute.model.view.StructureView

// ---------------------------------------------------------------------------
// Mp3View — read-only view contract for MP3 files
// ---------------------------------------------------------------------------

/**
 * Read-only view over an [Mp3].
 *
 * | Tier | Class | Use case |
 * |------|-------|----------|
 * | **Immutable** | via [Mp3.view] | Read-only inspection |
 * | **Mutable** | [MutableMp3View] | In-memory rebuild |
 */
interface Mp3View : StructureView<Mp3> {

    // --- Constructor fields ---

    /** ID3v2 tag data, if present. */
    val id3v2Tag: Bytes?

    /** Raw audio frame data. */
    val audioData: Bytes

    /** ID3v1 tag data (128 bytes), if present. */
    val id3v1TagData: Bytes?

    // --- Computed accessors ---

    /** Parsed first MPEG audio frame header. */
    val firstFrameHeader: Mp3FrameHeader?

    /** Sample rate from the first frame header. */
    val sampleRate: Hertz?

    /** Channel count from the first frame header. */
    val channels: Channels?

    /** Parsed ID3v1 tag. */
    val id3v1Tag: Mp3Id3v1Tag?
}

// ---------------------------------------------------------------------------
// ImmutableMp3View
// ---------------------------------------------------------------------------

private class ImmutableMp3View(private val file: Mp3) : Mp3View {
    override val id3v2Tag get() = file.id3v2Tag
    override val audioData get() = file.audioData
    override val id3v1TagData get() = file.id3v1TagData
    override val firstFrameHeader get() = file.firstFrameHeader
    override val sampleRate get() = file.sampleRate
    override val channels get() = file.channels
    override val id3v1Tag get() = file.id3v1Tag
}

/**
 * Obtain a read-only [Mp3View] over this file.
 */
fun Mp3.inspect(): Mp3View = ImmutableMp3View(this)
