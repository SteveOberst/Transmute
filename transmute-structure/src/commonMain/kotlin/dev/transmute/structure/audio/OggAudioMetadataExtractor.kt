@file:Suppress("unused")

package dev.transmute.structure.audio

import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.metadata.vorbis.VorbisCommentMetadata
import dev.transmute.model.structure.audio.types.OggAudioRaw

/**
 * Extract metadata from a parsed [OggAudioRaw].
 *
 * Supports:
 * - **Vorbis Comment** - from the Vorbis comment header packet (type 3)
 *
 * In Vorbis streams, the second header packet (packet type 0x03) carries
 * the Vorbis Comment metadata. It begins with `\x03vorbis` (7 bytes)
 * followed by the standard Vorbis Comment structure.
 */
fun OggAudioRaw.extractMetadata(): List<MediaMetadata> = buildList {
  extractVorbisComment()?.let(::add)
}

// -- Vorbis Comment extraction ---

private fun OggAudioRaw.extractVorbisComment(): VorbisCommentMetadata? {
  // The comment header is the second Vorbis header packet.
  // It often appears in the second page (immediately after the BOS page)
  // but may span multiple pages in rare cases.
  //
  // Strategy: scan pages for one that starts with packet type 3 + "vorbis".
  val commentHeader = byteArrayOf(0x03, 0x76, 0x6F, 0x72, 0x62, 0x69, 0x73) // \x03vorbis

  for (page in pages) {
    val d = page.data.data
    if (d.size < commentHeader.size + 8) continue

    // Check for the comment header magic
    var match = true
    for (i in commentHeader.indices) {
      if (d[i] != commentHeader[i]) {
        match = false
        break
      }
    }
    if (!match) continue

    // Skip the 7-byte "\x03vorbis" prefix; the rest is Vorbis Comment data
    return parseVorbisCommentBytes(d, offset = commentHeader.size)
  }

  return null
}
