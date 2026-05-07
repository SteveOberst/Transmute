@file:Suppress("unused")

package dev.transmute.structure.common

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes
import dev.transmute.model.identify.RiffChunkId
import dev.transmute.model.structure.StructureReadException
import dev.transmute.model.structure.common.RiffChunk

/**
 * Parse a complete RIFF container (e.g. WAV, AVI, WebP) with validation.
 *
 * Validates the file size, RIFF magic, and expected form type before
 * parsing sub-chunks.  Shared by all RIFF-based readers.
 *
 * @param data             raw file bytes
 * @param expectedFormType the 4-byte form type code to expect (e.g. "WAVE", "AVI ")
 * @param formatName       human-readable name for error messages (e.g. "WAV", "AVI")
 * @return a [RiffChunk] representing the top-level RIFF container
 * @throws StructureReadException on invalid/mismatched data
 */
fun parseRiffContainer(data: ByteArray, expectedFormType: String, formatName: String): RiffChunk {
  if (data.size < 12) throw StructureReadException("$formatName file too small (${data.size} bytes)")

  val riffId = data.decodeAscii(0, 4)
  if (riffId != "RIFF") throw StructureReadException("Not a RIFF file: got '$riffId'")

  val fileSize = data.readU32LE(4)
  val formType = data.decodeAscii(8, 4)
  if (formType != expectedFormType) {
    throw StructureReadException(
      "Not a $formatName file: form type '$formType'",
    )
  }

  val children = data.parseRiffChildren(
    offset = 12,
    end = minOf(8 + fileSize.toInt(), data.size),
  )

  return RiffChunk(
    id = RiffChunkId("RIFF"),
    size = fileSize,
    formType = RiffChunkId(expectedFormType),
    children = children,
  )
}

/**
 * Parse sub-chunks within a RIFF/LIST container.
 *
 * Shared by WAV, WebP, and AVI readers.
 */
fun ByteArray.parseRiffChildren(offset: Int, end: Int): List<RiffChunk> {
  val chunks = mutableListOf<RiffChunk>()
  var pos = offset

  while (pos + 8 <= end) {
    val id = decodeAscii(pos, 4)
    val size = readU32LE(pos + 4)
    val payloadStart = pos + 8
    val payloadEnd = minOf(payloadStart + size.toInt(), end)

    if (id == "RIFF" || id == "LIST") {
      val ft = if (payloadStart + 4 <= end) decodeAscii(payloadStart, 4) else "    "
      val children = parseRiffChildren(payloadStart + 4, payloadEnd)
      chunks += RiffChunk(
        id = RiffChunkId(id),
        size = size,
        formType = RiffChunkId(ft),
        children = children,
      )
    } else {
      val payload = if (payloadEnd > payloadStart) {
        copyOfRange(payloadStart, payloadEnd)
      } else {
        ByteArray(0)
      }
      chunks += RiffChunk(
        id = RiffChunkId(id),
        size = size,
        data = payload.asBytes(),
      )
    }

    // Advance past payload + optional pad byte
    pos = payloadEnd + (size.toInt() % 2)
  }

  return chunks
}

/**
 * Parse top-level ISO BMFF boxes from this byte array.
 *
 * Shared by HEIF, AVIF, M4A, MP4, MOV readers.
 */
fun ByteArray.parseIsoBmffBoxes(offset: Int = 0, end: Int = size): List<dev.transmute.model.structure.common.IsoBmffBox> {
  val boxes = mutableListOf<dev.transmute.model.structure.common.IsoBmffBox>()
  var pos = offset

  while (pos + 8 <= end) {
    val size32 = readU32BE(pos)
    val type = decodeAscii(pos + 4, 4)

    val (headerSize, boxSize) = when {
      size32 == 1u -> {
        // 64-bit extended size
        if (pos + 16 > end) return boxes
        val large = readU64BE(pos + 8)
        16 to large.toLong()
      }
      size32 == 0u -> {
        // Box extends to end of data
        8 to (end - pos).toLong()
      }
      else -> {
        8 to size32.toLong()
      }
    }

    val payloadStart = pos + headerSize
    val payloadEnd = minOf(pos + boxSize.toInt(), end)
    val payload = if (payloadEnd > payloadStart) {
      copyOfRange(payloadStart, payloadEnd)
    } else {
      ByteArray(0)
    }

    boxes += dev.transmute.model.structure.common.IsoBmffBox(
      type = dev.transmute.model.identify.FourCC(type),
      data = Bytes(payload),
      largeSize = if (size32 == 1u) boxSize.toULong() else null,
    )

    pos = if (boxSize <= 0) end else minOf(pos + boxSize.toInt(), end)
  }

  return boxes
}

