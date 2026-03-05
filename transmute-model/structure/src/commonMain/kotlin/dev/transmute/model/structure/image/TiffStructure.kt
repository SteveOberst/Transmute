@file:Suppress("unused")

package dev.transmute.model.structure.image

import dev.transmute.model.core.MediaStructure
import dev.transmute.model.identify.Endianness
import dev.transmute.model.structure.image.types.TiffIfdEntry
import dev.transmute.model.structure.image.types.TiffRaw
import kotlinx.serialization.Serializable

@Serializable
data class TiffIfdTree(
  /** Offset of this IFD from the start of the file, in bytes. */
  val offset: UInt,
  /** Entries belonging to this IFD. */
  val entries: List<TiffIfdEntry>,
  /** Next IFD in the primary chain (IFD0 -> IFD1 -> ...), if present. */
  val next: TiffIfdTree? = null,
  /** Exif IFD referenced by tag 34665, if present. */
  val exifIfd: TiffIfdTree? = null,
  /** GPS IFD referenced by tag 34853, if present. */
  val gpsIfd: TiffIfdTree? = null,
  /** SubIFDs referenced by tag 330, if present. */
  val subIfds: List<TiffIfdTree> = emptyList(),
)

/**
 * Structured representation of a TIFF file, mirroring the on-disk layout.
 *
 * ```
 * | byteOrder (2 B) | magic 42 (2 B) | firstIfdOffset (4 B) | IFD chain ... |
 * ```
 *
 * Image strip / tile data blobs are excluded; IFD hierarchy (chain + referenced
 * Exif/GPS/SubIFDs) is preserved.
 */
@Serializable
data class TiffStructure(
  /** Byte order present in the TIFF header: `II` (Little) or `MM` (Big). */
  val byteOrder: Endianness,
  /** Offset of the first IFD from the start of the file, in bytes. */
  val firstIfdOffset: UInt,
  /** Full TIFF IFD hierarchy rooted at [firstIfdOffset]. */
  val ifdChain: TiffIfdTree?,
) : MediaStructure

/**
 * Parse this [dev.transmute.model.structure.image.types.TiffRaw] into a [TiffStructure].
 */
fun TiffRaw.toStructure(): TiffStructure = TiffStructure(
  byteOrder = byteOrder,
  firstIfdOffset = firstIfdOffset,
  ifdChain = buildIfdTree(),
)

private fun TiffRaw.buildIfdTree(): TiffIfdTree? {
  if (firstIfdOffset == 0u) return null
  val byOffset = ifds.associateBy { it.offset }
  val visited = mutableSetOf<UInt>()

  fun readU32(bytes: ByteArray, off: Int): UInt = when (byteOrder) {
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
    val d = entry.data.data
    if (d.size < 4) return emptyList()
    val count = entry.count.toInt().coerceAtLeast(0)
    return (0 until count).mapNotNull { i ->
      val o = i * 4
      if (o + 3 < d.size) readU32(d, o) else null
    }
  }

  fun build(offset: UInt): TiffIfdTree? {
    val ifd = byOffset[offset] ?: return null
    if (!visited.add(offset)) return null

    val exifOffset = ifd.entries
      .firstOrNull { it.tag == 34665u.toUShort() }
      ?.let { offsetValues(it).firstOrNull() }

    val gpsOffset = ifd.entries
      .firstOrNull { it.tag == 34853u.toUShort() }
      ?.let { offsetValues(it).firstOrNull() }

    val subIfdOffsets = ifd.entries
      .firstOrNull { it.tag == 330u.toUShort() }
      ?.let { offsetValues(it) }
      ?: emptyList()

    return TiffIfdTree(
      offset = ifd.offset,
      entries = ifd.entries,
      next = if (ifd.nextIfdOffset != 0u) build(ifd.nextIfdOffset) else null,
      exifIfd = exifOffset?.let { build(it) },
      gpsIfd = gpsOffset?.let { build(it) },
      subIfds = subIfdOffsets.mapNotNull { build(it) },
    )
  }

  return build(firstIfdOffset)
}
