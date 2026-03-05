@file:Suppress("unused")

package dev.transmute.model.metadata.id3

import dev.transmute.model.core.AsciiString
import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.core.UriString
import dev.transmute.model.metadata.common.PayloadRef
import kotlinx.serialization.Serializable

/**
 * ID3v2 tag metadata (ID3v2.2 / v2.3 / v2.4).
 *
 * Models the on-disk hierarchy:
 * - Tag header (10 bytes)
 * - Optional extended header
 * - Frames with typed slots for well-known IDs and an [Id3v2Frames.extra] list for the rest
 * - Padding (optional)
 * - Optional footer (v2.4)
 *
 * Frame payload bytes are not embedded; each frame carries a [PayloadRef] so
 * unknown frames can be preserved for round-tripping when the original tag bytes
 * are available.
 */
@Serializable
data class Id3v2Metadata(
  val header: Id3v2Header,
  val extendedHeader: Id3v2ExtendedHeader? = null,

  /**
   * Parsed frames with common IDs elevated into typed slots.
   *
   * Order is preserved via [Id3v2Frames.order] so writers can reproduce
   * the original disk layout. All remaining (unknown/uncommon) frames
   * are in [Id3v2Frames.extra].
   */
  val content: Id3v2Frames = Id3v2Frames(),

  /** Number of padding bytes between the last frame and end-of-tag. */
  val paddingSize: UInt = 0u,
  val footer: Id3v2Footer? = null,
  /** Reference to the original ID3 tag bytes (header+body), when available. */
  val original: PayloadRef? = null,
) : MediaMetadata

/**
 * Deprecated flat frame accessor. Use [Id3v2Metadata.content] (typed slots + extra + order).
 *
 * Returns frames in their original on-disk order by traversing [Id3v2Frames.order].
 */
@Deprecated("Use content (typed slots + extra + order)", ReplaceWith("content"))
val Id3v2Metadata.frames: List<Id3v2Frame>
  get() = content.order.mapNotNull { ref ->
    when (ref) {
      is Id3v2FrameRef.Known -> {
        val list = when (ref.frameId) {
          Id3v2KnownFrameId.Title -> content.title
          Id3v2KnownFrameId.Artist -> content.artist
          Id3v2KnownFrameId.Album -> content.album
          Id3v2KnownFrameId.Year -> content.year
          Id3v2KnownFrameId.Genre -> content.genre
          Id3v2KnownFrameId.Comment -> content.comment
          Id3v2KnownFrameId.TrackNumber -> content.trackNumber
          Id3v2KnownFrameId.DiscNumber -> content.discNumber
          Id3v2KnownFrameId.Composer -> content.composer
          Id3v2KnownFrameId.AlbumArtist -> content.albumArtist
          Id3v2KnownFrameId.Picture -> content.picture
          Id3v2KnownFrameId.Lyrics -> content.lyrics
        }
        list.getOrNull(ref.index.toInt())
      }
      is Id3v2FrameRef.Extra -> content.extra.getOrNull(ref.index.toInt())
    }
  }

@Deprecated("Use header.version", ReplaceWith("header.version"))
val Id3v2Metadata.version: Id3v2Version get() = header.version

@Deprecated("Use header.flags", ReplaceWith("header.flags"))
val Id3v2Metadata.flags: Id3v2HeaderFlags get() = header.flags

@Deprecated("Use header.tagSize", ReplaceWith("header.tagSize"))
val Id3v2Metadata.tagSizeBytes: Long get() = header.tagSize.toLong()

// -- Typed frame slots --------------------------------------------------------

/**
 * "Typed slots + extra list" wrapper for ID3v2 frames.
 *
 * Well-known frame IDs (TIT2, TPE1, TALB, etc.) are elevated into typed slots
 * for convenient access. All remaining frames (unknown or uncommon IDs) are
 * in [extra]. The [order] list preserves the original on-disk sequence.
 *
 * This mirrors the pattern used by [dev.transmute.model.metadata.itunes.ItunesIlst],
 * [dev.transmute.model.metadata.riff.RiffInfoEntries], and
 * [dev.transmute.model.metadata.vorbis.VorbisFields].
 */
