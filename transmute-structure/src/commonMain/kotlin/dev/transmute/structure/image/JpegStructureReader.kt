@file:Suppress("unused")

package dev.transmute.structure.image

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes
import dev.transmute.model.structure.StructureReadException
import dev.transmute.model.structure.StructureReader
import dev.transmute.model.structure.image.Jpeg
import dev.transmute.model.structure.image.JpegMarkerType
import dev.transmute.model.structure.image.JpegSegment

/**
 * Parses raw JPEG file bytes into a [Jpeg] structure.
 *
 * JPEG layout:
 * ```
 * | 0xFF D8 (SOI) | segment₁ | segment₂ | … | 0xFF D9 (EOI) |
 * ```
 *
 * Each segment is either **standalone** (SOI, EOI, RST0–RST7) with no
 * payload, or **payload** with `| 0xFF | marker | length (2 B BE) | data |`.
 * SOS segments additionally carry entropy-coded scan data until the next
 * non-stuffed `0xFF` marker.
 */
class JpegStructureReader : StructureReader<Jpeg> {

    override fun canRead(source: Bytes): Boolean {
        val d = source.data
        return d.size >= 2 && d[0] == 0xFF.toByte() && d[1] == 0xD8.toByte()
    }

    override fun read(source: Bytes): Jpeg {
        val d = source.data
        if (!canRead(source)) throw StructureReadException("Not a JPEG file (bad SOI marker)")

        val segments = mutableListOf<JpegSegment>()
        var pos = 0

        while (pos < d.size) {
            // Scan for 0xFF
            if (d[pos] != 0xFF.toByte()) {
                pos++
                continue
            }

            // Skip fill bytes (consecutive 0xFF)
            while (pos + 1 < d.size && d[pos + 1] == 0xFF.toByte()) pos++
            if (pos + 1 >= d.size) break

            val marker = d[pos + 1].toUByte()
            pos += 2

            // 0xFF 0x00 is a stuffed byte inside entropy data — shouldn't be here at top level
            if (marker == 0x00.toUByte()) continue

            if (JpegMarkerType.isStandalone(marker)) {
                segments += JpegSegment(marker = marker)
                if (marker == 0xD9.toUByte()) break // EOI — done
                continue
            }

            // Payload marker: read 2-byte length
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

            // SOS has entropy-coded data following the header
            val entropy = if (marker == 0xDA.toUByte()) {
                readEntropyData(d, pos)
            } else {
                null
            }

            segments += JpegSegment(
                marker = marker,
                data = segData.asBytes(),
                entropy = entropy?.asBytes() ?: Bytes(ByteArray(0)),
            )

            if (entropy != null) {
                pos += entropy.size
            }
        }

        return Jpeg(segments = segments)
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
                    // Found a real marker — entropy data ends here
                    return data.copyOfRange(start, pos)
                }
                pos += 2 // skip stuffed byte or RST marker
            } else {
                pos++
            }
        }
        return data.copyOfRange(start, pos)
    }
}

// --- Byte helpers ---

private fun ByteArray.readU16BE(off: Int): Int =
    ((this[off].toInt() and 0xFF) shl 8) or (this[off + 1].toInt() and 0xFF)
