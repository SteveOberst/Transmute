@file:Suppress("unused")

package dev.transmute.model.view.audio

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.Channels
import dev.transmute.model.core.Hertz
import dev.transmute.model.structure.audio.*
import dev.transmute.model.view.StructureView

// ---------------------------------------------------------------------------
// AacView — read-only view contract for AAC (ADTS) files
// ---------------------------------------------------------------------------

/**
 * Read-only view over an [Aac].
 *
 * | Tier | Class | Use case |
 * |------|-------|----------|
 * | **Immutable** | via [Aac.view] | Read-only inspection |
 * | **Mutable** | [MutableAacView] | In-memory rebuild |
 */
interface AacView : StructureView<Aac> {

    /** Raw ADTS stream data. */
    val data: Bytes

    // --- Computed accessors ---

    /** Parsed first ADTS frame header. */
    val firstFrameHeader: AdtsFrameHeader?

    /** Sample rate from the first frame header. */
    val sampleRate: Hertz?

    /** Channel count from the first frame header. */
    val channels: Channels?

    /** AAC profile from the first frame header. */
    val profile: AacProfile?
}

// ---------------------------------------------------------------------------
// ImmutableAacView
// ---------------------------------------------------------------------------

private class ImmutableAacView(private val file: Aac) : AacView {
    override val data get() = file.data
    override val firstFrameHeader get() = file.firstFrameHeader
    override val sampleRate get() = file.sampleRate
    override val channels get() = file.channels
    override val profile get() = file.profile
}

/**
 * Obtain a read-only [AacView] over this file.
 */
fun Aac.inspect(): AacView = ImmutableAacView(this)
