@file:Suppress("unused")

package dev.transmute.structure.audio

import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.structure.audio.types.M4aRaw
import dev.transmute.structure.common.extractItunesMetadata

/**
 * Extract metadata from a parsed [M4aRaw].
 *
 * Supports:
 * - **iTunes metadata** - from `moov > udta > meta > ilst`
 *
 * M4A files are audio-only ISO BMFF containers that often contain
 * iTunes-style metadata (title, artist, album, year, etc.) in the
 * item list (`ilst`) box.
 */
fun M4aRaw.extractMetadata(): List<MediaMetadata> = buildList {
    extractItunesMetadata(boxes)?.let(::add)
}
