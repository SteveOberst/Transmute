@file:Suppress("unused")

package dev.transmute.model.metadata.riff

import dev.transmute.model.core.Latin1String
import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.identify.RiffChunkId
import dev.transmute.model.metadata.common.PayloadRef
import kotlinx.serialization.Serializable

/**
 * RIFF LIST INFO metadata.
 *
 * On disk:
 * `LIST` (chunk id) + size + formType `INFO` + child chunks:
 * `| id (4) | size (u32LE) | data (padded to even) |`
 *
 * This model keeps the list structure, exposes typed slots for common INFO tags,
 * and preserves the remainder and ordering for write compatibility.
 */
@Serializable
data class RiffInfoMetadata(
  val info: RiffInfoList,
  /** Reference to the original LIST/INFO payload when available. */
  val original: PayloadRef? = null,
) : MediaMetadata

@Deprecated("Use info.entries", ReplaceWith("info.entries"))
val RiffInfoMetadata.entries: List<RiffInfoTextChunk>
  get() = info.entries.order.mapNotNull { ref ->
    when (ref) {
      is RiffInfoEntryRef.Known -> when (ref.tag) {
        RiffInfoKnownTag.Artist -> info.entries.artist.getOrNull(ref.index.toInt())
        RiffInfoKnownTag.Title -> info.entries.title.getOrNull(ref.index.toInt())
        RiffInfoKnownTag.Product -> info.entries.product.getOrNull(ref.index.toInt())
        RiffInfoKnownTag.CreationDate -> info.entries.creationDate.getOrNull(ref.index.toInt())
        RiffInfoKnownTag.Comment -> info.entries.comment.getOrNull(ref.index.toInt())
        RiffInfoKnownTag.Genre -> info.entries.genre.getOrNull(ref.index.toInt())
        RiffInfoKnownTag.Software -> info.entries.software.getOrNull(ref.index.toInt())
        RiffInfoKnownTag.Engineer -> info.entries.engineer.getOrNull(ref.index.toInt())
        RiffInfoKnownTag.Technician -> info.entries.technician.getOrNull(ref.index.toInt())
        RiffInfoKnownTag.Copyright -> info.entries.copyright.getOrNull(ref.index.toInt())
        RiffInfoKnownTag.Subject -> info.entries.subject.getOrNull(ref.index.toInt())
        RiffInfoKnownTag.Source -> info.entries.source.getOrNull(ref.index.toInt())
        RiffInfoKnownTag.Keywords -> info.entries.keywords.getOrNull(ref.index.toInt())
        RiffInfoKnownTag.TrackNumber -> info.entries.trackNumber.getOrNull(ref.index.toInt())
      }
      is RiffInfoEntryRef.Extra -> info.entries.extra.getOrNull(ref.index.toInt())
    }
  }

@Serializable
data class RiffInfoList(
  val entries: RiffInfoEntries,
)

@Serializable
data class RiffInfoEntries(
  // -- Typed slots (common tags) --
  val artist: List<RiffInfoTextChunk> = emptyList(), // IART
  val title: List<RiffInfoTextChunk> = emptyList(), // INAM
  val product: List<RiffInfoTextChunk> = emptyList(), // IPRD
  val creationDate: List<RiffInfoTextChunk> = emptyList(), // ICRD
  val comment: List<RiffInfoTextChunk> = emptyList(), // ICMT
  val genre: List<RiffInfoTextChunk> = emptyList(), // IGNR
  val software: List<RiffInfoTextChunk> = emptyList(), // ISFT
  val engineer: List<RiffInfoTextChunk> = emptyList(), // IENG
  val technician: List<RiffInfoTextChunk> = emptyList(), // ITCH
  val copyright: List<RiffInfoTextChunk> = emptyList(), // ICOP
  val subject: List<RiffInfoTextChunk> = emptyList(), // ISBJ
  val source: List<RiffInfoTextChunk> = emptyList(), // ISRC
  val keywords: List<RiffInfoTextChunk> = emptyList(), // IKEY
  val trackNumber: List<RiffInfoTextChunk> = emptyList(), // ITRK

  // -- Remainder --
  val extra: List<RiffInfoTextChunk> = emptyList(),

  /** Original child order for round-trip fidelity. */
  val order: List<RiffInfoEntryRef> = emptyList(),
)

@Serializable
enum class RiffInfoKnownTag {
  Artist,
  Title,
  Product,
  CreationDate,
  Comment,
  Genre,
  Software,
  Engineer,
  Technician,
  Copyright,
  Subject,
  Source,
  Keywords,
  TrackNumber,
}

@Serializable
sealed class RiffInfoEntryRef {
  @Serializable
  data class Known(val tag: RiffInfoKnownTag, val index: UInt) : RiffInfoEntryRef()

  @Serializable
  data class Extra(val index: UInt) : RiffInfoEntryRef()
}

@Serializable
data class RiffInfoTextChunk(
  /** 4-byte INFO tag id (e.g. IART, INAM). */
  val tag: RiffChunkId,
  /** Resolved human-readable name, `null` when the id is unknown. */
  val name: String? = null,
  /** Decoded text value (INFO strings are byte strings, often Latin-1). */
  val value: Latin1String,
  /** Reference to the original chunk payload bytes when available (excluding RIFF chunk header). */
  val payload: PayloadRef? = null,
)
