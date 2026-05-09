@file:Suppress("unused")

package dev.transmute.structure.common

import dev.transmute.model.core.Utf8String
import dev.transmute.model.metadata.itunes.ItunesMetadata
import dev.transmute.model.metadata.common.PayloadRef
import dev.transmute.model.metadata.itunes.ItunesDataBox
import dev.transmute.model.metadata.itunes.ItunesIlst
import dev.transmute.model.metadata.itunes.ItunesIlstItemRef
import dev.transmute.model.metadata.itunes.ItunesItem
import dev.transmute.model.metadata.itunes.ItunesKnownKey
import dev.transmute.model.metadata.itunes.ItunesUnknownBox
import dev.transmute.model.metadata.itunes.ItunesValue
import dev.transmute.model.structure.common.IsoBmffBox

// ===
//  ISO BMFF box navigation helpers
// ===

/**
 * Parse the payload of a regular ISO BMFF container box as child boxes.
 *
 * For simple containers (e.g. `moov`, `udta`, `ilst`), the payload
 * immediately contains child boxes.
 */
internal fun IsoBmffBox.subBoxes(): List<IsoBmffBox> = if (data.data.isEmpty()) children else data.data.parseIsoBmffBoxes()

/**
 * Parse the payload of a "full box" (version + flags header) as child boxes.
 *
 * Full boxes (e.g. `meta`, `hdlr`) have a 4-byte header
 * (`version: u8, flags: u24`) before the child box data.
 */
internal fun IsoBmffBox.fullBoxSubBoxes(): List<IsoBmffBox> = if (data.size < 4) {
  emptyList()
} else {
  data.data.parseIsoBmffBoxes(offset = 4)
}

// ===
//  iTunes ilst metadata extraction (M4A / MP4 / MOV)
// ===

/** Well-known types that are full boxes and need 4 bytes skipped. */
private val FULL_BOX_TYPES = setOf("meta", "stbl", "stsd")

/**
 * Navigate through a chain of nested ISO BMFF boxes starting from this list.
 *
 * At each level, the box payload is parsed as child boxes.  For types in
 * [FULL_BOX_TYPES], the leading 4-byte version/flags header is skipped.
 *
 * @return the children of the final box in the path, or empty if any
 *         intermediate box is missing.
 */
internal fun List<IsoBmffBox>.navigatePath(vararg path: String): List<IsoBmffBox> {
  var current = this
  for (type in path) {
    val box = current.firstOrNull { it.type.value == type } ?: return emptyList()
    current = if (type in FULL_BOX_TYPES) box.fullBoxSubBoxes() else box.subBoxes()
  }
  return current
}

/**
 * Extract an [ItunesMetadata] from the `ilst` box found at
 * `moov > udta > meta > ilst`.
 *
 * Returns `null` if the path doesn't exist or contains no parseable items.
 */
