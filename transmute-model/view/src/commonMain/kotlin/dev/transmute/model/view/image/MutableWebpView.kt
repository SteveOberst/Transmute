@file:Suppress("unused")

package dev.transmute.model.view.image

import dev.transmute.model.core.Pixels
import dev.transmute.model.structure.common.RiffChunk
import dev.transmute.model.structure.image.*
import dev.transmute.model.view.MutableStructureView

// ---------------------------------------------------------------------------
// MutableWebpView — the mutator for Webp
// ---------------------------------------------------------------------------

/**
 * Mutable view over a [Webp].
 *
 * The [riff] chunk is exposed as a `var`; computed properties
 * ([chunks], [format], [width], [height], etc.) automatically
 * reflect the current RIFF container.
 *
 * ```kotlin
 * val edited = webpFile.edit {
 *     riff = riff.copy(children = riff.children.filter { it.id.value != "EXIF" })
 * }
 * ```
 */
open class MutableWebpView internal constructor(
    protected val source: Webp,
) : WebpView, MutableStructureView<Webp> {

    // --- Mutable field ---

    override var riff: RiffChunk = source.riff

    // --- Computed accessors (re-derived from current state) ---

    private fun currentFile() = Webp(riff)

    override val chunks: List<RiffChunk> get() = currentFile().chunks
    override val format: WebpFormat get() = currentFile().format
    override val hasAlpha: Boolean get() = currentFile().hasAlpha
    override val hasAnimation: Boolean get() = currentFile().hasAnimation
    override val width: Pixels? get() = currentFile().width
    override val height: Pixels? get() = currentFile().height

    // --- Build ---

    override fun build(): Webp = Webp(riff = riff)
}

// ---------------------------------------------------------------------------
// edit() extension
// ---------------------------------------------------------------------------

/**
 * Create a modified copy of this [Webp] by mutating properties
 * inside the [block].
 */
fun Webp.edit(block: MutableWebpView.() -> Unit): Webp =
    MutableWebpView(this).apply(block).build()
