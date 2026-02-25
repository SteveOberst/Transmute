@file:Suppress("unused")

package dev.transmute.model.view.audio

import dev.transmute.model.core.BitsPerSample
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.Channels
import dev.transmute.model.core.Hertz
import dev.transmute.model.structure.audio.*
import dev.transmute.model.view.MutableStructureView

// ---------------------------------------------------------------------------
// MutableFlacView — the mutator for Flac
// ---------------------------------------------------------------------------

/**
 * Mutable view over a [Flac].
 *
 * Constructor fields ([metadataBlocks], [audioData]) are exposed as
 * `var` properties.  Computed accessors automatically reflect the
 * current mutable state.
 *
 * ```kotlin
 * val edited = flacFile.edit {
 *     // Remove picture blocks
 *     metadataBlocks = metadataBlocks.filter { it.blockType != FlacMetadataBlockType.Picture }
 * }
 * ```
 */
open class MutableFlacView internal constructor(
    protected val source: Flac,
) : FlacView, MutableStructureView<Flac> {

    // --- Mutable fields ---

    override var metadataBlocks: List<FlacMetadataBlock> = source.metadataBlocks
    override var audioData: Bytes = source.audioData

    // --- Computed accessors (re-derived from current state) ---

    private fun currentFile() = Flac(metadataBlocks, audioData)

    override val streamInfoBlock: FlacMetadataBlock? get() = currentFile().streamInfoBlock
    override val streamInfo: FlacStreamInfo? get() = currentFile().streamInfo
    override val sampleRate: Hertz? get() = currentFile().sampleRate
    override val channels: Channels? get() = currentFile().channels
    override val bitsPerSample: BitsPerSample? get() = currentFile().bitsPerSample
    override val vorbisCommentBlock: FlacMetadataBlock? get() = currentFile().vorbisCommentBlock
    override val pictureBlocks: List<FlacMetadataBlock> get() = currentFile().pictureBlocks

    // --- Build ---

    override fun build(): Flac = Flac(
        metadataBlocks = metadataBlocks,
        audioData = audioData,
    )
}

// ---------------------------------------------------------------------------
// edit() extension
// ---------------------------------------------------------------------------

/**
 * Create a modified copy of this [Flac] by mutating properties
 * inside the [block].
 */
fun Flac.edit(block: MutableFlacView.() -> Unit): Flac =
    MutableFlacView(this).apply(block).build()
