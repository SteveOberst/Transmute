@file:Suppress("unused")

package dev.transmute.structure.image

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes
import dev.transmute.model.structure.StructureReader
import dev.transmute.model.structure.image.types.JpegFrame
import dev.transmute.model.structure.image.types.JpegMarkerType
import dev.transmute.model.structure.image.types.JpegRaw
import dev.transmute.model.structure.image.types.JpegScan
import dev.transmute.model.structure.image.types.JpegSegment
import dev.transmute.model.structure.image.types.parseSofData
import dev.transmute.model.structure.image.types.parseSosData
import dev.transmute.structure.common.readU16BE

/**
 * Parses raw JPEG file bytes into a [JpegRaw] structure following the
 * ITU-T T.81 hierarchy: Image -> Frame -> Scan(s).
 *
 * ```
 * SOI
 *   +- [APP0, APP1 ..., COM, DQT ...]     <- headerSegments
 *   +- Frame (SOFn)
 *        +- [DQT, DHT, DRI ...]          <- frame.tableSegments
 *        +- Scan 1 (SOS + entropy)
 *        +- [inter-scan tables]
 *        +- Scan 2 ...
 * EOI                                    <- trailerSegments
 * ```
 */
class JpegStructureReader : StructureReader<JpegRaw> {

  override fun read(source: Bytes): JpegRaw {
    val d = source.data

    // Step 1: Parse all raw markers into a flat list of (marker, data, entropy)
    data class RawMarker(val marker: UByte, val data: ByteArray, val entropy: ByteArray)

    val markers = mutableListOf<RawMarker>()
    var pos = 0

    while (pos < d.size) {
      if (d[pos] != 0xFF.toByte()) {
        pos++
        continue
      }
      while (pos + 1 < d.size && d[pos + 1] == 0xFF.toByte()) pos++
      if (pos + 1 >= d.size) break

      val marker = d[pos + 1].toUByte()
      pos += 2

      if (marker == 0x00.toUByte()) continue

      if (JpegMarkerType.isStandalone(marker)) {
        markers += RawMarker(marker, ByteArray(0), ByteArray(0))
        if (marker == 0xD9.toUByte()) break
        continue
      }

      if (pos + 2 > d.size) break
      val length = d.readU16BE(pos)
      val dataLength = length - 2
      pos += 2

      val segData = if (dataLength > 0 && pos + dataLength <= d.size) {
        d.copyOfRange(pos, pos + dataLength)
      } else {
        ByteArray(0)
      }
      pos += maxOf(dataLength, 0)

      val entropy = if (marker == 0xDA.toUByte()) {
        readEntropyData(d, pos)
      } else {
        ByteArray(0)
      }
      if (entropy.isNotEmpty()) pos += entropy.size

      markers += RawMarker(marker, segData, entropy)
    }

    // Step 2: Organize into ITU-T T.81 hierarchy
    val headerSegments = mutableListOf<JpegSegment>()
    var frame: JpegFrame? = null
    val trailerSegments = mutableListOf<JpegSegment>()
    var idx = 0

    // 2a. Collect header segments (SOI, APP, COM, etc.) until SOF
    while (idx < markers.size) {
      val m = markers[idx]
      if (JpegMarkerType.isSof(m.marker)) break
      headerSegments += JpegSegment(marker = m.marker, data = m.data.asBytes())
      idx++
    }

    // 2b. Parse frame if SOF found
    if (idx < markers.size && JpegMarkerType.isSof(markers[idx].marker)) {
      val sofRaw = markers[idx]
      val sofSegment = JpegSegment(marker = sofRaw.marker, data = sofRaw.data.asBytes())
      val sofData = parseSofData(sofRaw.data)
      idx++

      // Collect table segments between SOF and first SOS
      val tableSegments = mutableListOf<JpegSegment>()
      while (idx < markers.size) {
        val m = markers[idx]
        if (m.marker.toInt() == 0xDA || m.marker.toInt() == 0xD9) break
        if (JpegMarkerType.isSof(m.marker)) break
        tableSegments += JpegSegment(marker = m.marker, data = m.data.asBytes())
        idx++
      }

      // Collect scans
      val scans = mutableListOf<JpegScan>()
      while (idx < markers.size && markers[idx].marker.toInt() == 0xDA) {
        val sosRaw = markers[idx]
        val sosSegment = JpegSegment(marker = sosRaw.marker, data = sosRaw.data.asBytes())
        val sosData = parseSosData(sosRaw.data)
        val entropy = sosRaw.entropy.asBytes()
        idx++

        // Collect inter-scan segments
        val interScan = mutableListOf<JpegSegment>()
        while (idx < markers.size) {
          val m = markers[idx]
          if (m.marker.toInt() == 0xDA || m.marker.toInt() == 0xD9) break
          interScan += JpegSegment(marker = m.marker, data = m.data.asBytes())
          idx++
        }

        scans += JpegScan(
          sosSegment = sosSegment,
          sosData = sosData,
          entropy = entropy,
          interScanSegments = interScan,
        )
      }

      frame = JpegFrame(
        sofMarker = sofRaw.marker,
        sofSegment = sofSegment,
        sofData = sofData,
        tableSegments = tableSegments,
        scans = scans,
      )
    }

    // 2c. Collect trailer segments (EOI etc.)
    while (idx < markers.size) {
      val m = markers[idx]
      trailerSegments += JpegSegment(marker = m.marker, data = m.data.asBytes())
      idx++
    }

    return JpegRaw(
      headerSegments = headerSegments,
      frame = frame,
      trailerSegments = trailerSegments,
    )
  }

  /**
   * Read entropy-coded data after an SOS header until the next valid marker.
   *
   * Entropy data may contain `0xFF 0x00` byte-stuffed pairs.
   * A non-zero marker byte after `0xFF` signals the end of entropy data.
   */
  private fun readEntropyData(data: ByteArray, start: Int): ByteArray {
    var pos = start
    while (pos < data.size) {
      if (data[pos] == 0xFF.toByte()) {
        if (pos + 1 >= data.size) break
        val next = data[pos + 1].toInt() and 0xFF
        if (next != 0 && next !in 0xD0..0xD7) {
          return data.copyOfRange(start, pos)
        }
        pos += 2
      } else {
        pos++
      }
    }
    return data.copyOfRange(start, pos)
  }
}
