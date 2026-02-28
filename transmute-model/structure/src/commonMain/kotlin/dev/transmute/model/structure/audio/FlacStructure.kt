@file:Suppress("unused")

package dev.transmute.model.structure.audio

import dev.transmute.model.core.MediaStructure
import kotlinx.serialization.Serializable

/**
 * Structured, JSON-safe representation of a FLAC file.
 *
 * Audio frame data is excluded; [audioDataBytes] captures only its size.
 * [streamInfo] contains all relevant codec parameters.
 */
@Serializable
data class FlacStructure(
    /** STREAMINFO metadata block (codec parameters, total samples, MD5). */
    val streamInfo: FlacStreamInfo?,
    /** Number of metadata blocks. */
    val metadataBlockCount: Int,
    /** Total audio frame data size in bytes. */
    val audioDataBytes: Long,
) : MediaStructure

/**
 * Parse this [FlacRaw] into a [FlacStructure].
 */
fun FlacRaw.toStructure(): FlacStructure =
    FlacStructure(
        streamInfo = streamInfo,
        metadataBlockCount = metadataBlocks.size,
        audioDataBytes = audioData.size.toLong(),
    )
