@file:Suppress("unused")

package dev.transmute.model.metadata.png

import dev.transmute.model.core.MediaMetadata
import kotlinx.serialization.Serializable

/**
 * PNG textual metadata aggregated from `tEXt`, `zTXt`, and `iTXt` chunks.
 *
 * Each entry records the keyword, the text value, and the source chunk type.
 * Compressed text (`zTXt`) payloads are inflated during extraction so the
 * value field contains the decompressed text.  Uncompressed (`tEXt`) and
 * international (`iTXt`) text is stored verbatim.
 *
 * Common keywords defined by the PNG specification:
 * `Title`, `Author`, `Description`, `Copyright`, `Creation Time`,
 * `Software`, `Disclaimer`, `Warning`, `Source`, `Comment`.
 */
@Serializable
data class PngTextMetadata(
    /** All text entries found in the file, in chunk order. */
    val entries: List<PngTextEntry>,
) : MediaMetadata

@Serializable
data class PngTextEntry(
    /** Keyword (1-79 Latin-1 characters). */
    val keyword: String,
    /** Text value (Latin-1 for tEXt, UTF-8 for iTXt). */
    val text: String,
    /** Source chunk type. */
    val chunkType: PngTextChunkType,
    /** Language tag (iTXt only). */
    val language: String? = null,
    /** Translated keyword (iTXt only). */
    val translatedKeyword: String? = null,
)

@Serializable
enum class PngTextChunkType {
    /** Uncompressed Latin-1 text. */
    TEXT,
    /** Deflate-compressed Latin-1 text. */
    ZTXT,
    /** UTF-8 international text (optionally compressed). */
    ITXT,
}
