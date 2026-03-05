@file:Suppress("unused")

package dev.transmute.structure.audio

import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.metadata.riff.RiffInfoMetadata
import dev.transmute.model.structure.audio.types.WavRaw
import dev.transmute.model.structure.audio.types.chunks
import dev.transmute.structure.common.extractRiffInfoList

/**
 * Extract metadata from a parsed [WavRaw].
 *
 * Supports:
 * - **RIFF INFO** - text key/value entries from the `LIST` `INFO` chunk
 *
 * WAV files may contain a `LIST` chunk with form type `INFO` that carries
 * descriptive text entries such as artist, title, creation date, etc.
 */
fun WavRaw.extractMetadata(): List<MediaMetadata> = buildList {
  extractRiffInfo()?.let(::add)
}

// -- RIFF INFO extraction -----------------------------------------------------

private fun WavRaw.extractRiffInfo(): RiffInfoMetadata? {
  val infoList = chunks.firstOrNull {
    it.id.value == "LIST" && it.formType?.value == "INFO"
  } ?: return null

  val list = extractRiffInfoList(infoList) ?: return null
  return RiffInfoMetadata(info = list)
}
