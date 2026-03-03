@file:Suppress("unused")

package dev.transmute.model.metadata.id3

import dev.transmute.model.core.MediaMetadata
import kotlinx.serialization.Serializable

/**
 * ID3v1 tag metadata (the fixed-size 128-byte tag appended to an MP3 file).
 *
 * On-disk layout:
 * ```
 * | "TAG" (3 B) | title (30 B) | artist (30 B) | album (30 B) |
 * | year (4 B)  | comment (28-30 B) | [track (1 B)] | genre (1 B) |
 * ```
 *
 * If byte 125 is `0x00` and byte 126 is non-zero the file carries
 * ID3v1.1 with a track number; otherwise the full 30 bytes are comment.
 */
@Serializable
data class Id3v1Metadata(
    val title: String,
    val artist: String,
    val album: String,
    val year: String,
    val comment: String,
    /** Track number (ID3v1.1 extension); `null` when not present. */
    val track: Int? = null,
    /** Genre index (0-191 defined by Winamp, 255 = unset). */
    val genre: Int,
    /** Resolved genre name, `null` when the index is out of range. */
    val genreName: String? = null,
) : MediaMetadata