internal fun extractItunesMetadata(boxes: List<IsoBmffBox>): ItunesMetadata? {
  val ilstChildren = boxes.navigatePath("moov", "udta", "meta", "ilst")
  if (ilstChildren.isEmpty()) return null

  val items = ilstChildren.mapNotNull { item -> parseItunesItem(item) }
  if (items.isEmpty()) return null

  val order = mutableListOf<ItunesIlstItemRef>()
  val title = mutableListOf<ItunesItem>()
  val artist = mutableListOf<ItunesItem>()
  val album = mutableListOf<ItunesItem>()
  val year = mutableListOf<ItunesItem>()
  val genre = mutableListOf<ItunesItem>()
  val comment = mutableListOf<ItunesItem>()
  val composer = mutableListOf<ItunesItem>()
  val encodingTool = mutableListOf<ItunesItem>()
  val grouping = mutableListOf<ItunesItem>()
  val lyrics = mutableListOf<ItunesItem>()
  val albumArtist = mutableListOf<ItunesItem>()
  val trackNumber = mutableListOf<ItunesItem>()
  val discNumber = mutableListOf<ItunesItem>()
  val tempoBpm = mutableListOf<ItunesItem>()
  val compilation = mutableListOf<ItunesItem>()
  val artwork = mutableListOf<ItunesItem>()
  val description = mutableListOf<ItunesItem>()
  val extra = mutableListOf<ItunesItem>()

  fun addKnown(key: ItunesKnownKey, list: MutableList<ItunesItem>, item: ItunesItem) {
    val idx = list.size.toUInt()
    list.add(item)
    order.add(ItunesIlstItemRef.Known(key, idx))
  }

  fun addExtra(item: ItunesItem) {
    val idx = extra.size.toUInt()
    extra.add(item)
    order.add(ItunesIlstItemRef.Extra(idx))
  }

  for (item in items) {
    when (item.key.value) {
      "\u00A9nam" -> addKnown(ItunesKnownKey.Title, title, item)
      "\u00A9ART" -> addKnown(ItunesKnownKey.Artist, artist, item)
      "\u00A9alb" -> addKnown(ItunesKnownKey.Album, album, item)
      "\u00A9day" -> addKnown(ItunesKnownKey.Year, year, item)
      "\u00A9gen", "gnre" -> addKnown(ItunesKnownKey.Genre, genre, item)
      "\u00A9cmt" -> addKnown(ItunesKnownKey.Comment, comment, item)
      "\u00A9wrt" -> addKnown(ItunesKnownKey.Composer, composer, item)
      "\u00A9too" -> addKnown(ItunesKnownKey.EncodingTool, encodingTool, item)
      "\u00A9grp" -> addKnown(ItunesKnownKey.Grouping, grouping, item)
      "\u00A9lyr" -> addKnown(ItunesKnownKey.Lyrics, lyrics, item)
      "aART" -> addKnown(ItunesKnownKey.AlbumArtist, albumArtist, item)
      "trkn" -> addKnown(ItunesKnownKey.TrackNumber, trackNumber, item)
      "disk" -> addKnown(ItunesKnownKey.DiscNumber, discNumber, item)
      "tmpo" -> addKnown(ItunesKnownKey.TempoBpm, tempoBpm, item)
      "cpil" -> addKnown(ItunesKnownKey.Compilation, compilation, item)
      "covr" -> addKnown(ItunesKnownKey.Artwork, artwork, item)
      "desc", "ldes" -> addKnown(ItunesKnownKey.Description, description, item)
      else -> addExtra(item)
    }
  }

  return ItunesMetadata(
    ilst = ItunesIlst(
      title = title,
      artist = artist,
      album = album,
      year = year,
      genre = genre,
      comment = comment,
      composer = composer,
      encodingTool = encodingTool,
      grouping = grouping,
      lyrics = lyrics,
      albumArtist = albumArtist,
      trackNumber = trackNumber,
      discNumber = discNumber,
      tempoBpm = tempoBpm,
      compilation = compilation,
      artwork = artwork,
      description = description,
      extra = extra,
      order = order,
    ),
  )
}

// -- iTunes ilst item parsing ---

/**
 * Parse a single iTunes metadata item box into an [ItunesItem].
 *
 * Each item in `ilst` has one or more `data` sub-boxes.  The `data` box
 * has a header:
 * ```
 * | version (1 B) | flags (3 B) | locale (4 B) | value ... |
 * ```
 *
 * Well-known data flags:
 * - `0x01` = UTF-8 text
 * - `0x02` = UTF-16 text
 * - `0x00` / `0x15` (`21`) = implicit / unsigned integer
 * - `0x0D` = JPEG data  / `0x0E` = PNG data (artwork)
 */
private fun parseItunesItem(item: IsoBmffBox): ItunesItem? {
  val key = item.type
  val sub = item.subBoxes()
  if (sub.isEmpty()) return null

  val dataBoxes = sub.filter { it.type.value == "data" }.mapNotNull { box ->
    val d = box.data.data
    if (d.size < 8) return@mapNotNull null
    val version = d[0].toUByte()
    val flags = (((d[1].toInt() and 0xFF) shl 16) or ((d[2].toInt() and 0xFF) shl 8) or (d[3].toInt() and 0xFF)).toUInt()
    val locale = readBE32(d, 4).toUInt()
    val payload = d.copyOfRange(8, d.size)
    val payloadRef = PayloadRef(sizeBytes = payload.size.toULong())

    val value = when (flags.toInt()) {
      1 -> ItunesValue.Utf8Text(Utf8String(payload.decodeToString()))
      2 -> ItunesValue.Utf16Text(decodeUtf16Be(payload))
      0, 21 -> decodeItunesIntegerValue(key.value, payload)
      13 -> ItunesValue.Artwork(ItunesValue.ArtworkFormat.Jpeg, payloadRef)
      14 -> ItunesValue.Artwork(ItunesValue.ArtworkFormat.Png, payloadRef)
      else -> ItunesValue.Binary(note = "flags=$flags, ${payload.size} bytes")
    }

    ItunesDataBox(
      version = version,
      dataType = flags,
      locale = locale,
      value = value,
      payload = PayloadRef(sizeBytes = d.size.toULong()),
    )
  }

  val extraBoxes = sub.filterNot { it.type.value == "data" }.map { box ->
    ItunesUnknownBox(
      type = box.type,
      payload = PayloadRef(sizeBytes = box.data.size.toULong()),
    )
  }

  return ItunesItem(
    key = key,
    name = ITUNES_KEY_NAMES[key.value],
    data = dataBoxes,
    extraBoxes = extraBoxes,
    original = PayloadRef(sizeBytes = item.data.size.toULong()),
  )
}