@Serializable
data class Id3v2Frames(
  // -- Typed slots (common frame IDs) --
  /** TIT2 / TT2: Title / Song name / Content description. */
  val title: List<Id3v2Frame> = emptyList(),
  /** TPE1 / TP1: Lead artist / Lead performer / Soloist / Performing group. */
  val artist: List<Id3v2Frame> = emptyList(),
  /** TALB / TAL: Album / Movie / Show title. */
  val album: List<Id3v2Frame> = emptyList(),
  /** TDRC (v2.4) / TYER / TYE (v2.3) / TYE (v2.2): Recording year / date. */
  val year: List<Id3v2Frame> = emptyList(),
  /** TCON / TCO: Content type (genre). */
  val genre: List<Id3v2Frame> = emptyList(),
  /** COMM / COM: Comment. */
  val comment: List<Id3v2Frame> = emptyList(),
  /** TRCK / TRK: Track number / Position in set. */
  val trackNumber: List<Id3v2Frame> = emptyList(),
  /** TPOS / TPA: Part of a set (disc number). */
  val discNumber: List<Id3v2Frame> = emptyList(),
  /** TCOM / TCM: Composer. */
  val composer: List<Id3v2Frame> = emptyList(),
  /** TPE2 / TP2: Band / Orchestra / Accompaniment (album artist). */
  val albumArtist: List<Id3v2Frame> = emptyList(),
  /** APIC / PIC: Attached picture (cover art). */
  val picture: List<Id3v2Frame> = emptyList(),
  /** USLT / ULT: Unsynchronised lyrics / text transcription. */
  val lyrics: List<Id3v2Frame> = emptyList(),

  // -- Remainder --
  /** All frames whose IDs are not one of the well-known typed slots. */
  val extra: List<Id3v2Frame> = emptyList(),

  /**
   * Original frame order in the ID3v2 tag.
   *
   * Allows writers to preserve the on-disk order even when frames are
   * elevated into typed slots.
   */
  val order: List<Id3v2FrameRef> = emptyList(),
)

/**
 * Well-known ID3v2 frame IDs that are elevated into typed slots on [Id3v2Frames].
 */
@Serializable
enum class Id3v2KnownFrameId {
  Title,
  Artist,
  Album,
  Year,
  Genre,
  Comment,
  TrackNumber,
  DiscNumber,
  Composer,
  AlbumArtist,
  Picture,
  Lyrics,
}

/**
 * Reference to a frame within [Id3v2Frames], used by [Id3v2Frames.order] to
 * preserve original on-disk ordering.
 */
@Serializable
sealed class Id3v2FrameRef {
  /** Reference to a frame in one of the typed-slot lists on [Id3v2Frames]. */
  @Serializable
  data class Known(val frameId: Id3v2KnownFrameId, val index: UInt) : Id3v2FrameRef()

  /** Reference to a frame in [Id3v2Frames.extra]. */
  @Serializable
  data class Extra(val index: UInt) : Id3v2FrameRef()
}

// -- Header types -------------------------------------------------------------

@Serializable
data class Id3v2Header(
  val version: Id3v2Version,
  val flags: Id3v2HeaderFlags,
  /**
   * Tag size (syncsafe) as stored in the header: number of bytes following the 10-byte header.
   * Excludes the header itself, and (for v2.4) excludes the footer unless [flags.footer] is set.
   */
  val tagSize: UInt,
)

@Serializable
data class Id3v2Version(
  /** Major version (2, 3, or 4). */
  val major: UByte,
  /** Revision within the major version. */
  val revision: UByte,
)

@Serializable
data class Id3v2HeaderFlags(
  val unsynchronisation: Boolean = false,
  val extendedHeader: Boolean = false,
  val experimental: Boolean = false,
  /** v2.4 only. */
  val footer: Boolean = false,
)

