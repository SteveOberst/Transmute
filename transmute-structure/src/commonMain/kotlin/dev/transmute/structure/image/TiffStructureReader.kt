@file:Suppress("unused")

package dev.transmute.structure.image

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes
import dev.transmute.model.identify.Endianness
import dev.transmute.model.structure.StructureReader
import dev.transmute.model.structure.image.types.TiffIfd
import dev.transmute.model.structure.image.types.TiffIfdEntry
import dev.transmute.model.structure.image.types.TiffRaw

/**
 * Parses raw TIFF file bytes into a [TiffRaw] structure.
 *
 * TiffRaw layout:
 * ```
 * | byte-order (2 B) | magic 42 (2 B) | firstIfdOffset (4 B) | IFDs & data ... |
 * ```
 */
class TiffStructureReader : StructureReader<TiffRaw> {

  override fun read(source: Bytes): TiffRaw {
    val d = source.data

    val byteOrder = if (d[0] == 0x49.toByte()) Endianness.Little else Endianness.Big
    val firstIfdOffset = readU32(d, 4, byteOrder)

    // Parse the full reachable IFD graph starting from the first IFD.
    // This includes the main IFD chain (nextIfdOffset) plus referenced
    // IFDs via ExifIFD (34665), GPSIFD (34853), and SubIFDs (330).
    val ifdByOffset = linkedMapOf<UInt, TiffIfd>()
    val toVisit = ArrayDeque<UInt>()
    if (firstIfdOffset != 0u) toVisit.add(firstIfdOffset)

    fun readIfdAt(offset: UInt): TiffIfd? {
      if (offset == 0u) return null
      if (offset.toInt() + 2 > d.size) return null
      val off = offset.toInt()
      val entryCount = readU16(d, off, byteOrder)
      val entriesStart = off + 2
      val entries = mutableListOf<TiffIfdEntry>()

      for (i in 0 until entryCount) {
        val eOff = entriesStart + i * 12
        if (eOff + 12 > d.size) break

        val tag = readU16(d, eOff, byteOrder).toUShort()
        val fieldType = readU16(d, eOff + 2, byteOrder).toUShort()
        val count = readU32(d, eOff + 4, byteOrder)
        val valueOrOffset = d.copyOfRange(eOff + 8, eOff + 12).asBytes()

        // Resolve value data
        val bytesPerValue = tiffFieldSize(fieldType.toInt())
        val totalValueBytes = count.toLong() * bytesPerValue
        val data = if (totalValueBytes <= 4) {
          valueOrOffset
        } else {
          val dataOff = readU32(d, eOff + 8, byteOrder).toInt()
          if (dataOff >= 0 && dataOff + totalValueBytes.toInt() <= d.size) {
            d.copyOfRange(dataOff, dataOff + totalValueBytes.toInt()).asBytes()
          } else {
            valueOrOffset
          }
        }

        entries += TiffIfdEntry(
          tag = tag,
          fieldType = fieldType,
          count = count,
          valueOrOffset = valueOrOffset,
          data = data,
        )
      }

      val nextOff = entriesStart + entryCount * 12
      val nextIfd = if (nextOff + 4 <= d.size) readU32(d, nextOff, byteOrder) else 0u
      return TiffIfd(offset = offset, entries = entries, nextIfdOffset = nextIfd)
    }

    fun readU32FromBytes(bytes: ByteArray, off: Int): UInt = when (byteOrder) {
      Endianness.Little ->
        (bytes[off].toUInt() and 0xFFu) or
          ((bytes[off + 1].toUInt() and 0xFFu) shl 8) or
          ((bytes[off + 2].toUInt() and 0xFFu) shl 16) or
          ((bytes[off + 3].toUInt() and 0xFFu) shl 24)
      Endianness.Big ->
        ((bytes[off].toUInt() and 0xFFu) shl 24) or
          ((bytes[off + 1].toUInt() and 0xFFu) shl 16) or
          ((bytes[off + 2].toUInt() and 0xFFu) shl 8) or
          (bytes[off + 3].toUInt() and 0xFFu)
    }

    fun offsetValues(entry: TiffIfdEntry): List<UInt> {
      val payload = entry.data.data
      if (payload.size < 4) return emptyList()
      val count = entry.count.toInt().coerceAtLeast(0)
      return (0 until count).mapNotNull { i ->
        val o = i * 4
        if (o + 3 < payload.size) readU32FromBytes(payload, o) else null
      }
    }

    while (toVisit.isNotEmpty()) {
      val offset = toVisit.removeFirst()
      if (ifdByOffset.containsKey(offset)) continue
      val ifd = readIfdAt(offset) ?: continue
      ifdByOffset[offset] = ifd

      // Follow the main chain
      if (ifd.nextIfdOffset != 0u) toVisit.add(ifd.nextIfdOffset)

      // Follow referenced IFD pointers
      val exif = ifd.entries.firstOrNull { it.tag == 34665u.toUShort() }?.let { offsetValues(it).firstOrNull() }
      val gps = ifd.entries.firstOrNull { it.tag == 34853u.toUShort() }?.let { offsetValues(it).firstOrNull() }
      val subIfds = ifd.entries.firstOrNull { it.tag == 330u.toUShort() }?.let { offsetValues(it) } ?: emptyList()

      listOfNotNull(exif, gps).forEach { if (it != 0u) toVisit.add(it) }
      subIfds.filter { it != 0u }.forEach { toVisit.add(it) }
    }

    // Deterministic ordering for Raw: primary chain first, then any referenced IFDs.
    val orderedIfds = mutableListOf<TiffIfd>()
    val inOrder = mutableSetOf<UInt>()
    var chainOff = firstIfdOffset
    while (chainOff != 0u && inOrder.add(chainOff)) {
      val ifd = ifdByOffset[chainOff] ?: break
      orderedIfds += ifd
      chainOff = ifd.nextIfdOffset
    }
    // Add any remaining discovered IFDs in ascending-offset order.
    orderedIfds += ifdByOffset
      .filterKeys { it !in inOrder }
      .toList()
      .sortedBy { it.first }
      .map { it.second }

    return TiffRaw(
      byteOrder = byteOrder,
      firstIfdOffset = firstIfdOffset,
      ifds = orderedIfds,
    )
  }
}

