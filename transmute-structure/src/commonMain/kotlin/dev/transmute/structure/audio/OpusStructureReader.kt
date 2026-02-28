@file:Suppress("unused")

package dev.transmute.structure.audio

import dev.transmute.model.core.Bytes
import dev.transmute.model.structure.StructureReadException
import dev.transmute.model.structure.StructureReader
import dev.transmute.model.structure.audio.Opus
import dev.transmute.structure.common.parseOggPages

/**
 * Parses raw Ogg Opus file bytes into an [Opus] structure.
 *
 * Opus uses the Ogg container.  The BOS page's first packet
 * starts with `OpusHead`.
 */
class OpusStructureReader : StructureReader<Opus> {

    override fun canRead(source: Bytes): Boolean {
        val d = source.data
        if (d.size < 36) return false
        // "OggS" capture pattern
        if (d[0] != 0x4F.toByte() || d[1] != 0x67.toByte() ||
            d[2] != 0x67.toByte() || d[3] != 0x53.toByte()
        ) return false

        // Look for Opus identification packet in first page data
        val segCount = d[26].toInt() and 0xFF
        val dataStart = 27 + segCount
        if (dataStart + 8 > d.size) return false
        // "OpusHead"
        return d[dataStart] == 0x4F.toByte() && d[dataStart + 1] == 0x70.toByte() && // "Op"
            d[dataStart + 2] == 0x75.toByte() && d[dataStart + 3] == 0x73.toByte() && // "us"
            d[dataStart + 4] == 0x48.toByte() && d[dataStart + 5] == 0x65.toByte() && // "He"
            d[dataStart + 6] == 0x61.toByte() && d[dataStart + 7] == 0x64.toByte()    // "ad"
    }

    override fun read(source: Bytes): Opus {
        val d = source.data
        if (!canRead(source)) throw StructureReadException("Not an Ogg Opus file (bad signature)")
        val pages = d.parseOggPages()
        return Opus(pages = pages)
    }
}
