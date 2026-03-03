@file:Suppress("unused")

package dev.transmute.model.metadata.vorbis

import dev.transmute.model.core.MediaMetadata
import kotlinx.serialization.Serializable

/**
 * Vorbis Comment metadata (used in Ogg Vorbis, FLAC, Opus, etc.).
 *
 * On-disk layout:
 * - Vendor string (encoder identification)
 * - Flat list of `FIELD=value` UTF-8 comment pairs
 *
 * Field names are case-insensitive per the spec; they are preserved as-is
 * from the file.
 */
@Serializable
data class VorbisCommentMetadata(
    /** Encoder/vendor identification string. */
    val vendor: String,
    /** Ordered list of comments as found in the file. */
    val comments: List<VorbisComment>,
) : MediaMetadata

@Serializable
data class VorbisComment(
    /** Field name (e.g. `"TITLE"`, `"ARTIST"`, `"TRACKNUMBER"`). */
    val field: String,
    /** UTF-8 value. */
    val value: String,
)