// -- Value decoding helpers ---

/**
 * Decode an integer payload from an iTunes data box.
 *
 * Special keys like `trkn` and `disk` encode track/disc numbers as
 * structured binary, while `tmpo`, `cpil`, etc. are simple integers.
 */
private fun decodeItunesIntegerValue(key: String, payload: ByteArray): ItunesValue {
  // Track/disc: | pad (2) | num (2) | total (2) | pad (2) |
  if ((key == "trkn" || key == "disk") && payload.size >= 6) {
    val num = readBE16(payload, 2).toUShort()
    val total = readBE16(payload, 4).toUShort()
    return ItunesValue.NumberOfTotal(num, total)
  }

  val width = payload.size.coerceIn(1, 8)
  val v = when (width) {
    1 -> payload[0].toLong()
    2 -> readBE16(payload, 0).toLong()
    4 -> readBE32(payload, 0)
    8 -> readBE64(payload, 0)
    else -> 0L
  }
  return ItunesValue.SignedInteger(v, width.toUByte())
}

private fun decodeUtf16Be(payload: ByteArray): String {
  if (payload.isEmpty()) return ""
  val len = payload.size / 2
  val chars = CharArray(len)
  var pos = 0
  for (i in 0 until len) {
    val hi = payload[pos].toInt() and 0xFF
    val lo = payload[pos + 1].toInt() and 0xFF
    chars[i] = ((hi shl 8) or lo).toChar()
    pos += 2
  }
  return chars.concatToString().trimEnd('\u0000')
}

private fun readBE16(d: ByteArray, off: Int): Int = ((d[off].toInt() and 0xFF) shl 8) or (d[off + 1].toInt() and 0xFF)

private fun readBE32(d: ByteArray, off: Int): Long = ((d[off].toLong() and 0xFF) shl 24) or
  ((d[off + 1].toLong() and 0xFF) shl 16) or
  ((d[off + 2].toLong() and 0xFF) shl 8) or
  (d[off + 3].toLong() and 0xFF)

private fun readBE64(d: ByteArray, off: Int): Long = (readBE32(d, off) shl 32) or (readBE32(d, off + 4) and 0xFFFFFFFFL)

// ===
//  HEIF / AVIF metadata helpers
// ===

/**
 * Attempt to find raw EXIF bytes in a HEIF/AVIF box tree.
 *
 * HEIF stores EXIF data as an item referenced through the `iloc` box.
 * We parse `meta > iinf` to find items of type `Exif`, then look up their
 * data location in `iloc`.  The actual bytes are read from the file-level
 * `mdat` box (or inline `idat`).
 *
 * The returned EXIF bytes begin with the TIFF header (after skipping the
 * 4-byte Exif offset prefix that precedes TIFF data in HEIF items).
 */
