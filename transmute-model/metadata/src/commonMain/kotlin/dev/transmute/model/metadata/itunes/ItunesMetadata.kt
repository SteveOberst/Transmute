@file:Suppress("unused")

package dev.transmute.model.metadata.itunes

import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.core.Utf8String
import dev.transmute.model.identify.FourCC
import dev.transmute.model.metadata.common.PayloadRef
import kotlinx.serialization.Serializable

/**
 * iTunes / ISO BMFF metadata from the `ilst` (item list) box.
 *
 * On disk the hierarchy is:
 * `ilst` (container)
 *   - item box per key (4-byte type, e.g. `(c)nam`, `aART`, `trkn`)
 *       - one or more `data` boxes holding typed values
 *       - (optional) other sub-boxes (e.g. `mean`/`name` for freeform `---`)
 *
 * This model:
 * - Preserves the container > items > data-box structure.
 * - Provides typed slots for common keys plus [ItunesIlst.extra] for everything else.
 * - Preserves ordering via [ItunesIlst.order] for write compatibility.
 */
@Serializable
data class ItunesMetadata(
  val ilst: ItunesIlst,
  /** Reference to the original `ilst` box payload when available. */
  val original: PayloadRef? = null,
) : MediaMetadata

@Deprecated("Use ilst (typed slots + extra + order)", ReplaceWith("ilst"))
val ItunesMetadata.items: List<ItunesItem>
  get() = ilst.order.mapNotNull { ref ->
    when (ref) {
      is ItunesIlstItemRef.Known -> when (ref.key) {
        ItunesKnownKey.Title -> ilst.title.getOrNull(ref.index.toInt())
        ItunesKnownKey.Artist -> ilst.artist.getOrNull(ref.index.toInt())
        ItunesKnownKey.Album -> ilst.album.getOrNull(ref.index.toInt())
        ItunesKnownKey.Year -> ilst.year.getOrNull(ref.index.toInt())
        ItunesKnownKey.Genre -> ilst.genre.getOrNull(ref.index.toInt())
        ItunesKnownKey.Comment -> ilst.comment.getOrNull(ref.index.toInt())
        ItunesKnownKey.Composer -> ilst.composer.getOrNull(ref.index.toInt())
        ItunesKnownKey.EncodingTool -> ilst.encodingTool.getOrNull(ref.index.toInt())
        ItunesKnownKey.Grouping -> ilst.grouping.getOrNull(ref.index.toInt())
        ItunesKnownKey.Lyrics -> ilst.lyrics.getOrNull(ref.index.toInt())
        ItunesKnownKey.AlbumArtist -> ilst.albumArtist.getOrNull(ref.index.toInt())
        ItunesKnownKey.TrackNumber -> ilst.trackNumber.getOrNull(ref.index.toInt())
        ItunesKnownKey.DiscNumber -> ilst.discNumber.getOrNull(ref.index.toInt())
        ItunesKnownKey.TempoBpm -> ilst.tempoBpm.getOrNull(ref.index.toInt())
        ItunesKnownKey.Compilation -> ilst.compilation.getOrNull(ref.index.toInt())
        ItunesKnownKey.Artwork -> ilst.artwork.getOrNull(ref.index.toInt())
        ItunesKnownKey.Description -> ilst.description.getOrNull(ref.index.toInt())
      }
      is ItunesIlstItemRef.Extra -> ilst.extra.getOrNull(ref.index.toInt())
    }
  }