@Serializable
data class Id3v2Footer(
  val version: Id3v2Version,
  val flags: Id3v2HeaderFlags,
  val tagSize: UInt,
)

/**
 * Extended header (v2.3/v2.4).
 *
 * The structure differs between v2.3 and v2.4; this model keeps the size and
 * a payload reference for round-tripping, and optionally parsed flag fields when
 * they can be interpreted safely.
 */
@Serializable
data class Id3v2ExtendedHeader(
  val size: UInt,
  val rawFlags: UShort? = null,
  val payload: PayloadRef,
)

// -- Frames -------------------------------------------------------------------

@Serializable
@JvmInline
value class Id3FrameId(val value: String) {
  init {
    require(value.length == 3 || value.length == 4) { "Id3FrameId must be 3 or 4 characters: '$value'" }
    // IDs are ASCII bytes on disk. This is a semantic constraint, not a correctness proof.
    require(value.all { it.code in 0x00..0x7F }) { "Id3FrameId contains non-ASCII characters: '$value'" }
  }

  override fun toString(): String = value
}

@Serializable
data class Id3v2Frame(
  val id: Id3FrameId,
  /** Frame payload size in bytes (excluding the frame header). */
  val dataSize: UInt,
  /**
   * Raw frame flags (v2.3/v2.4). Null for v2.2.
   *
   * Consumers that need flag semantics should interpret these based on [Id3v2Header.version].
   */
  val flags: UShort? = null,
  /** Decoded content (best-effort). */
  val content: Id3v2FrameContent,
  /** Opaque payload reference for round-tripping. */
  val payload: PayloadRef = PayloadRef(sizeBytes = dataSize.toULong()),
)

@Serializable
enum class Id3TextEncoding(val code: UByte) {
  Iso8859_1(0u),
  Utf16(1u),
  Utf16Be(2u),
  Utf8(3u),
  Unknown(255u),
  ;

  companion object {
    fun fromCode(code: UByte): Id3TextEncoding = entries.firstOrNull { it.code == code } ?: Unknown
  }
}

/**
 * Typed frame content matching the ID3v2 frame families.
 *
 * The goal is round-trip safety:
 * - Unknown frame types are preserved as [Binary] with their payload reference in [Id3v2Frame.payload].
 * - Parsed frames keep only decoded fields; writers can re-encode from the decoded representation,
 *   or copy from [Id3v2Frame.payload] when unchanged.
 */
@Serializable
sealed class Id3v2FrameContent {
  /** Text-information frame (T*** except TXXX). */
  @Serializable
  data class Text(val encoding: Id3TextEncoding, val text: String) : Id3v2FrameContent()

  /** User-defined text (TXXX). */
  @Serializable
  data class UserText(val encoding: Id3TextEncoding, val description: String, val text: String) : Id3v2FrameContent()

  /** URL link frame (W*** except WXXX). */
  @Serializable
  data class Url(val url: UriString) : Id3v2FrameContent()

  /** User-defined URL (WXXX). */
  @Serializable
  data class UserUrl(val encoding: Id3TextEncoding, val description: String, val url: UriString) : Id3v2FrameContent()

  /** Comment (COMM) or Unsynchronised lyrics (USLT). */
  @Serializable
  data class Comment(
    val encoding: Id3TextEncoding,
    /** ISO-639-2 language code (3 ASCII bytes). */
    val language: AsciiString,
    val description: String,
    val text: String,
  ) : Id3v2FrameContent()

  /** Attached picture (APIC). Image bytes are preserved via [data]. */
  @Serializable
  data class Picture(
    val encoding: Id3TextEncoding,
    val mimeType: String,
    val pictureType: UByte,
    val description: String,
    val data: PayloadRef,
  ) : Id3v2FrameContent()

  /** Any frame whose payload is not decoded. */
  @Serializable
  data class Binary(val note: String? = null) : Id3v2FrameContent()
}