internal fun findHeifExifBytes(topLevelBoxes: List<IsoBmffBox>): ByteArray? {
  // Locate the meta box (top-level in HEIF/AVIF, or inside moov for MOV)
  val metaBox = topLevelBoxes.firstOrNull { it.type.value == "meta" }
    ?: topLevelBoxes.navigatePath("moov").firstOrNull { it.type.value == "meta" }
    ?: return null

  val metaChildren = metaBox.fullBoxSubBoxes()

  // Parse iinf to find Exif item ID
  val iinfBox = metaChildren.firstOrNull { it.type.value == "iinf" } ?: return null
  val exifItemId = findItemIdByType(iinfBox, "Exif") ?: return null

  // Parse iloc to find the Exif item's byte range
  val ilocBox = metaChildren.firstOrNull { it.type.value == "iloc" } ?: return null
  val (offset, length) = findItemLocation(ilocBox, exifItemId) ?: return null

  // Read Exif bytes from mdat.
  // iloc construction_method=0 stores absolute file offsets; translate to mdat-payload-relative.
  val mdatBox = topLevelBoxes.firstOrNull { it.type.value == "mdat" } ?: return null
  val mdatPayloadStart = computeBoxPayloadStart(topLevelBoxes, "mdat")
  val mdatRelOffset = if (mdatPayloadStart >= 0) offset - mdatPayloadStart else offset

  val exifData = extractExifBytesFromMdat(mdatBox.data.data, mdatRelOffset.toInt(), length.toInt())
    ?: extractExifBytesFromMdat(mdatBox.data.data, offset.toInt(), length.toInt()) // fallback: try raw offset
    ?: return null

  // HEIF Exif items: the first 4 bytes are a BE uint32 giving the offset to the TIFF header
  if (exifData.size < 4) return null
  val tiffOffset = ((exifData[0].toInt() and 0xFF) shl 24) or
    ((exifData[1].toInt() and 0xFF) shl 16) or
    ((exifData[2].toInt() and 0xFF) shl 8) or
    (exifData[3].toInt() and 0xFF)
  val tiffStart = 4 + tiffOffset
  return if (tiffStart < exifData.size) exifData.copyOfRange(tiffStart, exifData.size) else null
}

/**
 * Attempt to find raw XMP bytes in a HEIF/AVIF box tree.
 *
 * XMP data is stored as a `mime` type item with content type
 * `application/rdf+xml` in the `iinf` box, referenced via `iloc`.
 */
internal fun findHeifXmpBytes(topLevelBoxes: List<IsoBmffBox>): ByteArray? {
  val metaBox = topLevelBoxes.firstOrNull { it.type.value == "meta" }
    ?: topLevelBoxes.navigatePath("moov").firstOrNull { it.type.value == "meta" }
    ?: return null

  val metaChildren = metaBox.fullBoxSubBoxes()
  val iinfBox = metaChildren.firstOrNull { it.type.value == "iinf" } ?: return null

  // Look for 'mime' item with content type containing "rdf+xml" or "xmp"
  val xmpItemId = findMimeItemId(iinfBox, "application/rdf+xml")
    ?: findItemIdByType(iinfBox, "mime")
    ?: return null

  val ilocBox = metaChildren.firstOrNull { it.type.value == "iloc" } ?: return null
  val (offset, length) = findItemLocation(ilocBox, xmpItemId) ?: return null

  val mdatBox = topLevelBoxes.firstOrNull { it.type.value == "mdat" } ?: return null
  val mdatPayloadStart = computeBoxPayloadStart(topLevelBoxes, "mdat")
  val mdatRelOffset = if (mdatPayloadStart >= 0) offset - mdatPayloadStart else offset

  return extractExifBytesFromMdat(mdatBox.data.data, mdatRelOffset.toInt(), length.toInt())
    ?: extractExifBytesFromMdat(mdatBox.data.data, offset.toInt(), length.toInt()) // fallback
}

// -- iinf (Item Information) parsing ---

/**
 * Find the item ID from an `iinf` full box for items matching [itemType] (4 chars).
 *
 * `iinf` layout (version >= 2):
 * ```
 * | version (1 B) | flags (3 B) | entry_count (4 B for v0, 4 B for v2) |
 * | infe boxes ... |
 * ```
 *
 * `infe` (Item Info Entry) layout (version 2):
 * ```
 * | version (1 B) | flags (3 B) | item_ID (2 B) | item_protection_index (2 B) |
 * | item_type (4 B) | item_name (null-terminated) |
 * ```
 */
private fun findItemIdByType(iinfBox: IsoBmffBox, itemType: String): Int? {
  val d = iinfBox.data.data
  if (d.size < 6) return null

  val version = d[0].toInt() and 0xFF
  // version(1) + flags(3) = 4 bytes; entry_count is 16-bit for v0, 32-bit for v1+
  val headerSize = if (version == 0) 6 else 8
  val infeBoxes = d.parseIsoBmffBoxes(offset = headerSize)

  for (infe in infeBoxes) {
    if (infe.type.value != "infe") continue
    val id = parseInfeItemId(infe, itemType)
    if (id != null) return id
  }
  return null
}

private fun findMimeItemId(iinfBox: IsoBmffBox, contentType: String): Int? {
  val d = iinfBox.data.data
  if (d.size < 6) return null

  val version = d[0].toInt() and 0xFF
  val headerSize = if (version == 0) 6 else 8
  val infeBoxes = d.parseIsoBmffBoxes(offset = headerSize)

  for (infe in infeBoxes) {
    if (infe.type.value != "infe") continue
    val id = parseInfeMimeItem(infe, contentType)
    if (id != null) return id
  }
  return null
}

