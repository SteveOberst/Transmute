@file:Suppress("unused")

package dev.transmute.structure.common

import dev.transmute.model.core.Latin1String
import dev.transmute.model.metadata.common.PayloadRef
import dev.transmute.model.metadata.riff.RiffInfoEntries
import dev.transmute.model.metadata.riff.RiffInfoEntryRef
import dev.transmute.model.metadata.riff.RiffInfoKnownTag
import dev.transmute.model.metadata.riff.RiffInfoList
import dev.transmute.model.metadata.riff.RiffInfoTextChunk
import dev.transmute.model.structure.common.RiffChunk

// -- Well-known RIFF INFO tag ID -> human-readable name ------------------------

internal val RIFF_INFO_TAG_NAMES: Map<String, String> = mapOf(
  "IARL" to "Archival Location",
  "IART" to "Artist",
  "ICMS" to "Commissioned",
  "ICMT" to "Comment",
  "ICOP" to "Copyright",
  "ICRD" to "Creation Date",
  "ICRP" to "Cropped",
  "IDIM" to "Dimensions",
  "IDPI" to "Dots Per Inch",
  "IENG" to "Engineer",
  "IGNR" to "Genre",
  "IKEY" to "Keywords",
  "ILGT" to "Lightness",
  "IMED" to "Medium",
  "INAM" to "Title",
  "IPLT" to "Palette Setting",
  "IPRD" to "Product",
  "ISBJ" to "Subject",
  "ISFT" to "Software",
  "ISHP" to "Sharpness",
  "ISRC" to "Source",
  "ISRF" to "Source Form",
  "ITCH" to "Technician",
  "ITRK" to "Track Number",
)

// -- Shared RIFF INFO extraction ----------------------------------------------

/**
 * Extract RIFF INFO entries from a `LIST` chunk with form type `INFO`.
 *
 * Preserves:
 * - Order
 * - Unknown tag IDs
 * - Empty values (kept, not dropped)
 */
internal fun extractRiffInfoList(infoList: RiffChunk): RiffInfoList? {
  val order = mutableListOf<RiffInfoEntryRef>()
  val artist = mutableListOf<RiffInfoTextChunk>()
  val title = mutableListOf<RiffInfoTextChunk>()
  val product = mutableListOf<RiffInfoTextChunk>()
  val creationDate = mutableListOf<RiffInfoTextChunk>()
  val comment = mutableListOf<RiffInfoTextChunk>()
  val genre = mutableListOf<RiffInfoTextChunk>()
  val software = mutableListOf<RiffInfoTextChunk>()
  val engineer = mutableListOf<RiffInfoTextChunk>()
  val technician = mutableListOf<RiffInfoTextChunk>()
  val copyright = mutableListOf<RiffInfoTextChunk>()
  val subject = mutableListOf<RiffInfoTextChunk>()
  val source = mutableListOf<RiffInfoTextChunk>()
  val keywords = mutableListOf<RiffInfoTextChunk>()
  val trackNumber = mutableListOf<RiffInfoTextChunk>()
  val extra = mutableListOf<RiffInfoTextChunk>()

  fun decodeLatin1(bytes: ByteArray): Latin1String {
    val s = buildString(bytes.size) {
      for (b in bytes) append((b.toInt() and 0xFF).toChar())
    }
    return Latin1String(s)
  }

  fun addKnown(tag: RiffInfoKnownTag, list: MutableList<RiffInfoTextChunk>, chunk: RiffInfoTextChunk) {
    val idx = list.size.toUInt()
    list.add(chunk)
    order.add(RiffInfoEntryRef.Known(tag, idx))
  }

  for (child in infoList.children) {
    val tag = child.id.value
    val rawText = child.data.data
      .let(::decodeLatin1)
      .value
      .trimEnd('\u0000', ' ')
    val chunk = RiffInfoTextChunk(
      tag = child.id,
      name = RIFF_INFO_TAG_NAMES[tag],
      value = Latin1String(rawText),
      payload = PayloadRef(sizeBytes = child.data.size.toULong()),
    )
    when (tag) {
      "IART" -> addKnown(RiffInfoKnownTag.Artist, artist, chunk)
      "INAM" -> addKnown(RiffInfoKnownTag.Title, title, chunk)
      "IPRD" -> addKnown(RiffInfoKnownTag.Product, product, chunk)
      "ICRD" -> addKnown(RiffInfoKnownTag.CreationDate, creationDate, chunk)
      "ICMT" -> addKnown(RiffInfoKnownTag.Comment, comment, chunk)
      "IGNR" -> addKnown(RiffInfoKnownTag.Genre, genre, chunk)
      "ISFT" -> addKnown(RiffInfoKnownTag.Software, software, chunk)
      "IENG" -> addKnown(RiffInfoKnownTag.Engineer, engineer, chunk)
      "ITCH" -> addKnown(RiffInfoKnownTag.Technician, technician, chunk)
      "ICOP" -> addKnown(RiffInfoKnownTag.Copyright, copyright, chunk)
      "ISBJ" -> addKnown(RiffInfoKnownTag.Subject, subject, chunk)
      "ISRC" -> addKnown(RiffInfoKnownTag.Source, source, chunk)
      "IKEY" -> addKnown(RiffInfoKnownTag.Keywords, keywords, chunk)
      "ITRK" -> addKnown(RiffInfoKnownTag.TrackNumber, trackNumber, chunk)
      else -> {
        val idx = extra.size.toUInt()
        extra.add(chunk)
        order.add(RiffInfoEntryRef.Extra(idx))
      }
    }
  }

  val entries = RiffInfoEntries(
    artist = artist,
    title = title,
    product = product,
    creationDate = creationDate,
    comment = comment,
    genre = genre,
    software = software,
    engineer = engineer,
    technician = technician,
    copyright = copyright,
    subject = subject,
    source = source,
    keywords = keywords,
    trackNumber = trackNumber,
    extra = extra,
    order = order,
  )

  val any = artist.isNotEmpty() || title.isNotEmpty() || product.isNotEmpty() || creationDate.isNotEmpty() ||
    comment.isNotEmpty() || genre.isNotEmpty() || software.isNotEmpty() || engineer.isNotEmpty() || technician.isNotEmpty() ||
    copyright.isNotEmpty() || subject.isNotEmpty() || source.isNotEmpty() || keywords.isNotEmpty() ||
    trackNumber.isNotEmpty() || extra.isNotEmpty()
  return if (any) RiffInfoList(entries = entries) else null
}