// --- Byte helpers ---

private fun readU16(d: ByteArray, off: Int, order: Endianness): Int = when (order) {
  Endianness.Little -> (d[off].toInt() and 0xFF) or ((d[off + 1].toInt() and 0xFF) shl 8)
  Endianness.Big -> ((d[off].toInt() and 0xFF) shl 8) or (d[off + 1].toInt() and 0xFF)
}

private fun readU32(d: ByteArray, off: Int, order: Endianness): UInt = when (order) {
  Endianness.Little ->
    (d[off].toUInt() and 0xFFu) or
      ((d[off + 1].toUInt() and 0xFFu) shl 8) or
      ((d[off + 2].toUInt() and 0xFFu) shl 16) or
      ((d[off + 3].toUInt() and 0xFFu) shl 24)
  Endianness.Big ->
    ((d[off].toUInt() and 0xFFu) shl 24) or
      ((d[off + 1].toUInt() and 0xFFu) shl 16) or
      ((d[off + 2].toUInt() and 0xFFu) shl 8) or
      (d[off + 3].toUInt() and 0xFFu)
}

/** Bytes per value for the given TiffRaw field type code. */
private fun tiffFieldSize(typeCode: Int): Int = when (typeCode) {
  1, 2, 6, 7 -> 1 // BYTE, ASCII, SBYTE, UNDEFINED
  3, 8 -> 2 // SHORT, SSHORT
  4, 9, 11 -> 4 // LONG, SLONG, FLOAT
  5, 10, 12 -> 8 // RATIONAL, SRATIONAL, DOUBLE
  else -> 1
}
