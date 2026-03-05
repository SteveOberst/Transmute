@file:Suppress("unused")

package dev.transmute.model.structure.audio

import dev.transmute.model.core.MediaStructure
import dev.transmute.model.structure.audio.types.AacRaw
import dev.transmute.model.structure.audio.types.AdtsFrameHeader
import dev.transmute.model.structure.audio.types.firstFrameHeader
import kotlinx.serialization.Serializable

/**
 * Structured, JSON-safe representation of a raw AAC (ADTS) file.
 *
 * The audio bitstream is excluded; [dataSizeBytes] captures only its size.
 * Codec parameters are taken from the first ADTS frame header.
 */
@Serializable
data class AacStructure(
  /** Parsed first ADTS frame header (codec parameters). */
  val firstFrame: AdtsFrameHeader?,
  /** Total file size in bytes. */
  val dataSizeBytes: Long,
) : MediaStructure

/**
 * Parse this [dev.transmute.model.structure.audio.types.AacRaw] into an [AacStructure].
 */
fun AacRaw.toStructure(): AacStructure = AacStructure(
  firstFrame = firstFrameHeader,
  dataSizeBytes = data.size.toLong(),
)
