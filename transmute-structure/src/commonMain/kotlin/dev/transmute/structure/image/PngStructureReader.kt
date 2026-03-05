@file:Suppress("unused")

package dev.transmute.structure.image

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes
import dev.transmute.model.identify.FourCC
import dev.transmute.model.structure.StructureReadException
import dev.transmute.model.structure.StructureReader
import dev.transmute.model.structure.image.types.PngChunk
import dev.transmute.model.structure.image.types.PngRaw
import dev.transmute.structure.common.decodeAscii
import dev.transmute.structure.common.readU32BE

/**
 * Parses raw PNG file bytes into a [PngRaw] structure.
 *
 * PngRaw layout:
 * ```
 * | signature (8 B) | chunk1 | chunk2 | ... | IEND chunk |
 * ```
 *
 * Each chunk:
 * ```
 * | length (4 B BE) | type (4 B ASCII) | data (length B) | crc (4 B) |
 * ```
 */
class PngStructureReader : StructureReader<PngRaw> {

  override fun read(source: Bytes): PngRaw {
    val d = source.data

    val signature = Bytes(d.copyOfRange(0, 8))
    val chunks = mutableListOf<PngChunk>()
    var pos = 8

    while (pos + 12 <= d.size) { // minimum chunk: 4 (len) + 4 (type) + 0 (data) + 4 (crc) = 12
      val length = d.readU32BE(pos)
      val type = d.decodeAscii(pos + 4, 4)
      val dataStart = pos + 8
      val dataEnd = dataStart + length.toInt()
      if (dataEnd + 4 > d.size) {
        throw StructureReadException(
          "PngRaw chunk '$type' at offset $pos overflows file " +
            "(need ${dataEnd + 4}, have ${d.size})",
        )
      }
      val crc = d.readU32BE(dataEnd)
      val chunkData = if (length > 0u) d.copyOfRange(dataStart, dataEnd) else ByteArray(0)

      chunks += PngChunk(
        length = length,
        type = FourCC(type),
        data = chunkData.asBytes(),
        crc = crc,
      )

      pos = dataEnd + 4 // past CRC
      if (type == "IEND") break
    }

    return PngRaw(signature = signature, chunks = chunks)
  }
}
