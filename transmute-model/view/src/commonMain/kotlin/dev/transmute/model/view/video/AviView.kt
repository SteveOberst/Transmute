@file:Suppress("unused")

package dev.transmute.model.view.video

import dev.transmute.model.structure.common.RiffChunk
import dev.transmute.model.structure.video.*
import dev.transmute.model.view.StructureView

// ---------------------------------------------------------------------------
// AviView — read-only view contract for AVI files
// ---------------------------------------------------------------------------

/**
 * Read-only view over an [Avi].
 *
 * | Tier | Class | Use case |
 * |------|-------|----------|
 * | **Immutable** | via [Avi.inspect] | Read-only inspection |
 * | **Mutable** | [MutableAviView] | In-memory rebuild |
 */
interface AviView : StructureView<Avi> {

    /** The top-level RIFF container chunk. */
    val riff: RiffChunk

    // --- Computed accessors ---

    /** Sub-chunks inside the RIFF container. */
    val chunks: List<RiffChunk>

    /** The `hdrl` header LIST chunk. */
    val headerList: RiffChunk?

    /** The `movi` movie data LIST chunk. */
    val movieList: RiffChunk?

    /** The `idx1` index chunk. */
    val indexChunk: RiffChunk?

    /** Parsed AVI main header (`avih`). */
    val mainHeader: AviMainHeader?

    /** Number of stream header (`strl`) entries. */
    val streamCount: Int
}

// ---------------------------------------------------------------------------
// ImmutableAviView
// ---------------------------------------------------------------------------

private class ImmutableAviView(private val file: Avi) : AviView {
    override val riff get() = file.riff
    override val chunks get() = file.chunks
    override val headerList get() = file.headerList
    override val movieList get() = file.movieList
    override val indexChunk get() = file.indexChunk
    override val mainHeader get() = file.mainHeader
    override val streamCount get() = file.streamCount
}

/**
 * Obtain a read-only [AviView] over this file.
 */
fun Avi.inspect(): AviView = ImmutableAviView(this)
