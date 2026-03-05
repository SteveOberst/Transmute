@file:Suppress("unused")

package dev.transmute.model.metadata.png

import dev.transmute.model.core.LanguageTag
import dev.transmute.model.core.Latin1String
import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.core.Utf8String
import dev.transmute.model.metadata.common.PayloadRef
import kotlinx.serialization.Serializable

/**
 * PNG textual metadata as a loss-minimizing representation of the underlying chunks.
 *
 * Rather than flattening into a single bag of "entries", this preserves the chunk
 * variants and their on-disk flags:
 * - `tEXt` (uncompressed Latin-1)
 * - `zTXt` (deflate-compressed Latin-1)
 * - `iTXt` (international UTF-8, optionally compressed)
 *
 * Chunk bytes are not embedded; compressed payloads can be preserved using [PayloadRef]
 * where the extractor can reference them.
 */
@Serializable
data class PngTextMetadata(
  /** Text chunks in chunk order. */
  val chunks: List<PngTextChunk>,
  /** Reference to the original set of text chunk payloads when available. */
  val original: PayloadRef? = null,
) : MediaMetadata

@Serializable
sealed class PngTextChunk {
  /** `tEXt`: keyword + null + text (Latin-1). */
  @Serializable
  data class Text(
    val keyword: Latin1String,
    val text: Latin1String,
    val payload: PayloadRef? = null,
  ) : PngTextChunk()

  /** `zTXt`: keyword + null + compressionMethod + compressedText (Latin-1 after inflate). */
  @Serializable
  data class ZText(
    val keyword: Latin1String,
    val compressionMethod: UByte,
    /** Compressed payload reference (after the compression-method byte). */
    val compressedText: PayloadRef,
    /** Inflated/decompressed text when decode succeeded. */
    val text: Latin1String? = null,
    /** Set when inflate/decode fails; payload is still preserved. */
    val decodeError: String? = null,
  ) : PngTextChunk()

  /**
   * `iTXt`: keyword + null + compressionFlag + compressionMethod + languageTag + null +
   * translatedKeyword + null + (text or compressedText).
   */
  @Serializable
  data class IText(
    val keyword: Latin1String,
    val compressed: Boolean,
    val compressionMethod: UByte,
    val languageTag: LanguageTag? = null,
    val translatedKeyword: Utf8String? = null,
    /** Decoded UTF-8 text when decode succeeded. */
    val text: Utf8String? = null,
    /** Compressed payload reference when [compressed] is true. */
    val compressedText: PayloadRef? = null,
    val decodeError: String? = null,
  ) : PngTextChunk()
}

