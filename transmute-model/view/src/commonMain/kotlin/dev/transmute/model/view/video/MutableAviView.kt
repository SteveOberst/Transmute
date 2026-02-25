@file:Suppress("unused")

package dev.transmute.model.view.video

import dev.transmute.model.structure.common.RiffChunk
import dev.transmute.model.structure.video.*
import dev.transmute.model.view.MutableStructureView

// ---------------------------------------------------------------------------
// MutableAviView — the mutator for Avi
// ---------------------------------------------------------------------------

/**
 * Mutable view over an [Avi].
 *
 * The [riff] chunk is exposed as a `var`; computed properties
 * ([chunks], [headerList], [mainHeader], etc.) automatically reflect
 * the current RIFF container.
 *
 * ```kotlin
 * val edited = aviFile.edit {
 *     riff = riff.copy(children = riff.children.filter { it.id.value != "idx1" })
 * }
 * ```
 */
open class MutableAviView internal constructor(
    protected val source: Avi,
) : AviView, MutableStructureView<Avi> {

    // --- Mutable field ---

    override var riff: RiffChunk = source.riff

    // --- Computed accessors (re-derived from current state) ---

    private fun currentFile() = Avi(riff)

    override val chunks: List<RiffChunk> get() = currentFile().chunks
    override val headerList: RiffChunk? get() = currentFile().headerList
    override val movieList: RiffChunk? get() = currentFile().movieList
    override val indexChunk: RiffChunk? get() = currentFile().indexChunk
    override val mainHeader: AviMainHeader? get() = currentFile().mainHeader
    override val streamCount: Int get() = currentFile().streamCount

    // --- Build ---

    override fun build(): Avi = Avi(riff = riff)
}

// ---------------------------------------------------------------------------
// edit() extension
// ---------------------------------------------------------------------------

/**
 * Create a modified copy of this [Avi] by mutating properties
 * inside the [block].
 */
fun Avi.edit(block: MutableAviView.() -> Unit): Avi =
    MutableAviView(this).apply(block).build()
