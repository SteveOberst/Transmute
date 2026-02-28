@file:Suppress("unused")

package dev.transmute.model.structure.audio

import dev.transmute.model.core.MediaStructure
import kotlinx.serialization.Serializable

/**
 * Structured, JSON-safe representation of an MP3 file.
 *
 * Frame audio data is excluded; only the first-frame header metadata
 * and optional ID3 tag data are captured.
 */
@Serializable
data class Mp3Structure(
    /** Parsed first MPEG audio frame header (from the audio data stream). */
    val firstFrame: Mp3FrameHeader?,
    /** ID3v1 tag if present (last 128 bytes of the file). */
    val id3v1Tag: Mp3Id3v1Tag?,
    /** Size of the ID3v2 tag in bytes, if present. */
    val id3v2TagBytes: Long?,
    /** Total size of the MPEG audio data in bytes. */
    val audioDataBytes: Long,
) : MediaStructure

/**
 * Parse this [Mp3Raw] into an [Mp3Structure].
 */
fun Mp3Raw.toStructure(): Mp3Structure =
    Mp3Structure(
        firstFrame = firstFrameHeader,
        id3v1Tag = id3v1Tag,
        id3v2TagBytes = id3v2Tag?.size?.toLong(),
        audioDataBytes = audioData.size.toLong(),
    )
