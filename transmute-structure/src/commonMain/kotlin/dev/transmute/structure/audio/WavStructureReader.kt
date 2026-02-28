@file:Suppress("unused")

package dev.transmute.structure.audio

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes
import dev.transmute.model.identify.RiffChunkId
import dev.transmute.model.structure.StructureReadException
import dev.transmute.model.structure.StructureReader
import dev.transmute.model.structure.audio.WavRaw
import dev.transmute.model.structure.common.RiffChunk
import dev.transmute.structure.common.decodeAscii
import dev.transmute.structure.common.parseRiffChildren
import dev.transmute.structure.common.readU32LE

/**
 * Parses raw WavRaw file bytes into a [WavRaw] structure.
 *
 * WavRaw files use the RIFF container format:
 * ```
 * | "RIFF" (4 B) | fileSize (4 B LE) | "WAVE" (4 B) | sub-chunks… |
 * ```
 */
class WavStructureReader : StructureReader<WavRaw> {

    override fun canRead(source: Bytes): Boolean {
        val d = source.data
        return d.size >= 12 &&
            d[0] == 0x52.toByte() && d[1] == 0x49.toByte() && // "RI"
            d[2] == 0x46.toByte() && d[3] == 0x46.toByte() && // "FF"
            d[8] == 0x57.toByte() && d[9] == 0x41.toByte() && // "WA"
            d[10] == 0x56.toByte() && d[11] == 0x45.toByte()  // "VE"
    }

    override fun read(source: Bytes): WavRaw {
        val d = source.data
        if (d.size < 12) throw StructureReadException("WAV file too small (${d.size} bytes)")

        val riffId = d.decodeAscii(0, 4)
        if (riffId != "RIFF") throw StructureReadException("Not a RIFF file: got '$riffId'")

        val fileSize = d.readU32LE(4)
        val formType = d.decodeAscii(8, 4)
        if (formType != "WAVE") throw StructureReadException("Not a WAV file: form type '$formType'")

        val children = d.parseRiffChildren(offset = 12, end = minOf(8 + fileSize.toInt(), d.size))

        val riff = RiffChunk(
            id = RiffChunkId("RIFF"),
            size = fileSize,
            formType = RiffChunkId("WAVE"),
            children = children,
        )

        return WavRaw(riff = riff)
    }
}
