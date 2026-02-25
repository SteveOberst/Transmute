@file:Suppress("unused")

package dev.transmute.model.view.image

import dev.transmute.model.core.Pixels
import dev.transmute.model.structure.image.*
import dev.transmute.model.view.MutableStructureView

// ---------------------------------------------------------------------------
// MutableGifView — the mutator for Gif
// ---------------------------------------------------------------------------

/**
 * Mutable view over a [Gif].
 *
 * Constructor fields ([version], [screenDescriptor], [globalColorTable],
 * [blocks]) are exposed as `var` properties.  Computed accessors
 * automatically reflect the current mutable state.
 *
 * ```kotlin
 * val edited = gifFile.edit {
 *     screenDescriptor = screenDescriptor.copy(width = 640u)
 * }
 * ```
 */
open class MutableGifView internal constructor(
    protected val source: Gif,
) : GifView, MutableStructureView<Gif> {

    // --- Mutable fields ---

    override var version: GifVersion = source.version
    override var screenDescriptor: GifLogicalScreenDescriptor = source.screenDescriptor
    override var globalColorTable: List<GifColor> = source.globalColorTable
    override var blocks: List<GifBlock> = source.blocks

    // --- Computed accessors (re-derived from current state) ---

    private fun currentFile() = Gif(version, screenDescriptor, globalColorTable, blocks)

    override val width: Pixels get() = currentFile().width
    override val height: Pixels get() = currentFile().height
    override val frameCount: Int get() = currentFile().frameCount
    override val isAnimated: Boolean get() = currentFile().isAnimated
    override val imageDescriptors: List<GifImageDescriptor> get() = currentFile().imageDescriptors
    override val graphicControlExtensions: List<GifGraphicControl> get() = currentFile().graphicControlExtensions
    override val applicationExtensions: List<GifApplicationExtension> get() = currentFile().applicationExtensions

    // --- Build ---

    override fun build(): Gif = Gif(
        version = version,
        screenDescriptor = screenDescriptor,
        globalColorTable = globalColorTable,
        blocks = blocks,
    )
}

// ---------------------------------------------------------------------------
// edit() extension
// ---------------------------------------------------------------------------

/**
 * Create a modified copy of this [Gif] by mutating properties
 * inside the [block].
 */
fun Gif.edit(block: MutableGifView.() -> Unit): Gif =
    MutableGifView(this).apply(block).build()
