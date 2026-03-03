@file:Suppress("unused")

package dev.transmute.model.metadata.riff

import dev.transmute.model.core.MediaMetadata
import kotlinx.serialization.Serializable

/**
 * RIFF LIST INFO metadata - key/value text entries found in WAV and AVI files.
 *
 * The INFO list contains sub-chunks whose 4-character IDs are well-known
 * tag names (e.g. `IART` for artist, `INAM` for title).  The chunk data
 * is a null-terminated or length-bounded text string.
 *
 * Common INFO tags:
 * | ID     | Meaning            |
 * |--------|--------------------|
 * | `IART` | Artist             |
 * | `INAM` | Title / Name       |
 * | `IPRD` | Product / Album    |
 * | `ICRD` | Creation date      |
 * | `ICMT` | Comment            |
 * | `IGNR` | Genre              |
 * | `ISFT` | Software           |
 * | `IENG` | Engineer           |
 * | `ITCH` | Technician         |
 * | `ICOP` | Copyright          |
 * | `ISBJ` | Subject            |
 * | `ISRC` | Source              |
 * | `IKEY` | Keywords           |
 */
@Serializable
data class RiffInfoMetadata(
    /** Ordered list of INFO entries as found in the file. */
    val entries: List<RiffInfoEntry>,
) : MediaMetadata

@Serializable
data class RiffInfoEntry(
    /** 4-character INFO tag ID (e.g. `"IART"`, `"INAM"`). */
    val tag: String,
    /** Resolved human-readable name (e.g. `"Artist"`), or `null` if unknown. */
    val name: String? = null,
    /** Text value (may be empty). */
    val value: String,
)
