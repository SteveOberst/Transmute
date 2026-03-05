@file:Suppress("unused")

package dev.transmute.structure.video

import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.structure.video.types.WebmRaw
import dev.transmute.structure.common.extractMatroskaTags

/**
 * Extract metadata from a parsed [WebmRaw].
 *
 * Supports:
 * - **Matroska Tags** - TITLE, ARTIST, ALBUM, DATE_RELEASED, GENRE, etc.
 */
fun WebmRaw.extractMetadata(): List<MediaMetadata> = buildList {
  extractMatroskaTags(elements)?.let(::add)
}