@Serializable
data class ItunesIlst(
  // -- Typed slots (common keys) --
  val title: List<ItunesItem> = emptyList(), // (c)nam
  val artist: List<ItunesItem> = emptyList(), // (c)ART
  val album: List<ItunesItem> = emptyList(), // (c)alb
  val year: List<ItunesItem> = emptyList(), // (c)day
  val genre: List<ItunesItem> = emptyList(), // (c)gen / gnre
  val comment: List<ItunesItem> = emptyList(), // (c)cmt
  val composer: List<ItunesItem> = emptyList(), // (c)wrt
  val encodingTool: List<ItunesItem> = emptyList(), // (c)too
  val grouping: List<ItunesItem> = emptyList(), // (c)grp
  val lyrics: List<ItunesItem> = emptyList(), // (c)lyr
  val albumArtist: List<ItunesItem> = emptyList(), // aART
  val trackNumber: List<ItunesItem> = emptyList(), // trkn
  val discNumber: List<ItunesItem> = emptyList(), // disk
  val tempoBpm: List<ItunesItem> = emptyList(), // tmpo
  val compilation: List<ItunesItem> = emptyList(), // cpil
  val artwork: List<ItunesItem> = emptyList(), // covr
  val description: List<ItunesItem> = emptyList(), // desc / ldes

  // -- Remainder --
  val extra: List<ItunesItem> = emptyList(),

  /**
   * Original item order in the `ilst` container.
   *
   * This allows writers to preserve order even when items are elevated into typed slots.
   */
  val order: List<ItunesIlstItemRef> = emptyList(),
)

@Serializable
enum class ItunesKnownKey {
  Title,
  Artist,
  Album,
  Year,
  Genre,
  Comment,
  Composer,
  EncodingTool,
  Grouping,
  Lyrics,
  AlbumArtist,
  TrackNumber,
  DiscNumber,
  TempoBpm,
  Compilation,
  Artwork,
  Description,
}

@Serializable
sealed class ItunesIlstItemRef {
  @Serializable
  data class Known(val key: ItunesKnownKey, val index: UInt) : ItunesIlstItemRef()

  @Serializable
  data class Extra(val index: UInt) : ItunesIlstItemRef()
}

@Serializable
data class ItunesItem(
  /** The item's 4-byte box type (e.g. `(c)nam`, `trkn`). */
  val key: FourCC,
  /** Human-readable name when the key is well-known. */
  val name: String? = null,
  /** One or more `data` boxes in this item. */
  val data: List<ItunesDataBox> = emptyList(),
  /** Any non-`data` sub-boxes preserved as opaque payloads. */
  val extraBoxes: List<ItunesUnknownBox> = emptyList(),
  /** Reference to the original item box payload when available. */
  val original: PayloadRef? = null,
)

@Serializable
data class ItunesUnknownBox(
  val type: FourCC,
  val payload: PayloadRef,
)

@Serializable
data class ItunesDataBox(
  val version: UByte,
  /**
   * Data type field from the `data` box flags (u24).
   *
   * The meaning is Apple-defined; writers should treat unknown values as opaque.
   */
  val dataType: UInt,
  /** Locale field from the `data` box header (u32). */
  val locale: UInt,
  val value: ItunesValue,
  /** Reference to the original `data` box payload when available. */
  val payload: PayloadRef,
)

@Serializable
sealed class ItunesValue {
  @Serializable
  data class Utf8Text(val text: Utf8String) : ItunesValue()

  @Serializable
  data class Utf16Text(val text: String) : ItunesValue()

  /**
   * A big-endian signed integer from the data box payload.
   *
   * The payload length determines the integer width (1/2/4/8 bytes).
   */
  @Serializable
  data class SignedInteger(val value: Long, val widthBytes: UByte) : ItunesValue()

  /**
   * Structured track/disc number payload:
   * `| pad (u16) | number (u16) | total (u16) | pad (u16) |`
   */
  @Serializable
  data class NumberOfTotal(val number: UShort, val total: UShort) : ItunesValue()

  @Serializable
  enum class ArtworkFormat { Jpeg, Png, Unknown }

  @Serializable
  data class Artwork(val format: ArtworkFormat, val data: PayloadRef) : ItunesValue()

  /** Any value that is not decoded. */
  @Serializable
  data class Binary(val note: String? = null) : ItunesValue()
}
