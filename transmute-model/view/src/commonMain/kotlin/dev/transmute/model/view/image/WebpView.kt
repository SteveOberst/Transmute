@file:Suppress("unused")

package dev.transmute.model.view.image

import dev.transmute.model.core.Pixels
import dev.transmute.model.structure.common.RiffChunk
import dev.transmute.model.structure.image.*
import dev.transmute.model.view.StructureView

// ---------------------------------------------------------------------------
// WebpView — read-only view contract for WebP files
// ---------------------------------------------------------------------------

/**
 * Read-only view over a [Webp].
 *
 * | Tier | Class | Use case |
 * |------|-------|----------|
 * | **Immutable** | via [Webp.view] | Read-only inspection |
 * | **Mutable** | [MutableWebpView] | In-memory rebuild |
 */
interface WebpView : StructureView<Webp> {

    /** The top-level RIFF container chunk. */
    val riff: RiffChunk

    /** Sub-chunks inside the RIFF container. */
    val chunks: List<RiffChunk>

    /** Inferred encoding format. */
    val format: WebpFormat

    /** `true` when alpha is present. */
    val hasAlpha: Boolean

    /** `true` when animation is present. */
    val hasAnimation: Boolean

    /** Image width. */
    val width: Pixels?

    /** Image height. */
    val height: Pixels?
}

// ---------------------------------------------------------------------------
// ImmutableWebpView
// ---------------------------------------------------------------------------

private class ImmutableWebpView(private val file: Webp) : WebpView {
    override val riff get() = file.riff
    override val chunks get() = file.chunks
    override val format get() = file.format
    override val hasAlpha get() = file.hasAlpha
    override val hasAnimation get() = file.hasAnimation
    override val width get() = file.width
    override val height get() = file.height
}

/**
 * Obtain a read-only [WebpView] over this file.
 */
fun Webp.view(): WebpView = ImmutableWebpView(this)
