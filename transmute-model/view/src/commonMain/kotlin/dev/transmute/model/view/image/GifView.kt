@file:Suppress("unused")

package dev.transmute.model.view.image

import dev.transmute.model.core.Pixels
import dev.transmute.model.structure.image.*
import dev.transmute.model.view.StructureView

// ---------------------------------------------------------------------------
// GifView — read-only view contract for GIF files
// ---------------------------------------------------------------------------

/**
 * Read-only view over a [Gif].
 *
 * | Tier | Class | Use case |
 * |------|-------|----------|
 * | **Immutable** | via [Gif.view] | Read-only inspection |
 * | **Mutable** | [MutableGifView] | In-memory rebuild |
 */
interface GifView : StructureView<Gif> {

    // --- Constructor fields ---

    /** GIF version (87a or 89a). */
    val version: GifVersion

    /** Logical Screen Descriptor. */
    val screenDescriptor: GifLogicalScreenDescriptor

    /** Global Color Table entries; empty when absent. */
    val globalColorTable: List<GifColor>

    /** All blocks (images, extensions) in file order. */
    val blocks: List<GifBlock>

    // --- Computed accessors ---

    /** Canvas width from the Logical Screen Descriptor. */
    val width: Pixels

    /** Canvas height from the Logical Screen Descriptor. */
    val height: Pixels

    /** Number of image blocks (frames) in the file. */
    val frameCount: Int

    /** `true` for animated GIFs. */
    val isAnimated: Boolean

    /** Parsed Image Descriptor blocks in order. */
    val imageDescriptors: List<GifImageDescriptor>

    /** Parsed Graphic Control Extensions in order. */
    val graphicControlExtensions: List<GifGraphicControl>

    /** Parsed Application Extensions in order. */
    val applicationExtensions: List<GifApplicationExtension>
}

// ---------------------------------------------------------------------------
// ImmutableGifView
// ---------------------------------------------------------------------------

private class ImmutableGifView(private val file: Gif) : GifView {
    override val version get() = file.version
    override val screenDescriptor get() = file.screenDescriptor
    override val globalColorTable get() = file.globalColorTable
    override val blocks get() = file.blocks
    override val width get() = file.width
    override val height get() = file.height
    override val frameCount get() = file.frameCount
    override val isAnimated get() = file.isAnimated
    override val imageDescriptors get() = file.imageDescriptors
    override val graphicControlExtensions get() = file.graphicControlExtensions
    override val applicationExtensions get() = file.applicationExtensions
}

/**
 * Obtain a read-only [GifView] over this file.
 */
fun Gif.view(): GifView = ImmutableGifView(this)
