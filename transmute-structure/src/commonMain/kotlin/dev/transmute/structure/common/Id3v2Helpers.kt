@file:Suppress("unused")

package dev.transmute.structure.common

import dev.transmute.model.core.AsciiString
import dev.transmute.model.core.UriString
import dev.transmute.model.metadata.common.ByteSlice
import dev.transmute.model.metadata.common.PayloadRef
import dev.transmute.model.metadata.id3.Id3v2ExtendedHeader
import dev.transmute.model.metadata.id3.Id3v2Footer
import dev.transmute.model.metadata.id3.Id3v2Frame
import dev.transmute.model.metadata.id3.Id3v2FrameContent
import dev.transmute.model.metadata.id3.Id3v2FrameRef
import dev.transmute.model.metadata.id3.Id3v2Frames
import dev.transmute.model.metadata.id3.Id3v2Header
import dev.transmute.model.metadata.id3.Id3v2HeaderFlags
import dev.transmute.model.metadata.id3.Id3v2KnownFrameId
import dev.transmute.model.metadata.id3.Id3v2Metadata
import dev.transmute.model.metadata.id3.Id3v2Version
import dev.transmute.model.metadata.id3.Id3FrameId
import dev.transmute.model.metadata.id3.Id3TextEncoding

// -- Shared ID3v2 parser ------------------------------------------------------

/**
 * Parse an ID3v2 tag from raw bytes.
 *
 * The [data] should start with the "ID3" header (10 bytes) followed by the tag body.
 * Supports ID3v2.2, v2.3 and v2.4.
 *
 * Returns `null` if the data is too small or doesn't start with "ID3".
 */