/**
 * Parse top-level EBML elements from this byte array.
 *
 * Shared by WebM and MKV readers.
 */
fun ByteArray.parseEbmlElements(offset: Int = 0, end: Int = size): List<dev.transmute.model.structure.common.EbmlElement> {
  val elements = mutableListOf<dev.transmute.model.structure.common.EbmlElement>()
  var pos = offset

  while (pos < end) {
    // Read EBML element ID (VINT with class bits)
    val (id, idLen) = readEbmlId(this, pos, end) ?: break
    pos += idLen

    // Read EBML size (VINT)
    val (size, sizeLen) = readEbmlVint(this, pos, end) ?: break
    pos += sizeLen

    val payloadEnd = if (size < 0 || pos + size.toInt() > end) end else pos + size.toInt()
    val payload = copyOfRange(pos, payloadEnd)

    // For known master elements, recurse into children
    val isMaster = isEbmlMasterElement(id)
    val element = if (isMaster && payload.isNotEmpty()) {
      val children = payload.parseEbmlElements(0, payload.size)
      dev.transmute.model.structure.common.EbmlElement(
        id = dev.transmute.model.identify.EbmlId(id),
        children = children,
      )
    } else {
      dev.transmute.model.structure.common.EbmlElement(
        id = dev.transmute.model.identify.EbmlId(id),
        data = Bytes(payload),
      )
    }

    elements += element
    pos = payloadEnd
  }

  return elements
}

/**
 * Parse Ogg pages from this byte array.
 *
 * Shared by OggAudio and Opus readers.
 */
fun ByteArray.parseOggPages(offset: Int = 0, end: Int = size): List<dev.transmute.model.structure.common.OggPage> {
  val pages = mutableListOf<dev.transmute.model.structure.common.OggPage>()
  var pos = offset

  while (pos + 27 <= end) {
    // Sync to "OggS"
    if (this[pos] != 0x4F.toByte() ||
      this[pos + 1] != 0x67.toByte() ||
      this[pos + 2] != 0x67.toByte() ||
      this[pos + 3] != 0x53.toByte()
    ) {
      pos++
      continue
    }

    val version = this[pos + 4].toUByte()
    val headerType = this[pos + 5].toUByte()
    val granulePosition = readI64LE(pos + 6)
    val serialNumber = readI32LE(pos + 14)
    val pageSequence = readU32LE(pos + 18)
    val crc = readU32LE(pos + 22)
    val segCount = this[pos + 26].toInt() and 0xFF

    if (pos + 27 + segCount > end) break
    val segTable = copyOfRange(pos + 27, pos + 27 + segCount)
    val dataSize = segTable.sumOf { it.toInt() and 0xFF }
    val dataStart = pos + 27 + segCount
    val dataEnd = minOf(dataStart + dataSize, end)

    if (dataEnd > end) break

    val pageData = copyOfRange(dataStart, dataEnd)

    pages += dev.transmute.model.structure.common.OggPage(
      version = version,
      headerType = headerType,
      granulePosition = granulePosition,
      serialNumber = dev.transmute.model.structure.common.OggSerialNumber(serialNumber),
      pageSequence = pageSequence,
      crc = crc,
      segmentTable = Bytes(segTable),
      data = Bytes(pageData),
    )

    pos = dataEnd
  }

  return pages
}

// --- EBML helpers ---

/** Read an EBML element ID starting at [pos]. Returns (id, byteCount) or null. */
private fun readEbmlId(data: ByteArray, pos: Int, end: Int): Pair<Long, Int>? {
  if (pos >= end) return null
  val first = data[pos].toInt() and 0xFF
  val len = when {
    first and 0x80 != 0 -> 1
    first and 0x40 != 0 -> 2
    first and 0x20 != 0 -> 3
    first and 0x10 != 0 -> 4
    else -> return null
  }
  if (pos + len > end) return null
  var id = 0L
  for (i in 0 until len) {
    id = (id shl 8) or (data[pos + i].toLong() and 0xFF)
  }
  return id to len
}

