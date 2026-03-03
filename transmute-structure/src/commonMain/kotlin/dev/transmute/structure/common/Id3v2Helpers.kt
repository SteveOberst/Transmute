@file:Suppress("unused")

package dev.transmute.structure.common

import dev.transmute.model.metadata.id3.*

// -- Shared ID3v2 parser ------------------------------------------------------

/**
 * Parse an ID3v2 tag from raw bytes.
 *
 * The [data] should start with the "ID3" header (10 bytes) followed by
 * frames.  This supports ID3v2.2, v2.3 and v2.4.
 *
 * Returns `null` if the data is too small or doesn't start with "ID3".
 */
fun parseId3v2FromBytes(data: ByteArray): Id3v2Metadata? {
    val d = data
    if (d.size < 10) return null
    if (d[0].toInt().toChar() != 'I' || d[1].toInt().toChar() != 'D' || d[2].toInt().toChar() != '3') return null

    val major = d[3].toInt() and 0xFF
    val revision = d[4].toInt() and 0xFF
    val flags = d[5].toInt() and 0xFF
    val tagSize = syncsafeInt(d, 6)

    val headerFlags = Id3v2HeaderFlags(
        unsynchronisation = (flags and 0x80) != 0,
        extendedHeader = (flags and 0x40) != 0,
        experimental = (flags and 0x20) != 0,
        footer = major >= 4 && (flags and 0x10) != 0,
    )

    val frames = mutableListOf<Id3v2Frame>()
    var pos = 10
    val end = (10 + tagSize).coerceAtMost(d.size)

    // ID3v2.2 uses 3-char frame IDs and 3-byte sizes
    val isV22 = major == 2
    val frameHeaderSize = if (isV22) 6 else 10

    while (pos + frameHeaderSize <= end) {
        val idBytes = d.copyOfRange(pos, pos + if (isV22) 3 else 4)
        val frameId = idBytes.decodeToString()
        if (frameId[0] == '\u0000') break // padding

        val frameSize = if (isV22) {
            ((d[pos + 3].toInt() and 0xFF) shl 16) or
            ((d[pos + 4].toInt() and 0xFF) shl 8) or
            (d[pos + 5].toInt() and 0xFF)
        } else if (major >= 4) {
            syncsafeInt(d, pos + 4)
        } else {
            ((d[pos + 4].toInt() and 0xFF) shl 24) or
            ((d[pos + 5].toInt() and 0xFF) shl 16) or
            ((d[pos + 6].toInt() and 0xFF) shl 8) or
            (d[pos + 7].toInt() and 0xFF)
        }

        val dataStart = pos + frameHeaderSize
        val dataEnd = (dataStart + frameSize).coerceAtMost(d.size)

        if (dataEnd <= dataStart) {
            pos = dataEnd
            continue
        }

        val frameData = d.copyOfRange(dataStart, dataEnd)
        val content = decodeFrameContent(frameId, frameData)

        frames.add(Id3v2Frame(
            id = frameId,
            dataSizeBytes = frameSize.toLong(),
            content = content,
        ))

        pos = dataEnd
    }

    return Id3v2Metadata(
        version = Id3v2Version(major, revision),
        flags = headerFlags,
        tagSizeBytes = tagSize.toLong(),
        frames = frames,
    )
}

/**
 * Compute the total size of an ID3v2 tag (header + data) from the first
 * 10 bytes.  Returns `null` if the bytes don't start with "ID3".
 */
fun id3v2TotalSize(header: ByteArray): Int? {
    if (header.size < 10) return null
    if (header[0].toInt().toChar() != 'I' || header[1].toInt().toChar() != 'D' || header[2].toInt().toChar() != '3') return null
    return 10 + syncsafeInt(header, 6)
}

// -- Internal helpers ---------------------------------------------------------

internal fun syncsafeInt(d: ByteArray, offset: Int): Int =
    ((d[offset].toInt() and 0x7F) shl 21) or
    ((d[offset + 1].toInt() and 0x7F) shl 14) or
    ((d[offset + 2].toInt() and 0x7F) shl 7) or
    (d[offset + 3].toInt() and 0x7F)

