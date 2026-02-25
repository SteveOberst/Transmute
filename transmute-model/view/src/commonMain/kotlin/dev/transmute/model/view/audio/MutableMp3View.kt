@file:Suppress("unused")

package dev.transmute.model.view.audio

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.Channels
import dev.transmute.model.core.Hertz
import dev.transmute.model.structure.audio.*
import dev.transmute.model.view.MutableStructureView

// ---------------------------------------------------------------------------
// MutableMp3View — the mutator for Mp3
// ---------------------------------------------------------------------------

/**
 * Mutable view over an [Mp3].
 *
 * Constructor fields ([id3v2Tag], [audioData], [id3v1TagData]) are
 * exposed as `var` properties.  Computed accessors automatically
 * reflect the current mutable state.
 *
 * ```kotlin
 * val edited = mp3File.edit {
 *     id3v1TagData = null  // strip ID3v1 tag
 * }
 * ```
 */
open class MutableMp3View internal constructor(
    protected val source: Mp3,
) : Mp3View, MutableStructureView<Mp3> {

    // --- Mutable fields ---

    override var id3v2Tag: Bytes? = source.id3v2Tag
    override var audioData: Bytes = source.audioData
    override var id3v1TagData: Bytes? = source.id3v1TagData

    // --- Computed accessors (re-derived from current state) ---

    private fun currentFile() = Mp3(id3v2Tag, audioData, id3v1TagData)

    override val firstFrameHeader: Mp3FrameHeader? get() = currentFile().firstFrameHeader
    override val sampleRate: Hertz? get() = currentFile().sampleRate
    override val channels: Channels? get() = currentFile().channels
    override val id3v1Tag: Mp3Id3v1Tag? get() = currentFile().id3v1Tag

    // --- Build ---

    override fun build(): Mp3 = Mp3(
        id3v2Tag = id3v2Tag,
        audioData = audioData,
        id3v1TagData = id3v1TagData,
    )
}

// ---------------------------------------------------------------------------
// edit() extension
// ---------------------------------------------------------------------------

/**
 * Create a modified copy of this [Mp3] by mutating properties
 * inside the [block].
 */
fun Mp3.edit(block: MutableMp3View.() -> Unit): Mp3 =
    MutableMp3View(this).apply(block).build()
