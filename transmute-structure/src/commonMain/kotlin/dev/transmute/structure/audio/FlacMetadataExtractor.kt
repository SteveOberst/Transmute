@file:Suppress("unused")

package dev.transmute.structure.audio

import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.core.AsciiString
import dev.transmute.model.core.Utf8String
import dev.transmute.model.metadata.common.PayloadRef
import dev.transmute.model.metadata.vorbis.VorbisComment
import dev.transmute.model.metadata.vorbis.VorbisCommentMetadata
import dev.transmute.model.metadata.vorbis.VorbisFieldRef
import dev.transmute.model.metadata.vorbis.VorbisFields
import dev.transmute.model.metadata.vorbis.VorbisKnownField
import dev.transmute.model.structure.audio.types.FlacRaw
import dev.transmute.model.structure.audio.types.vorbisCommentBlock

/**
 * Extract metadata from a parsed [FlacRaw].
 *
 * Supports:
 * - **Vorbis Comment** - from the `VorbisComment` metadata block
 */
fun FlacRaw.extractMetadata(): List<MediaMetadata> = buildList {
  extractVorbisComment()?.let(::add)
}

// -- Vorbis Comment extraction ------------------------------------------------

/**
 * FLAC Vorbis Comment block data layout (all little-endian):
 * ```
 * | vendor_length (4B) | vendor_string | comment_count (4B) |
 * | len_1 (4B) | "FIELD=value" | len_2 (4B) | "FIELD=value" | ... |
 * ```
 */
private fun FlacRaw.extractVorbisComment(): VorbisCommentMetadata? {
  val block = vorbisCommentBlock ?: return null
  return parseVorbisCommentBytes(block.data.data)
}

// -- Shared Vorbis Comment parser ---------------------------------------------

/**
 * Parse a raw Vorbis Comment byte sequence (used by both FLAC and Ogg).
 *
 * The format is always little-endian:
 * - vendorLength (u32LE) + vendor string (UTF-8)
 * - commentCount (u32LE)
 * - For each comment: length (u32LE) + "FIELD=value" (UTF-8)
 */
internal fun parseVorbisCommentBytes(d: ByteArray, offset: Int = 0): VorbisCommentMetadata? {
  if (d.size - offset < 8) return null
  var pos = offset

  fun u32le(): Int {
    if (pos + 4 > d.size) return 0
    val v = (d[pos].toInt() and 0xFF) or
      ((d[pos + 1].toInt() and 0xFF) shl 8) or
      ((d[pos + 2].toInt() and 0xFF) shl 16) or
      ((d[pos + 3].toInt() and 0xFF) shl 24)
    pos += 4
    return v
  }

  val vendorLen = u32le()
  if (pos + vendorLen > d.size) return null
  val vendor = d.decodeToString(pos, pos + vendorLen)
  pos += vendorLen

  val commentCount = u32le()
  val order = mutableListOf<VorbisFieldRef>()
  val title = mutableListOf<VorbisComment>()
  val artist = mutableListOf<VorbisComment>()
  val album = mutableListOf<VorbisComment>()
  val albumArtist = mutableListOf<VorbisComment>()
  val date = mutableListOf<VorbisComment>()
  val genre = mutableListOf<VorbisComment>()
  val comment = mutableListOf<VorbisComment>()
  val trackNumber = mutableListOf<VorbisComment>()
  val discNumber = mutableListOf<VorbisComment>()
  val encoder = mutableListOf<VorbisComment>()
  val extra = mutableListOf<VorbisComment>()

  for (i in 0 until commentCount) {
    val len = u32le()
    if (pos + len > d.size) break
    val raw = d.decodeToString(pos, pos + len)
    pos += len
    val eq = raw.indexOf('=')
    val fieldRaw = if (eq > 0) raw.substring(0, eq) else raw
    val valueRaw = if (eq > 0) raw.substring(eq + 1) else ""
    val field = runCatching { AsciiString(fieldRaw) }.getOrElse { AsciiString(fieldRaw.filter { it.code in 0x00..0x7F }) }
    val v = Utf8String(valueRaw)
    val commentObj = VorbisComment(field = field, value = v)
    val fieldUpper = fieldRaw.uppercase()
    fun addKnown(k: VorbisKnownField, list: MutableList<VorbisComment>) {
      val idx = list.size.toUInt()
      list.add(commentObj)
      order.add(VorbisFieldRef.Known(k, idx))
    }
    when (fieldUpper) {
      "TITLE" -> addKnown(VorbisKnownField.Title, title)
      "ARTIST" -> addKnown(VorbisKnownField.Artist, artist)
      "ALBUM" -> addKnown(VorbisKnownField.Album, album)
      "ALBUMARTIST", "ALBUM_ARTIST" -> addKnown(VorbisKnownField.AlbumArtist, albumArtist)
      "DATE" -> addKnown(VorbisKnownField.Date, date)
      "GENRE" -> addKnown(VorbisKnownField.Genre, genre)
      "COMMENT" -> addKnown(VorbisKnownField.Comment, comment)
      "TRACKNUMBER" -> addKnown(VorbisKnownField.TrackNumber, trackNumber)
      "DISCNUMBER" -> addKnown(VorbisKnownField.DiscNumber, discNumber)
      "ENCODER" -> addKnown(VorbisKnownField.Encoder, encoder)
      else -> {
        val idx = extra.size.toUInt()
        extra.add(commentObj)
        order.add(VorbisFieldRef.Extra(idx))
      }
    }
  }

  return VorbisCommentMetadata(
    vendor = Utf8String(vendor),
    fields = VorbisFields(
      title = title,
      artist = artist,
      album = album,
      albumArtist = albumArtist,
      date = date,
      genre = genre,
      comment = comment,
      trackNumber = trackNumber,
      discNumber = discNumber,
      encoder = encoder,
      extra = extra,
      order = order,
    ),
    original = PayloadRef(sizeBytes = (d.size - offset).toULong()),
  )
}
