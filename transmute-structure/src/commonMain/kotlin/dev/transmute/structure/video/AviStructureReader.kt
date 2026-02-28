@file:Suppress("unused")

package dev.transmute.structure.video

import dev.transmute.model.core.Bytes
import dev.transmute.model.identify.RiffChunkId
import dev.transmute.model.structure.StructureReadException
import dev.transmute.model.structure.StructureReader
import dev.transmute.model.structure.common.RiffChunk
import dev.transmute.model.structure.video.AviRaw
import dev.transmute.structure.common.parseRiffChildren
import dev.transmute.structure.common.readU32LE

/**
 * Parses raw AVI file bytes into an [AviRaw] structure.
 *
 * AVI uses a RIFF container with form type `AVI `.
 *
 * ```
 * | "RIFF" (4 B) | fileSize (4 B LE) | "AVI " (4 B) | sub-chunks… |
 * ```
 */
class AviStructureReader : StructureReader<AviRaw> {

    override fun canRead(source: Bytes): Boolean {
        val d = source.data
        return d.size >= 12 &&
            d[0] == 0x52.toByte() && d[1] == 0x49.toByte() && // "RI"
            d[2] == 0x46.toByte() && d[3] == 0x46.toByte() && // "FF"
            d[8] == 0x41.toByte() && d[9] == 0x56.toByte() && // "AV"
            d[10] == 0x49.toByte() && d[11] == 0x20.toByte()  // "I "
    }

    override fun read(source: Bytes): AviRaw {
        val d = source.data
        if (!canRead(source)) throw StructureReadException("Not an AVI file (bad signature)")

        val fileSize = d.readU32LE(4)
        val children = d.parseRiffChildren(offset = 12, end = minOf(8 + fileSize.toInt(), d.size))

        val riff = RiffChunk(
            id = RiffChunkId("RIFF"),
            size = fileSize,
            formType = RiffChunkId("AVI "),
            children = children,
        )

        return AviRaw(riff = riff)
    }
}
