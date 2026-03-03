@file:Suppress("unused")

package dev.transmute.model.structure.audio

import dev.transmute.model.core.MediaStructure
import dev.transmute.model.structure.audio.types.Mp3FrameHeader
import dev.transmute.model.structure.audio.types.Mp3Id3v1Tag
import dev.transmute.model.structure.audio.types.Mp3Raw
import dev.transmute.model.structure.audio.types.Mp3VbrInfo
import dev.transmute.model.structure.audio.types.firstFrameHeader
import dev.transmute.model.structure.audio.types.id3v1Tag
import dev.transmute.model.structure.audio.types.vbrInfo
import kotlinx.serialization.Serializable

/**
 * Structured representation of an MP3 file.
 *
 * ```
 * [ID3v2 tag]                    <- optional v2 tag prepended at file start
 * MPEG audio frames              <- frame 1 may carry Xing / VBRI VBR header
 *   [Xing / Info / VBRI header]  <- VBR info block (optional, first frame)
 * [ID3v1 tag (128 B)]            <- optional v1 tag appended at file end
 * ```
 *
 * Audio frame data is excluded; [audioDataBytes] records its total size.
 */
@Serializable
data class Mp3Structure(
    /** Parsed first MPEG audio frame header (version, layer, bitrate, sample rate, channel mode). */
    val firstFrame: Mp3FrameHeader?,
    /** VBR info from Xing / VBRI header in the first frame, if present. */
    val vbrInfo: Mp3VbrInfo?,
    /** ID3v2 tag size in bytes (including the header), if present at the start of the file. */
    val id3v2TagBytes: Long?,
    /** ID3v1 tag, if present (last 128 bytes of the file). */
    val id3v1Tag: Mp3Id3v1Tag?,
    /** Total size of the MPEG audio frame data in bytes. */
    val audioDataBytes: Long,
) : MediaStructure

/**
 * Parse this [dev.transmute.model.structure.audio.types.Mp3Raw] into an [Mp3Structure].
 */
fun Mp3Raw.toStructure(): Mp3Structure =
    Mp3Structure(
        firstFrame = firstFrameHeader,
        vbrInfo = vbrInfo,
        id3v2TagBytes = id3v2Tag?.size?.toLong(),
        id3v1Tag = id3v1Tag,
        audioDataBytes = audioData.size.toLong(),
    )
