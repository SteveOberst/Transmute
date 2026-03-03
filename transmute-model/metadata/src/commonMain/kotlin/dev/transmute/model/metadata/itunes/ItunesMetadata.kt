@file:Suppress("unused")

package dev.transmute.model.metadata.itunes

import dev.transmute.model.core.MediaMetadata
import kotlinx.serialization.Serializable

/**
 * iTunes / ISO BMFF metadata from the `ilst` (item list) box.
 *
 * Found in M4A, MP4, and MOV files inside:
 * ```
 * moov > udta > meta > ilst
 * ```
 *
 * Each item in the `ilst` box is identified by a 4-byte type code
 * (e.g. `nam` for title, `ART` for artist).  Values are decoded
 * from the `data` sub-box according to their well-known data type.
 *
 * Common atoms:
 * | Key    | Meaning          |
 * |--------|------------------|
 * | `nam` | Title            |
 * | `ART` | Artist           |
 * | `alb` | Album            |
 * | `day` | Year             |
 * | `gen` | Genre            |
 * | `cmt` | Comment          |
 * | `wrt` | Composer         |
 * | `too` | Encoding tool    |
 * | `aART` | Album Artist     |
 * | `trkn` | Track number     |
 * | `disk` | Disc number      |
 * | `tmpo` | Tempo / BPM      |
 * | `cpil` | Compilation flag |
 * | `covr` | Artwork          |
 * | `desc` | Description      |
 */
@Serializable
data class ItunesMetadata(
    /** Ordered list of metadata items as found in the ilst box. */
    val items: List<ItunesTag>,
) : MediaMetadata

@Serializable
data class ItunesTag(
    /** Raw 4-character box type code (e.g. `"nam"`, `"aART"`). */
    val key: String,
    /** Human-readable name (e.g. `"Title"`, `"Artist"`), or `null` if unknown. */
    val name: String? = null,
    /**
     * Data type from the `data` sub-box flags field.
     *
     * Well-known values per the Apple iTunes spec:
     * - `0` - implicit (binary, context-dependent)
     * - `1` - UTF-8 text
     * - `2` - UTF-16 text
     * - `13` - JPEG image data
     * - `14` - PNG image data
     * - `21` - signed integer (big-endian, 1/2/4/8 bytes)
     *
     * `null` when the data box could not be parsed.
     */
    val dataType: Int? = null,
    /** Decoded value as a string representation. */
    val value: String,
)
