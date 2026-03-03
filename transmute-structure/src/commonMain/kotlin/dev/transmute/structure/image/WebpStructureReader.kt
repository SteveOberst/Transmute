@file:Suppress("unused")

package dev.transmute.structure.image

import dev.transmute.model.core.Bytes
import dev.transmute.model.identify.RiffChunkId
import dev.transmute.model.structure.StructureReader
import dev.transmute.model.structure.common.RiffChunk
import dev.transmute.model.structure.image.types.WebpRaw
import dev.transmute.structure.common.parseRiffChildren
import dev.transmute.structure.common.readU32LE

/**
 * Parses raw WebP file bytes into a [WebpRaw] structure.
 *
 * WebP layout:
 * ```
 * | "RIFF" (4 B) | fileSize (4 B LE) | "WEBP" (4 B) | sub-chunks... |
 * ```
 */
class WebpStructureReader : StructureReader<WebpRaw> {

    override fun read(source: Bytes): WebpRaw {
        val d = source.data

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
