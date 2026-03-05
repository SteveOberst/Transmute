@file:Suppress("unused")

package dev.transmute.structure.audio

import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.metadata.vorbis.VorbisCommentMetadata
import dev.transmute.model.structure.audio.types.OpusRaw

/**
 * Extract metadata from a parsed [OpusRaw].
 *
 * Supports:
 * - **Vorbis Comment** - from the OpusTags header packet
 *
 * In Opus streams, the second header packet in the Ogg container carries
 * the Vorbis Comment metadata. It begins with `"OpusTags"` (8 bytes)
 * followed by the standard Vorbis Comment structure.
 */
fun OpusRaw.extractMetadata(): List<MediaMetadata> = buildList {
  extractVorbisComment()?.let(::add)
}

// -- Vorbis Comment extraction (OpusTags) -------------------------------------

private fun OpusRaw.extractVorbisComment(): VorbisCommentMetadata? {
  // OpusTags magic: the string "OpusTags" (8 bytes)
  val magic = "OpusTags".encodeToByteArray()

  for (page in pages) {
    val d = page.data.data
    if (d.size < magic.size + 8) continue

    // Check for the OpusTags magic
    var match = true
    for (i in magic.indices) {
      if (d[i] != magic[i]) {
        match = false
        break
      }
    }
    if (!match) continue

    // Skip the 8-byte "OpusTags" prefix; the rest is Vorbis Comment data
    return parseVorbisCommentBytes(d, offset = magic.size)
  }

  return null
}