/** Read an EBML VINT (size) starting at [pos]. Returns (value, byteCount) or null. */
private fun readEbmlVint(data: ByteArray, pos: Int, end: Int): Pair<Long, Int>? {
  if (pos >= end) return null
  val first = data[pos].toInt() and 0xFF
  val len = when {
    first and 0x80 != 0 -> 1
    first and 0x40 != 0 -> 2
    first and 0x20 != 0 -> 3
    first and 0x10 != 0 -> 4
    first and 0x08 != 0 -> 5
    first and 0x04 != 0 -> 6
    first and 0x02 != 0 -> 7
    first and 0x01 != 0 -> 8
    else -> return null
  }
  if (pos + len > end) return null
  // Strip the leading marker bit
  val mask = 0xFF shr len
  var value = (first and mask).toLong()
  for (i in 1 until len) {
    value = (value shl 8) or (data[pos + i].toLong() and 0xFF)
  }
  // Check for "unknown size" (all data bits set to 1)
  val allOnes = (1L shl (7 * len)) - 1L
  if (value == allOnes) value = -1L // sentinel for unknown size
  return value to len
}

/** Master elements that contain children (well-known Matroska/WebM IDs). */
private fun isEbmlMasterElement(id: Long): Boolean = id in EBML_MASTER_IDS

private val EBML_MASTER_IDS = setOf(
  0x1A45DFA3L, // EBML header
  0x18538067L, // Segment
  0x114D9B74L, // SeekHead
  0x1549A966L, // Info
  0x1654AE6BL, // Tracks
  0x1F43B675L, // Cluster
  0x1C53BB6BL, // Cues
  0x1941A469L, // Attachments
  0x1043A770L, // Chapters
  0x1254C367L, // Tags
  0x4DBBL, // Seek
  0xAEL, // TrackEntry
  0xE0L, // Video settings
  0xE1L, // Audio settings
  0x6D80L, // ContentEncodings
  0x6240L, // ContentEncoding
  0xBBL, // CuePoint
  0xB7L, // CueTrackPositions
  0x61A7L, // Attached file
  0x6924L, // ChapterTranslate
  0x6944L, // ChapProcess
  0x7373L, // Tag
  0x63C0L, // Targets
  0x67C8L, // SimpleTag
)

// --- Byte helpers ---

internal fun ByteArray.readU16LE(off: Int): Int = (this[off].toInt() and 0xFF) or ((this[off + 1].toInt() and 0xFF) shl 8)

internal fun ByteArray.readU16BE(off: Int): Int = ((this[off].toInt() and 0xFF) shl 8) or (this[off + 1].toInt() and 0xFF)

internal fun ByteArray.readU32LE(off: Int): UInt = (this[off].toUInt() and 0xFFu) or
  ((this[off + 1].toUInt() and 0xFFu) shl 8) or
  ((this[off + 2].toUInt() and 0xFFu) shl 16) or
  ((this[off + 3].toUInt() and 0xFFu) shl 24)

internal fun ByteArray.readU32BE(off: Int): UInt = ((this[off].toUInt() and 0xFFu) shl 24) or
  ((this[off + 1].toUInt() and 0xFFu) shl 16) or
  ((this[off + 2].toUInt() and 0xFFu) shl 8) or
  (this[off + 3].toUInt() and 0xFFu)

internal fun ByteArray.readU64BE(off: Int): ULong = ((this[off].toULong() and 0xFFu) shl 56) or
  ((this[off + 1].toULong() and 0xFFu) shl 48) or
  ((this[off + 2].toULong() and 0xFFu) shl 40) or
  ((this[off + 3].toULong() and 0xFFu) shl 32) or
  ((this[off + 4].toULong() and 0xFFu) shl 24) or
  ((this[off + 5].toULong() and 0xFFu) shl 16) or
  ((this[off + 6].toULong() and 0xFFu) shl 8) or
  (this[off + 7].toULong() and 0xFFu)

internal fun ByteArray.readI32LE(off: Int): Int = (this[off].toInt() and 0xFF) or
  ((this[off + 1].toInt() and 0xFF) shl 8) or
  ((this[off + 2].toInt() and 0xFF) shl 16) or
  ((this[off + 3].toInt() and 0xFF) shl 24)

internal fun ByteArray.readI64LE(off: Int): Long = (this[off].toLong() and 0xFF) or
  ((this[off + 1].toLong() and 0xFF) shl 8) or
  ((this[off + 2].toLong() and 0xFF) shl 16) or
  ((this[off + 3].toLong() and 0xFF) shl 24) or
  ((this[off + 4].toLong() and 0xFF) shl 32) or
  ((this[off + 5].toLong() and 0xFF) shl 40) or
  ((this[off + 6].toLong() and 0xFF) shl 48) or
  ((this[off + 7].toLong() and 0xFF) shl 56)

internal fun ByteArray.decodeAscii(off: Int, len: Int): String =
  CharArray(len) { this[off + it].toInt().toChar() }.concatToString()
