@file:Suppress("unused")

package dev.transmute.structure.image

import dev.transmute.model.core.Bytes
import dev.transmute.model.identify.RiffChunkId
import dev.transmute.model.structure.StructureReadException
import dev.transmute.model.structure.StructureReader
import dev.transmute.model.structure.common.RiffChunk
import dev.transmute.model.structure.image.WebpRaw
import dev.transmute.structure.common.parseRiffChildren
import dev.transmute.structure.common.readU32LE

/**
 * Parses raw WebP file bytes into a [WebpRaw] structure.
 *
 * WebP layout:
 * ```
 * | "RIFF" (4 B) | fileSize (4 B LE) | "WEBP" (4 B) | sub-chunks… |
 * ```
 */
class WebpStructureReader : StructureReader<WebpRaw> {

    override fun canRead(source: Bytes): Boolean {
        val d = source.data
        return d.size >= 12 &&
            d[0] == 0x52.toByte() && d[1] == 0x49.toByte() && // "RI"
            d[2] == 0x46.toByte() && d[3] == 0x46.toByte() && // "FF"
            d[8] == 0x57.toByte() && d[9] == 0x45.toByte() && // "WE"
            d[10] == 0x42.toByte() && d[11] == 0x50.toByte()  // "BP"
    }

    override fun read(source: Bytes): WebpRaw {
        val d = source.data
        if (!canRead(source)) throw StructureReadException("Not a WebP file (bad signature)")

        val fileSize = d.readU32LE(4)
        val children = d.parseRiffChildren(offset = 12, end = minOf(8 + fileSize.toInt(), d.size))

        val riff = RiffChunk(
            id = RiffChunkId("RIFF"),
            size = fileSize,
            formType = RiffChunkId("WEBP"),
            children = children,
        )

        return WebpRaw(riff = riff)
    }
}