fun parseId3v2FromBytes(data: ByteArray): Id3v2Metadata? {
  val d = data
  if (d.size < 10) return null
  if (d[0].toInt().toChar() != 'I' || d[1].toInt().toChar() != 'D' || d[2].toInt().toChar() != '3') return null

  val major = (d[3].toInt() and 0xFF).toUByte()
  val revision = (d[4].toInt() and 0xFF).toUByte()
  val flagsByte = d[5].toInt() and 0xFF
  val tagSize = syncsafeInt(d, 6).toUInt()

  val headerFlags = Id3v2HeaderFlags(
    unsynchronisation = (flagsByte and 0x80) != 0,
    extendedHeader = (flagsByte and 0x40) != 0,
    experimental = (flagsByte and 0x20) != 0,
    footer = major.toInt() >= 4 && (flagsByte and 0x10) != 0,
  )

  val header = Id3v2Header(
    version = Id3v2Version(major, revision),
    flags = headerFlags,
    tagSize = tagSize,
  )

  var pos = 10
  val tagBodyEnd = (10u + tagSize).toInt().coerceAtMost(d.size)

  // Optional extended header
  val extendedHeader = if (headerFlags.extendedHeader) {
    val extStart = pos
    if (pos + 4 > tagBodyEnd) {
      // Malformed tag: flag set but no bytes available. Preserve header+frames instead of failing.
      null
    } else {
    val extSize = if (major.toInt() >= 4) syncsafeInt(d, pos).toUInt() else readU32BE(d, pos).toUInt()
    // Best-effort: treat size as "bytes after size field" (v2.3) or "bytes excluding size field" (v2.4).
    val extTotal = (4u + extSize).toInt()
    val extEnd = (extStart + extTotal).coerceAtMost(tagBodyEnd)
    val rawFlags = when {
      major.toInt() == 3 && extStart + 6 <= extEnd -> readU16BE(d, extStart + 4)
      major.toInt() >= 4 && extStart + 6 <= extEnd -> readU16BE(d, extStart + 4)
      else -> null
    }
    pos = extEnd
    Id3v2ExtendedHeader(
      size = (extEnd - extStart).toUInt(),
      rawFlags = rawFlags,
      payload = PayloadRef(
        sizeBytes = (extEnd - extStart).toULong(),
        slice = ByteSlice(offset = extStart.toULong(), length = (extEnd - extStart).toULong()),
      ),
    )
    }
  } else {
    null
  }

  // Footer handling (v2.4): detect "3DI" footer at end of tag body if present.
  val footer = run {
    if (!headerFlags.footer) return@run null
    val footerStart = tagBodyEnd
    if (footerStart + 10 > d.size) return@run null
    if (d[footerStart].toInt().toChar() != '3' || d[footerStart + 1].toInt().toChar() != 'D' || d[footerStart + 2].toInt().toChar() != 'I') return@run null
    val fMajor = (d[footerStart + 3].toInt() and 0xFF).toUByte()
    val fRev = (d[footerStart + 4].toInt() and 0xFF).toUByte()
    val fFlags = d[footerStart + 5].toInt() and 0xFF
    val fSize = syncsafeInt(d, footerStart + 6).toUInt()
    Id3v2Footer(
      version = Id3v2Version(fMajor, fRev),
      flags = Id3v2HeaderFlags(
        unsynchronisation = (fFlags and 0x80) != 0,
        extendedHeader = (fFlags and 0x40) != 0,
        experimental = (fFlags and 0x20) != 0,
        footer = (fFlags and 0x10) != 0,
      ),
      tagSize = fSize,
    )
  }

  val frames = mutableListOf<Id3v2Frame>()

  // ID3v2.2 uses 3-char IDs and 3-byte sizes (no per-frame flags)
  val isV22 = major.toInt() == 2
  val frameHeaderSize = if (isV22) 6 else 10

  // Parse frames until padding or end.
  while (pos + frameHeaderSize <= tagBodyEnd) {
    val idLen = if (isV22) 3 else 4
    val idBytes = d.copyOfRange(pos, pos + idLen)
    val frameIdStr = idBytes.decodeToString()
    if (frameIdStr.isEmpty() || frameIdStr[0] == '\u0000') break // padding

    val frameSize = if (isV22) {
      ((d[pos + 3].toInt() and 0xFF) shl 16) or
        ((d[pos + 4].toInt() and 0xFF) shl 8) or
        (d[pos + 5].toInt() and 0xFF)
    } else if (major.toInt() >= 4) {
      syncsafeInt(d, pos + 4)
    } else {
      readU32BE(d, pos + 4)
    }

    val frameFlags = if (!isV22 && pos + 10 <= tagBodyEnd) readU16BE(d, pos + 8) else null
    val dataStart = pos + frameHeaderSize
    val dataEnd = (dataStart + frameSize).coerceAtMost(tagBodyEnd)

    if (dataEnd <= dataStart) {
      pos = dataEnd
      continue
    }

    val frameData = d.copyOfRange(dataStart, dataEnd)
    val payloadRef = PayloadRef(
      sizeBytes = frameData.size.toULong(),
      slice = ByteSlice(offset = dataStart.toULong(), length = frameData.size.toULong()),
    )
    val content = decodeFrameContent(Id3FrameId(frameIdStr), frameData, dataStart.toULong())

    frames.add(
      Id3v2Frame(
        id = Id3FrameId(frameIdStr),
        dataSize = frameSize.toUInt(),
        flags = frameFlags,
        content = content,
        payload = payloadRef,
      ),
    )

    pos = dataEnd
  }

  // Remaining bytes are padding (best-effort: count trailing zeros)
  val paddingSize = run {
    var pad = 0
    var p = pos
    while (p < tagBodyEnd && d[p] == 0.toByte()) {
      pad++
      p++
    }
    pad.toUInt()
  }

  // -- Partition frames into typed slots + extra + order ----------------------

  val order = mutableListOf<Id3v2FrameRef>()
  val title = mutableListOf<Id3v2Frame>()
  val artist = mutableListOf<Id3v2Frame>()
  val album = mutableListOf<Id3v2Frame>()
  val year = mutableListOf<Id3v2Frame>()
  val genre = mutableListOf<Id3v2Frame>()
  val commentSlot = mutableListOf<Id3v2Frame>()
  val trackNumber = mutableListOf<Id3v2Frame>()
  val discNumber = mutableListOf<Id3v2Frame>()
  val composer = mutableListOf<Id3v2Frame>()
  val albumArtist = mutableListOf<Id3v2Frame>()
  val picture = mutableListOf<Id3v2Frame>()
  val lyrics = mutableListOf<Id3v2Frame>()
  val extra = mutableListOf<Id3v2Frame>()

  fun addKnown(knownId: Id3v2KnownFrameId, list: MutableList<Id3v2Frame>, frame: Id3v2Frame) {
    val idx = list.size.toUInt()
    list.add(frame)
    order.add(Id3v2FrameRef.Known(knownId, idx))
  }

  for (frame in frames) {
    when (frame.id.value) {
      // Title (v2.3/v2.4: TIT2, v2.2: TT2)
      "TIT2", "TT2" -> addKnown(Id3v2KnownFrameId.Title, title, frame)
      // Artist (v2.3/v2.4: TPE1, v2.2: TP1)
      "TPE1", "TP1" -> addKnown(Id3v2KnownFrameId.Artist, artist, frame)
      // Album (v2.3/v2.4: TALB, v2.2: TAL)
      "TALB", "TAL" -> addKnown(Id3v2KnownFrameId.Album, album, frame)
      // Year (v2.4: TDRC, v2.3: TYER, v2.2: TYE)
      "TDRC", "TYER", "TYE" -> addKnown(Id3v2KnownFrameId.Year, year, frame)
      // Genre (v2.3/v2.4: TCON, v2.2: TCO)
      "TCON", "TCO" -> addKnown(Id3v2KnownFrameId.Genre, genre, frame)
      // Comment (v2.3/v2.4: COMM, v2.2: COM)
      "COMM", "COM" -> addKnown(Id3v2KnownFrameId.Comment, commentSlot, frame)
      // Track number (v2.3/v2.4: TRCK, v2.2: TRK)
      "TRCK", "TRK" -> addKnown(Id3v2KnownFrameId.TrackNumber, trackNumber, frame)
      // Disc number (v2.3/v2.4: TPOS, v2.2: TPA)
      "TPOS", "TPA" -> addKnown(Id3v2KnownFrameId.DiscNumber, discNumber, frame)
      // Composer (v2.3/v2.4: TCOM, v2.2: TCM)
      "TCOM", "TCM" -> addKnown(Id3v2KnownFrameId.Composer, composer, frame)
      // Album artist (v2.3/v2.4: TPE2, v2.2: TP2)
      "TPE2", "TP2" -> addKnown(Id3v2KnownFrameId.AlbumArtist, albumArtist, frame)
      // Picture (v2.3/v2.4: APIC, v2.2: PIC)
      "APIC", "PIC" -> addKnown(Id3v2KnownFrameId.Picture, picture, frame)
      // Lyrics (v2.3/v2.4: USLT, v2.2: ULT)
      "USLT", "ULT" -> addKnown(Id3v2KnownFrameId.Lyrics, lyrics, frame)
      else -> {
        val idx = extra.size.toUInt()
        extra.add(frame)
        order.add(Id3v2FrameRef.Extra(idx))
      }
    }
  }

  val content = Id3v2Frames(
    title = title,
    artist = artist,
    album = album,
    year = year,
    genre = genre,
    comment = commentSlot,
    trackNumber = trackNumber,
    discNumber = discNumber,
    composer = composer,
    albumArtist = albumArtist,
    picture = picture,
    lyrics = lyrics,
    extra = extra,
    order = order,
  )

  return Id3v2Metadata(
    header = header,
    extendedHeader = extendedHeader,
    content = content,
    paddingSize = paddingSize,
    footer = footer,
    original = PayloadRef(sizeBytes = d.size.toULong()),
  )
}