/**
 * Parse an `infe` box and return the item ID if the item type matches.
 */
private fun parseInfeItemId(infe: IsoBmffBox, targetType: String): Int? {
  val d = infe.data.data
  if (d.size < 12) return null

  val version = d[0].toInt() and 0xFF
  if (version < 2) return null // only v2+ has item_type

  val itemId = ((d[4].toInt() and 0xFF) shl 8) or (d[5].toInt() and 0xFF)
  // d[6..7] = item_protection_index
  val typeStr = d.decodeToString(8, minOf(12, d.size))

  return if (typeStr == targetType) itemId else null
}

private fun parseInfeMimeItem(infe: IsoBmffBox, targetContentType: String): Int? {
  val d = infe.data.data
  if (d.size < 12) return null

  val version = d[0].toInt() and 0xFF
  if (version < 2) return null

  val itemId = ((d[4].toInt() and 0xFF) shl 8) or (d[5].toInt() and 0xFF)
  val typeStr = d.decodeToString(8, minOf(12, d.size))

  if (typeStr != "mime") return null

  // After the 4-byte type, there's a null-terminated item_name,
  // then a null-terminated content_type string
  var pos = 12
  // Skip item_name
  while (pos < d.size && d[pos] != 0.toByte()) pos++
  pos++ // skip null
  // Read content_type
  val ctStart = pos
  while (pos < d.size && d[pos] != 0.toByte()) pos++
  val ct = d.decodeToString(ctStart, pos)

  return if (ct.contains(targetContentType, ignoreCase = true)) itemId else null
}

// -- iloc (Item Location) parsing ---

/**
 * Parse an `iloc` box to find the offset and length of the given item.
 *
 * `iloc` layout (version 0):
 * ```
 * | version (1 B) | flags (3 B) |
 * | offset_size:4 | length_size:4 | base_offset_size:4 | reserved:4 |
 * | item_count (2 B) |
 * For each item:
 *   | item_ID (2 B) | data_reference_index (2 B) |
 *   | base_offset (variable) | extent_count (2 B) |
 *   For each extent:
 *     | extent_offset (variable) | extent_length (variable) |
 * ```
 */
private fun findItemLocation(ilocBox: IsoBmffBox, itemId: Int): Pair<Long, Long>? {
  val d = ilocBox.data.data
  if (d.size < 8) return null

  val version = d[0].toInt() and 0xFF
  val sizeByte = ((d[4].toInt() and 0xFF) shl 8) or (d[5].toInt() and 0xFF)
  val offsetSize = (sizeByte shr 12) and 0xF
  val lengthSize = (sizeByte shr 8) and 0xF
  val baseOffsetSize = (sizeByte shr 4) and 0xF
  // (sizeByte & 0xF) is reserved / index_size (v1/v2)

  var pos = 6
  val constructionMethod = if (version >= 1) {
    // version 1/2: next 2 bytes have reserved:4 + construction_method:4 already parsed above
    // Actually for v1+ the item_count may be 4 bytes (in v2)
    0 // simplified; construction_method is in the low 4 bits of the size byte for v1
  } else {
    0
  }

  // Item count: 2 bytes for v0/v1, 4 bytes for v2
  val itemCount = if (version < 2) {
    val c = readBE16Iloc(d, pos)
    pos += 2
    c
  } else {
    val c = readBE32Iloc(d, pos)
    pos += 4
    c.toInt()
  }

  for (i in 0 until itemCount) {
    if (pos >= d.size) break

    // Item ID: 2 bytes for v0/v1, 4 bytes for v2
    val id = if (version < 2) {
      val v = readBE16Iloc(d, pos)
      pos += 2
      v
    } else {
      val v = readBE32Iloc(d, pos)
      pos += 4
      v.toInt()
    }

    // v1/v2: construction_method (2 bytes) - skip
    if (version >= 1) pos += 2

    // data_reference_index (2 bytes)
    pos += 2

    // base_offset (variable)
    val baseOffset = readVariableInt(d, pos, baseOffsetSize)
    pos += baseOffsetSize

    // extent_count (2 bytes)
    val extentCount = readBE16Iloc(d, pos)
    pos += 2

    if (id == itemId && extentCount > 0) {
      // Read first extent
      // v1/v2: extent_index (variable) - skip if present
      if (version >= 1) {
        val indexSize = (sizeByte and 0xF) // index_size from the size byte
        pos += indexSize
      }
      val extentOffset = readVariableInt(d, pos, offsetSize)
      pos += offsetSize
      val extentLength = readVariableInt(d, pos, lengthSize)
      return Pair(baseOffset + extentOffset, extentLength)
    }

    // Skip remaining extents
    for (e in 0 until extentCount) {
      if (version >= 1) {
        val indexSize = (sizeByte and 0xF)
        pos += indexSize
      }
      pos += offsetSize + lengthSize
    }
  }

  return null
}

