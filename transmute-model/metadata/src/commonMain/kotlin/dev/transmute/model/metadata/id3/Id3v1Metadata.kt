@file:Suppress("unused")

package dev.transmute.model.metadata.id3

import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.metadata.common.PayloadRef
import kotlinx.serialization.Serializable

/**
 * ID3v1 tag metadata (the fixed-size 128-byte tag appended to an MP3 file).
 *
 * On-disk layout:
 * ```
 * | "TAG" (3 B) | title (30 B) | artist (30 B) | album (30 B) |
 * | year (4 B)  | comment (28-30 B) | [track (1 B)] | genre (1 B) |
 * ```
 */
@Serializable
data class Id3v1Metadata(
  val title: String,
  val artist: String,
  val album: String,
  /** 4-byte year field (not necessarily numeric). */
  val year: String,
  val comment: String,
  /** Track number (ID3v1.1 extension); `null` when not present. */
  val track: UByte? = null,
  /** Genre index (0-191 defined by Winamp, 255 = unset). */
  val genre: UByte,
  /** Resolved genre name, `null` when the index is out of range. */
  val genreName: String? = null,
  /** Reference to the original 128-byte tag payload, when available. */
  val original: PayloadRef? = null,
) : MediaMetadata

