@file:Suppress("unused")

package dev.transmute.structure.audio

import dev.transmute.model.core.Bytes
import dev.transmute.model.structure.StructureReadException
import dev.transmute.model.structure.StructureReader
import dev.transmute.model.structure.audio.OggAudio
import dev.transmute.structure.common.parseOggPages

/**
 * Parses raw Ogg Vorbis file bytes into an [OggAudio] structure.
 *
 * Ogg Vorbis uses the Ogg container with a Vorbis codec.
 * The BOS page's first packet starts with `\x01vorbis`.
 */
class OggAudioStructureReader : StructureReader<OggAudio> {

    override fun canRead(source: Bytes): Boolean {
        val d = source.data
        if (d.size < 35) return false
        // "OggS" capture pattern
        if (d[0] != 0x4F.toByte() || d[1] != 0x67.toByte() ||
            d[2] != 0x67.toByte() || d[3] != 0x53.toByte()
        ) return false

        // Look for Vorbis identification packet in first page data
        // Page header: 27 bytes + segment table
        val segCount = d[26].toInt() and 0xFF
        val dataStart = 27 + segCount
        if (dataStart + 7 > d.size) return false
        // First byte: 0x01, followed by "vorbis"
        return d[dataStart] == 0x01.toByte() &&
            d[dataStart + 1] == 0x76.toByte() && d[dataStart + 2] == 0x6F.toByte() && // "vo"
            d[dataStart + 3] == 0x72.toByte() && d[dataStart + 4] == 0x62.toByte() && // "rb"
            d[dataStart + 5] == 0x69.toByte() && d[dataStart + 6] == 0x73.toByte()    // "is"
    }

    override fun read(source: Bytes): OggAudio {
        val d = source.data
        if (!canRead(source)) throw StructureReadException("Not an Ogg Vorbis file (bad signature)")
        val pages = d.parseOggPages()
        return OggAudio(pages = pages)
    }
}