/**
 * Compute the total size of an ID3v2 tag (header + data) from the first 10 bytes.
 * Returns `null` if the bytes don't start with "ID3".
 */
fun id3v2TotalSize(header: ByteArray): Int? {
  if (header.size < 10) return null
  if (header[0].toInt().toChar() != 'I' || header[1].toInt().toChar() != 'D' || header[2].toInt().toChar() != '3') return null
  return 10 + syncsafeInt(header, 6)
}

// -- Internal helpers ---------------------------------------------------------

internal fun syncsafeInt(d: ByteArray, offset: Int): Int = ((d[offset].toInt() and 0x7F) shl 21) or
  ((d[offset + 1].toInt() and 0x7F) shl 14) or
  ((d[offset + 2].toInt() and 0x7F) shl 7) or
  (d[offset + 3].toInt() and 0x7F)

private fun readU16BE(d: ByteArray, offset: Int): UShort {
  val b0 = d[offset].toInt() and 0xFF
  val b1 = d[offset + 1].toInt() and 0xFF
  return ((b0 shl 8) or b1).toUShort()
}

private fun readU32BE(d: ByteArray, offset: Int): Int {
  val b0 = d[offset].toInt() and 0xFF
  val b1 = d[offset + 1].toInt() and 0xFF
  val b2 = d[offset + 2].toInt() and 0xFF
  val b3 = d[offset + 3].toInt() and 0xFF
  return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
}

