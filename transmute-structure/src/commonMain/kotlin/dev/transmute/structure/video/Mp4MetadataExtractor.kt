@file:Suppress("unused")

package dev.transmute.structure.video

import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.structure.video.types.Mp4Raw
import dev.transmute.structure.common.extractItunesMetadata

/**
 * Extract metadata from a parsed [Mp4Raw].
 *
 * Supports:
 * - **iTunes metadata** - from `moov > udta > meta > ilst`
 *
 * MP4 files can contain iTunes-style metadata in the item list (`ilst`) box,
 * including title, artist, album, year, genre, track number, etc.
 */
fun Mp4Raw.extractMetadata(): List<MediaMetadata> = buildList {
    extractItunesMetadata(boxes)?.let(::add)
}
