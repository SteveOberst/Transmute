@file:Suppress("unused")

package dev.transmute.model.structure.audio

import dev.transmute.model.core.MediaStructure
import dev.transmute.model.structure.audio.types.OggAudioRaw
import dev.transmute.model.structure.audio.types.VorbisIdentification
import dev.transmute.model.structure.audio.types.streamSerialNumbers
import dev.transmute.model.structure.audio.types.vorbisIdentification
import dev.transmute.model.structure.common.OggPageSummary
import dev.transmute.model.structure.common.toSummary
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
  /** All Ogg pages in file order (payload bytes excluded). */
  val pages: List<OggPageSummary>,
) : MediaStructure

/**
 * Parse this [dev.transmute.model.structure.audio.types.OggAudioRaw] into an [OggAudioStructure].
 */
fun OggAudioRaw.toStructure(): OggAudioStructure = OggAudioStructure(
  vorbisIdentification = vorbisIdentification,
  streamCount = streamSerialNumbers.size,
  pageCount = pages.size,
  pages = pages.map { it.toSummary() },
)
