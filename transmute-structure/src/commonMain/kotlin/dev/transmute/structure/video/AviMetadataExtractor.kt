@file:Suppress("unused")

package dev.transmute.structure.video

import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.metadata.riff.RiffInfoMetadata
import dev.transmute.model.structure.video.types.AviRaw
import dev.transmute.model.structure.video.types.chunks
import dev.transmute.structure.common.extractRiffInfoList

/**
 * Extract metadata from a parsed [AviRaw].
 *
 * Supports:
 * - **RIFF INFO** - text key/value entries from the `LIST` `INFO` chunk
 *
 * AVI files may contain a `LIST` chunk with form type `INFO` that carries
 * descriptive text entries such as artist, title, creation date, etc.
 */
fun AviRaw.extractMetadata(): List<MediaMetadata> = buildList {
  extractRiffInfo()?.let(::add)
}

// -- RIFF INFO extraction -----------------------------------------------------

private fun AviRaw.extractRiffInfo(): RiffInfoMetadata? {
  val infoList = chunks.firstOrNull {
    it.id.value == "LIST" && it.formType?.value == "INFO"
  } ?: return null

  val list = extractRiffInfoList(infoList) ?: return null
  return RiffInfoMetadata(info = list)
}