private fun decodeFrameContent(id: Id3FrameId, data: ByteArray, absolutePayloadOffset: ULong): Id3v2FrameContent {
  if (data.isEmpty()) return Id3v2FrameContent.Binary()

  return when {
    // Text frames (T*** except TXXX / TXX)
    id.value.startsWith("T") && id.value != "TXXX" && id.value != "TXX" -> {
      val (enc, text) = decodeId3Text(data)
      Id3v2FrameContent.Text(enc, text)
    }
    // User-defined text (TXXX / TXX)
    id.value == "TXXX" || id.value == "TXX" -> {
      val enc = Id3TextEncoding.fromCode((data[0].toInt() and 0xFF).toUByte())
      val rest = data.copyOfRange(1, data.size)
      val (descBytes, valueBytes) = splitNullTerminated(enc, rest)
      val desc = decodeId3String(enc, descBytes).trimEnd('\u0000')
      val value = decodeId3String(enc, valueBytes).trimEnd('\u0000')
      Id3v2FrameContent.UserText(enc, desc, value)
    }
    // URL frames (W*** except WXXX / WXX)
    id.value.startsWith("W") && id.value != "WXXX" && id.value != "WXX" -> {
      Id3v2FrameContent.Url(UriString(data.decodeToString().trimEnd('\u0000')))
    }
    // User-defined URL (WXXX / WXX)
    id.value == "WXXX" || id.value == "WXX" -> {
      val enc = Id3TextEncoding.fromCode((data[0].toInt() and 0xFF).toUByte())
      val rest = data.copyOfRange(1, data.size)
      val (descBytes, urlBytes) = splitNullTerminated(enc, rest)
      val desc = decodeId3String(enc, descBytes).trimEnd('\u0000')
      val url = UriString(decodeId3String(enc, urlBytes).trimEnd('\u0000'))
      Id3v2FrameContent.UserUrl(enc, desc, url)
    }
    // Comment (COMM / COM) or Unsynchronised Lyrics (USLT / ULT)
    id.value == "COMM" || id.value == "COM" || id.value == "USLT" || id.value == "ULT" -> {
      if (data.size < 4) return Id3v2FrameContent.Binary(note = "too short")
      val enc = Id3TextEncoding.fromCode((data[0].toInt() and 0xFF).toUByte())
      val lang = AsciiString(data.decodeToString(1, 4.coerceAtMost(data.size)))
      val rest = data.copyOfRange(4.coerceAtMost(data.size), data.size)
      val (descBytes, textBytes) = splitNullTerminated(enc, rest)
      val desc = decodeId3String(enc, descBytes).trimEnd('\u0000')
      val text = decodeId3String(enc, textBytes).trimEnd('\u0000')
      Id3v2FrameContent.Comment(enc, lang, desc, text)
    }
    // Attached picture (APIC / PIC)
    id.value == "APIC" || id.value == "PIC" -> {
      if (data.size < 4) return Id3v2FrameContent.Binary(note = "too short")
      val enc = Id3TextEncoding.fromCode((data[0].toInt() and 0xFF).toUByte())
      val mimeEnd = data.indexOf(0, 1)
      if (mimeEnd < 0) return Id3v2FrameContent.Binary(note = "missing mime terminator")
      val mime = data.decodeToString(1, mimeEnd)
      val picType = if (mimeEnd + 1 < data.size) (data[mimeEnd + 1].toInt() and 0xFF).toUByte() else 0u
      val descStart = mimeEnd + 2
      val descEnd = data.indexOf(0, descStart)
      val descBytes = if (descEnd >= descStart) data.copyOfRange(descStart, descEnd) else ByteArray(0)
      val desc = decodeId3String(enc, descBytes).trimEnd('\u0000')
      val picDataStart = if (descEnd >= 0) descEnd + 1 else descStart
      val picLen = (data.size - picDataStart).coerceAtLeast(0)
      val picRef = PayloadRef(
        sizeBytes = picLen.toULong(),
        slice = ByteSlice(offset = absolutePayloadOffset + picDataStart.toULong(), length = picLen.toULong()),
      )
      Id3v2FrameContent.Picture(enc, mime, picType, desc, picRef)
    }
    else -> Id3v2FrameContent.Binary(note = "unknown frame")
  }
}