internal fun decodeFrameContent(id: String, data: ByteArray): Id3v2FrameContent {
    if (data.isEmpty()) return Id3v2FrameContent.Binary(0)

    return when {
        // Text frames (T*** except TXXX / TXX)
        id.startsWith("T") && id != "TXXX" && id != "TXX" -> {
            val (enc, text) = decodeId3Text(data)
            Id3v2FrameContent.Text(enc, text)
        }
        // User-defined text (TXXX / TXX)
        id == "TXXX" || id == "TXX" -> {
            val enc = textEncodingName(data[0].toInt() and 0xFF)
            val rest = data.copyOfRange(1, data.size).decodeToString().trimEnd('\u0000')
            val sep = rest.indexOf('\u0000')
            if (sep >= 0)
                Id3v2FrameContent.UserText(enc, rest.substring(0, sep), rest.substring(sep + 1))
            else
                Id3v2FrameContent.UserText(enc, "", rest)
        }
        // URL frames (W*** except WXXX / WXX)
        id.startsWith("W") && id != "WXXX" && id != "WXX" -> {
            Id3v2FrameContent.Url(data.decodeToString().trimEnd('\u0000'))
        }
        // User-defined URL (WXXX / WXX)
        id == "WXXX" || id == "WXX" -> {
            val enc = textEncodingName(data[0].toInt() and 0xFF)
            val rest = data.copyOfRange(1, data.size).decodeToString().trimEnd('\u0000')
            val sep = rest.indexOf('\u0000')
            if (sep >= 0)
                Id3v2FrameContent.UserUrl(enc, rest.substring(0, sep), rest.substring(sep + 1))
            else
                Id3v2FrameContent.UserUrl(enc, "", rest)
        }
        // Comment (COMM / COM) or Unsynchronised Lyrics (USLT / ULT)
        id == "COMM" || id == "COM" || id == "USLT" || id == "ULT" -> {
            if (data.size < 4) return Id3v2FrameContent.Binary(data.size.toLong())
            val enc = textEncodingName(data[0].toInt() and 0xFF)
            val lang = data.decodeToString(1, 4.coerceAtMost(data.size))
            val rest = data.copyOfRange(4.coerceAtMost(data.size), data.size).decodeToString().trimEnd('\u0000')
            val sep = rest.indexOf('\u0000')
            if (sep >= 0)
                Id3v2FrameContent.Comment(enc, lang, rest.substring(0, sep), rest.substring(sep + 1))
            else
                Id3v2FrameContent.Comment(enc, lang, "", rest)
        }
        // Attached picture (APIC / PIC)
        id == "APIC" || id == "PIC" -> {
            if (data.size < 4) return Id3v2FrameContent.Binary(data.size.toLong())
            val enc = textEncodingName(data[0].toInt() and 0xFF)
            val mimeEnd = data.indexOf(0, 1)
            if (mimeEnd < 0) return Id3v2FrameContent.Binary(data.size.toLong())
            val mime = data.decodeToString(1, mimeEnd)
            val picType = if (mimeEnd + 1 < data.size) data[mimeEnd + 1].toInt() and 0xFF else 0
            val descStart = mimeEnd + 2
            val descEnd = data.indexOf(0, descStart)
            val desc = if (descEnd > descStart) data.decodeToString(descStart, descEnd) else ""
            val picDataStart = if (descEnd >= 0) descEnd + 1 else descStart
            Id3v2FrameContent.Picture(mime, picType, desc, (data.size - picDataStart).toLong())
        }
        else -> Id3v2FrameContent.Binary(data.size.toLong())
    }
}

private fun ByteArray.indexOf(byte: Int, from: Int): Int {
    for (i in from until size) {
        if (this[i].toInt() and 0xFF == byte) return i
    }
    return -1
}

private fun decodeId3Text(data: ByteArray): Pair<String, String> {
    val encoding = data[0].toInt() and 0xFF
    val enc = textEncodingName(encoding)
    val text = data.copyOfRange(1, data.size).decodeToString().trimEnd('\u0000')
    return enc to text
}

private fun textEncodingName(code: Int): String = when (code) {
    0 -> "ISO-8859-1"
    1 -> "UTF-16"
    2 -> "UTF-16BE"
    3 -> "UTF-8"
    else -> "Unknown($code)"
}
