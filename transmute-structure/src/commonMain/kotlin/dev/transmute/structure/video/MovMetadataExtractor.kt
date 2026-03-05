@file:Suppress("unused")

package dev.transmute.structure.video

import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.structure.video.types.MovRaw
import dev.transmute.structure.common.extractItunesMetadata

/**
 * Extract metadata from a parsed [MovRaw].
 *
 * Supports:
 * - **iTunes metadata** - from `moov > udta > meta > ilst`
 *
 * MOV (QuickTime) files use the same ISO BMFF container as MP4 and
 * may contain iTunes-style metadata in the item list (`ilst`) box.
 */
fun MovRaw.extractMetadata(): List<MediaMetadata> = buildList {
  extractItunesMetadata(boxes)?.let(::add)
}