private fun readVariableInt(d: ByteArray, offset: Int, size: Int): Long = when (size) {
  0 -> 0L
  2 -> readBE16Iloc(d, offset).toLong()
  4 -> readBE32Iloc(d, offset)
  8 -> (readBE32Iloc(d, offset) shl 32) or (readBE32Iloc(d, offset + 4) and 0xFFFFFFFFL)
  else -> 0L
}

private fun readBE16Iloc(d: ByteArray, off: Int): Int {
  if (off + 2 > d.size) return 0
  return ((d[off].toInt() and 0xFF) shl 8) or (d[off + 1].toInt() and 0xFF)
}

private fun readBE32Iloc(d: ByteArray, off: Int): Long {
  if (off + 4 > d.size) return 0L
  return ((d[off].toLong() and 0xFF) shl 24) or
    ((d[off + 1].toLong() and 0xFF) shl 16) or
    ((d[off + 2].toLong() and 0xFF) shl 8) or
    (d[off + 3].toLong() and 0xFF)
}

/**
 * Extract bytes from an mdat payload given an mdat-relative [offset] and [length].
 *
 * A [length] of 0 is treated as "to end of mdat", matching the iloc convention
 * where length=0 means the extent continues to the end of the item.
 */
private fun extractExifBytesFromMdat(mdat: ByteArray, offset: Int, length: Int): ByteArray? {
  if (offset < 0) return null
  if (length == 0) {
    // Extent continues to end of payload
    return if (offset < mdat.size) mdat.copyOfRange(offset, mdat.size) else null
  }
  return if (offset + length <= mdat.size) mdat.copyOfRange(offset, offset + length) else null
}

/**
 * Compute the file offset at which the named box's payload begins.
 *
 * Iterates [topLevelBoxes] accumulating on-disk sizes (header + payload)
 * so that absolute [iloc] offsets can be translated to mdat-relative offsets.
 *
 * Returns -1 when [targetType] is not present in [topLevelBoxes].
 */
private fun computeBoxPayloadStart(topLevelBoxes: List<IsoBmffBox>, targetType: String): Long {
  var filePos = 0L
  for (box in topLevelBoxes) {
    val hdrSize = if (box.largeSize != null) 16L else 8L
    if (box.type.value == targetType) return filePos + hdrSize
    filePos += hdrSize + box.data.size.toLong()
  }
  return -1L
}

// ===
//  Well-known iTunes key names
// ===

internal val ITUNES_KEY_NAMES: Map<String, String> = mapOf(
  "\u00A9nam" to "Title",
  "\u00A9ART" to "Artist",
  "\u00A9alb" to "Album",
  "\u00A9day" to "Year",
  "\u00A9gen" to "Genre",
  "\u00A9cmt" to "Comment",
  "\u00A9wrt" to "Composer",
  "\u00A9too" to "Encoding Tool",
  "\u00A9grp" to "Grouping",
  "\u00A9lyr" to "Lyrics",
  "aART" to "Album Artist",
  "trkn" to "Track Number",
  "disk" to "Disc Number",
  "tmpo" to "Tempo",
  "cpil" to "Compilation",
  "covr" to "Artwork",
  "desc" to "Description",
  "ldes" to "Long Description",
  "catg" to "Category",
  "keyw" to "Keywords",
  "purd" to "Purchase Date",
  "stik" to "Media Type",
  "pgap" to "Gapless Playback",
  "pcst" to "Podcast",
  "purl" to "Podcast URL",
  "egid" to "Episode Global ID",
  "gnre" to "Genre",
  "rtng" to "Rating",
  "soal" to "Sort Album",
  "soar" to "Sort Artist",
  "sonm" to "Sort Name",
  "soco" to "Sort Composer",
  "sosn" to "Sort Show",
)