private fun ByteArray.indexOf(byte: Int, from: Int): Int {
  for (i in from until size) {
    if (this[i].toInt() and 0xFF == byte) return i
  }
  return -1
}

private fun decodeId3Text(data: ByteArray): Pair<Id3TextEncoding, String> {
  val encoding = (data[0].toInt() and 0xFF).toUByte()
  val enc = Id3TextEncoding.fromCode(encoding)
  val text = decodeId3String(enc, data.copyOfRange(1, data.size)).trimEnd('\u0000')
  return enc to text
}

private fun splitNullTerminated(enc: Id3TextEncoding, bytes: ByteArray): Pair<ByteArray, ByteArray> {
  return when (enc) {
    Id3TextEncoding.Utf16, Id3TextEncoding.Utf16Be -> {
      var i = 0
      while (i + 1 < bytes.size) {
        if (bytes[i] == 0.toByte() && bytes[i + 1] == 0.toByte()) {
          val a = bytes.copyOfRange(0, i)
          val b = bytes.copyOfRange(i + 2, bytes.size)
          return a to b
        }
        i += 2
      }
      bytes to ByteArray(0)
    }
    else -> {
      val idx = bytes.indexOf(0, 0)
      if (idx >= 0) bytes.copyOfRange(0, idx) to bytes.copyOfRange(idx + 1, bytes.size) else bytes to ByteArray(0)
    }
  }
}

private fun decodeId3String(enc: Id3TextEncoding, bytes: ByteArray): String {
  return when (enc) {
    Id3TextEncoding.Iso8859_1 -> decodeLatin1(bytes)
    Id3TextEncoding.Utf8 -> bytes.decodeToString()
    Id3TextEncoding.Utf16 -> decodeUtf16(bytes)
    Id3TextEncoding.Utf16Be -> decodeUtf16Be(bytes)
    else -> bytes.decodeToString()
  }
}

private fun decodeLatin1(bytes: ByteArray): String = buildString(bytes.size) {
  for (b in bytes) append((b.toInt() and 0xFF).toChar())
}

private fun decodeUtf16(bytes: ByteArray): String {
  if (bytes.size < 2) return ""
  val b0 = bytes[0].toInt() and 0xFF
  val b1 = bytes[1].toInt() and 0xFF
  return when {
    b0 == 0xFE && b1 == 0xFF -> decodeUtf16Be(bytes.copyOfRange(2, bytes.size))
    b0 == 0xFF && b1 == 0xFE -> decodeUtf16Le(bytes.copyOfRange(2, bytes.size))
    else -> decodeUtf16Be(bytes) // default per spec when no BOM is present
  }
}

private fun decodeUtf16Be(bytes: ByteArray): String {
  val len = bytes.size / 2
  val chars = CharArray(len)
  var pos = 0
  for (i in 0 until len) {
    val hi = bytes[pos].toInt() and 0xFF
    val lo = bytes[pos + 1].toInt() and 0xFF
    chars[i] = ((hi shl 8) or lo).toChar()
    pos += 2
  }
  return chars.concatToString()
}

private fun decodeUtf16Le(bytes: ByteArray): String {
  val len = bytes.size / 2
  val chars = CharArray(len)
  var pos = 0
  for (i in 0 until len) {
    val lo = bytes[pos].toInt() and 0xFF
    val hi = bytes[pos + 1].toInt() and 0xFF
    chars[i] = ((hi shl 8) or lo).toChar()
    pos += 2
  }
  return chars.concatToString()
}
