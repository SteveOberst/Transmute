@file:Suppress("unused")

package dev.transmute.model.structure.audio

import dev.transmute.model.core.MediaStructure
import dev.transmute.model.structure.audio.types.OpusIdentification
import dev.transmute.model.structure.audio.types.OpusRaw
import dev.transmute.model.structure.audio.types.opusIdentification
import dev.transmute.model.structure.audio.types.streamSerialNumbers
import dev.transmute.model.structure.common.OggPageSummary
import dev.transmute.model.structure.common.toSummary
import kotlinx.serialization.Serializable

/**
 * Structured, JSON-safe representation of an Opus file.
 *
 * Ogg page data is excluded; codec parameters are taken from the
 * OpusHead identification header.
 *
 * Note: Opus always operates at 48 kHz internally, regardless of
 * the [dev.transmute.model.structure.audio.types.OpusIdentification.inputSampleRate] value, which records the
 * original source sample rate before encoding.
 */
@Serializable
data class OpusStructure(
    /** Parsed OpusHead identification header. */
    val opusIdentification: OpusIdentification?,
    /** Number of distinct logical Ogg streams in the file. */
    val streamCount: Int,
    /** Total number of Ogg pages in the file. */
    val pageCount: Int,
    /** All Ogg pages in file order (payload bytes excluded). */
    val pages: List<OggPageSummary>,
) : MediaStructure

/**
 * Parse this [dev.transmute.model.structure.audio.types.OpusRaw] into an [OpusStructure].
 */
fun OpusRaw.toStructure(): OpusStructure =
    OpusStructure(
        opusIdentification = opusIdentification,
        streamCount = streamSerialNumbers.size,
        pageCount = pages.size,
        pages = pages.map { it.toSummary() },
    )
