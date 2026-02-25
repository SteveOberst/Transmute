@file:Suppress("unused")

package dev.transmute.structure.audio

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes
import dev.transmute.model.identify.RiffChunkId
import dev.transmute.model.structure.StructureReadException
import dev.transmute.model.structure.StructureReader
import dev.transmute.model.structure.audio.Wav
import dev.transmute.model.structure.common.RiffChunk

/**
 * Parses raw WAV file bytes into a [Wav] structure.
 *
 * WAV files use the RIFF container format:
 * ```
 * | "RIFF" (4 B) | fileSize (4 B LE) | "WAVE" (4 B) | sub-chunks… |
 * ```
 */
class WavStructureReader : StructureReader<Wav> {

    override fun canRead(source: Bytes): Boolean {
        val d = source.data
        return d.size >= 12 &&
            d[0] == 0x52.toByte() && d[1] == 0x49.toByte() && // "RI"
            d[2] == 0x46.toByte() && d[3] == 0x46.toByte() && // "FF"
            d[8] == 0x57.toByte() && d[9] == 0x41.toByte() && // "WA"
            d[10] == 0x56.toByte() && d[11] == 0x45.toByte()  // "VE"
    }

    override fun read(source: Bytes): Wav {
        val d = source.data
        if (d.size < 12) throw StructureReadException("WAV file too small (${d.size} bytes)")

        val riffId = d.decodeAscii(0, 4)
        if (riffId != "RIFF") throw StructureReadException("Not a RIFF file: got '$riffId'")

        val fileSize = d.readU32LE(4)
        val formType = d.decodeAscii(8, 4)
        if (formType != "WAVE") throw StructureReadException("Not a WAV file: form type '$formType'")

        val children = parseRiffChildren(d, offset = 12, end = minOf(8 + fileSize.toInt(), d.size))

        val riff = RiffChunk(
            id = RiffChunkId("RIFF"),
            size = fileSize,
            formType = RiffChunkId("WAVE"),
            children = children,
        )

        return Wav(riff = riff)
    }
}

/**
 * Parse sub-chunks within a RIFF/LIST container.
 */
private fun parseRiffChildren(
    data: ByteArray,
    offset: Int,
    end: Int,
): List<RiffChunk> {
    val chunks = mutableListOf<RiffChunk>()
    var pos = offset

    while (pos + 8 <= end) {
        val id = data.decodeAscii(pos, 4)
        val size = data.readU32LE(pos + 4)
        val payloadStart = pos + 8
        val payloadEnd = minOf(payloadStart + size.toInt(), end)

        if (id == "RIFF" || id == "LIST") {
            val ft = if (payloadStart + 4 <= end) data.decodeAscii(payloadStart, 4) else "    "
            val children = parseRiffChildren(data, payloadStart + 4, payloadEnd)
            chunks += RiffChunk(
                id = RiffChunkId(id),
                size = size,
                formType = RiffChunkId(ft),
                children = children,
            )
        } else {
            val payload = if (payloadEnd > payloadStart) {
                data.copyOfRange(payloadStart, payloadEnd)
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

// --- Byte helpers ---

private fun ByteArray.readU32LE(off: Int): UInt =
    (this[off].toUInt() and 0xFFu) or
        ((this[off + 1].toUInt() and 0xFFu) shl 8) or
        ((this[off + 2].toUInt() and 0xFFu) shl 16) or
        ((this[off + 3].toUInt() and 0xFFu) shl 24)

private fun ByteArray.decodeAscii(off: Int, len: Int): String =
    String(CharArray(len) { this[off + it].toInt().toChar() })
