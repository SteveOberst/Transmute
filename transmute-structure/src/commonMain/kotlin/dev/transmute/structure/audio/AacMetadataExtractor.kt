@file:Suppress("unused")

package dev.transmute.structure.audio

import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.structure.audio.types.AacRaw
import dev.transmute.structure.common.parseId3v2FromBytes

/**
 * Extract metadata from a parsed [AacRaw].
 *
 * Raw AAC ADTS files don't have a native metadata container, but many
 * tools (iTunes, ffmpeg, etc.) prepend an ID3v2 tag before the first
 * ADTS sync frame.  This extractor scans for that tag.
 *
 * Supports:
 * - **ID3v2** - parsed from bytes preceding the ADTS stream
 */
fun AacRaw.extractMetadata(): List<MediaMetadata> = buildList {
  extractId3v2()?.let(::add)
}

private fun AacRaw.extractId3v2(): dev.transmute.model.metadata.id3.Id3v2Metadata? {
  val d = data.data
  if (d.size < 10) return null
  // Check for "ID3" header at start of file
  if (d[0].toInt().toChar() != 'I' || d[1].toInt().toChar() != 'D' || d[2].toInt().toChar() != '3') return null
  return parseId3v2FromBytes(d)
}
