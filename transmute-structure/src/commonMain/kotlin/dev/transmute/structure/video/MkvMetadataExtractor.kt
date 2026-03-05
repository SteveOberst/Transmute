@file:Suppress("unused")

package dev.transmute.structure.video

import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.structure.video.types.MkvRaw
import dev.transmute.structure.common.extractMatroskaTags

/**
 * Extract metadata from a parsed [MkvRaw].
 *
 * Supports:
 * - **Matroska Tags** - TITLE, ARTIST, ALBUM, DATE_RELEASED, GENRE, etc.
 */
fun MkvRaw.extractMetadata(): List<MediaMetadata> = buildList {
  extractMatroskaTags(elements)?.let(::add)
}
