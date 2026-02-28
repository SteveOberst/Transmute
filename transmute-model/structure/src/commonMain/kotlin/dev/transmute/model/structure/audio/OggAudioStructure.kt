@file:Suppress("unused")

package dev.transmute.model.structure.audio

import dev.transmute.model.core.MediaStructure
import kotlinx.serialization.Serializable

/**
 * Structured, JSON-safe representation of an Ogg Vorbis file.
 *
 * Ogg page data is excluded; codec parameters are taken from the
 * Vorbis identification header.
 */
@Serializable
data class OggAudioStructure(
    /** Parsed Vorbis identification header (codec parameters). */
    val vorbisIdentification: VorbisIdentification?,
    /** Number of distinct logical Ogg streams in the file. */
    val streamCount: Int,
    /** Total number of Ogg pages in the file. */
    val pageCount: Int,
) : MediaStructure

/**
 * Parse this [OggAudioRaw] into an [OggAudioStructure].
 */
fun OggAudioRaw.toStructure(): OggAudioStructure =
    OggAudioStructure(
        vorbisIdentification = vorbisIdentification,
        streamCount = streamSerialNumbers.size,
        pageCount = pages.size,
    )
