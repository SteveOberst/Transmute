@file:Suppress("unused")

package dev.transmute.model.view.audio

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.Channels
import dev.transmute.model.core.Hertz
import dev.transmute.model.structure.audio.*
import dev.transmute.model.view.MutableStructureView

// ---------------------------------------------------------------------------
// MutableAacView — the mutator for Aac
// ---------------------------------------------------------------------------

/**
 * Mutable view over an [Aac].
 *
 * The [data] field is exposed as a `var`; computed properties
 * automatically reflect the current state.
 *
 * ```kotlin
 * val edited = aacFile.edit {
 *     data = newStreamData
 * }
 * ```
 */
open class MutableAacView internal constructor(
    protected val source: Aac,
) : AacView, MutableStructureView<Aac> {

    // --- Mutable field ---

    override var data: Bytes = source.data

    // --- Computed accessors (re-derived from current state) ---

    private fun currentFile() = Aac(data)

    override val firstFrameHeader: AdtsFrameHeader? get() = currentFile().firstFrameHeader
    override val sampleRate: Hertz? get() = currentFile().sampleRate
    override val channels: Channels? get() = currentFile().channels
    override val profile: AacProfile? get() = currentFile().profile

    // --- Build ---

    override fun build(): Aac = Aac(data = data)
}

// ---------------------------------------------------------------------------
// edit() extension
// ---------------------------------------------------------------------------

/**
 * Create a modified copy of this [Aac] by mutating properties
 * inside the [block].
 */
fun Aac.edit(block: MutableAacView.() -> Unit): Aac =
    MutableAacView(this).apply(block).build()
