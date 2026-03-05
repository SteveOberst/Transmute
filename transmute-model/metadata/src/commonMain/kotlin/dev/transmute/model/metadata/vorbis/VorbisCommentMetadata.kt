@file:Suppress("unused")

package dev.transmute.model.metadata.vorbis

import dev.transmute.model.core.AsciiString
import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.core.Utf8String
import dev.transmute.model.metadata.common.PayloadRef
import kotlinx.serialization.Serializable

/**
 * Vorbis Comment metadata (used in Ogg Vorbis, FLAC, Opus, etc.).
 *
 * On-disk layout:
 * - Vendor string (encoder identification)
 * - Flat list of `FIELD=value` UTF-8 comment pairs (case-insensitive field names)
 *
 * This model keeps the original ordered list semantics while also providing typed
 * slots for common fields and an [extra] list for everything else.
 */
@Serializable
data class VorbisCommentMetadata(
  val vendor: Utf8String,
  val fields: VorbisFields,
  /** Reference to the original Vorbis Comment packet/block bytes when available. */
  val original: PayloadRef? = null,
) : MediaMetadata

@Deprecated("Use fields", ReplaceWith("fields"))
val VorbisCommentMetadata.comments: List<VorbisComment>
  get() = fields.order.mapNotNull { ref ->
    when (ref) {
      is VorbisFieldRef.Known -> when (ref.field) {
        VorbisKnownField.Title -> fields.title.getOrNull(ref.index.toInt())
        VorbisKnownField.Artist -> fields.artist.getOrNull(ref.index.toInt())
        VorbisKnownField.Album -> fields.album.getOrNull(ref.index.toInt())
        VorbisKnownField.AlbumArtist -> fields.albumArtist.getOrNull(ref.index.toInt())
        VorbisKnownField.Date -> fields.date.getOrNull(ref.index.toInt())
        VorbisKnownField.Genre -> fields.genre.getOrNull(ref.index.toInt())
        VorbisKnownField.Comment -> fields.comment.getOrNull(ref.index.toInt())
        VorbisKnownField.TrackNumber -> fields.trackNumber.getOrNull(ref.index.toInt())
        VorbisKnownField.DiscNumber -> fields.discNumber.getOrNull(ref.index.toInt())
        VorbisKnownField.Encoder -> fields.encoder.getOrNull(ref.index.toInt())
      }
      is VorbisFieldRef.Extra -> fields.extra.getOrNull(ref.index.toInt())
    }
  }

@Serializable
data class VorbisFields(
  // -- Typed slots (common fields) --
  val title: List<VorbisComment> = emptyList(),
  val artist: List<VorbisComment> = emptyList(),
  val album: List<VorbisComment> = emptyList(),
  val albumArtist: List<VorbisComment> = emptyList(),
  val date: List<VorbisComment> = emptyList(),
  val genre: List<VorbisComment> = emptyList(),
  val comment: List<VorbisComment> = emptyList(),
  val trackNumber: List<VorbisComment> = emptyList(),
  val discNumber: List<VorbisComment> = emptyList(),
  val encoder: List<VorbisComment> = emptyList(),

  // -- Remainder --
  val extra: List<VorbisComment> = emptyList(),

  /** Original comment order for round-trip fidelity. */
  val order: List<VorbisFieldRef> = emptyList(),
)

@Serializable
enum class VorbisKnownField {
  Title,
  Artist,
  Album,
  AlbumArtist,
  Date,
  Genre,
  Comment,
  TrackNumber,
  DiscNumber,
  Encoder,
}

@Serializable
sealed class VorbisFieldRef {
  @Serializable
  data class Known(val field: VorbisKnownField, val index: UInt) : VorbisFieldRef()

  @Serializable
  data class Extra(val index: UInt) : VorbisFieldRef()
}

@Serializable
data class VorbisComment(
  /** Field name as stored on disk (ASCII). */
  val field: AsciiString,
  /** UTF-8 value. */
  val value: Utf8String,
)
