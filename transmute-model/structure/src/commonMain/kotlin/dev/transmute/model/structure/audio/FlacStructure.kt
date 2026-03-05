@file:Suppress("unused")

package dev.transmute.model.structure.audio

import dev.transmute.model.core.MediaStructure
import dev.transmute.model.structure.audio.types.FlacMetadataBlockType
import dev.transmute.model.structure.audio.types.FlacRaw
import dev.transmute.model.structure.audio.types.FlacStreamInfo
import dev.transmute.model.structure.audio.types.streamInfo
import kotlinx.serialization.Serializable

/**
 * Summary of a single FLAC metadata block (type + size, without payload data).
 */
@Serializable
data class FlacMetadataBlockSummary(
  /** Block type (STREAMINFO, PADDING, APPLICATION, SEEKTABLE, VORBIS_COMMENT, CUESHEET, PICTURE, ...). */
  val type: FlacMetadataBlockType,
  /** `true` when this is the last metadata block before the audio frames. */
  val isLast: Boolean,
  /** Payload size in bytes (does not include the 4-byte block header). */
  val dataSizeBytes: Int,
)

/**
 * Structured representation of a FLAC file, following the native FLAC container layout.
 *
 * ```
 * "fLaC"  <- 4-byte stream marker
 * STREAMINFO       <- codec parameters, total samples, MD5
 * [SEEKTABLE]      <- optional seek points
 * [VORBIS_COMMENT] <- optional Vorbis comment tags (artist, title, ...)
 * [CUESHEET]       <- optional cue-sheet
 * [PICTURE] *      <- optional embedded cover art (one block per image)
 * [PADDING]        <- optional zero padding
 * Audio Frames     <- FLAC audio frames (excluded - size in [audioDataBytes])
 * ```
 *
 * Audio frame data is excluded; [audioDataBytes] records its total size.
 */
@Serializable
data class FlacStructure(
  /** Parsed STREAMINFO block (always the first metadata block). */
  val streamInfo: FlacStreamInfo?,
  /** All metadata blocks in file order (type + size; payload excluded). */
  val metadataBlocks: List<FlacMetadataBlockSummary>,
  /** Total audio frame data size in bytes. */
  val audioDataBytes: Long,
) : MediaStructure

/**
 * Parse this [dev.transmute.model.structure.audio.types.FlacRaw] into a [FlacStructure].
 */
fun FlacRaw.toStructure(): FlacStructure = FlacStructure(
  streamInfo = streamInfo,
  metadataBlocks = metadataBlocks.map {
    FlacMetadataBlockSummary(it.type, it.isLast, it.data.size)
  },
  audioDataBytes = audioData.size.toLong(),
)
