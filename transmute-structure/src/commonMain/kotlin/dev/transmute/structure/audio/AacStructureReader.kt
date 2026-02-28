@file:Suppress("unused")

package dev.transmute.structure.audio

import dev.transmute.model.core.Bytes
import dev.transmute.model.structure.StructureReadException
import dev.transmute.model.structure.StructureReader
import dev.transmute.model.structure.audio.Aac

/**
 * Parses raw AAC ADTS file bytes into an [Aac] structure.
 *
 * AAC ADTS is a stream of self-delimiting frames starting with a
 * 12-bit sync word (`0xFFF`).  The entire file is stored as an
 * opaque blob since individual frame parsing is expensive.
 */
class AacStructureReader : StructureReader<Aac> {

    override fun canRead(source: Bytes): Boolean {
        val d = source.data
        if (d.size < 7) return false
        // ADTS sync word: 0xFFF (first 12 bits)
        return (d[0].toInt() and 0xFF) == 0xFF &&
            (d[1].toInt() and 0xF0) == 0xF0
    }

    override fun read(source: Bytes): Aac {
        if (!canRead(source)) throw StructureReadException("Not an AAC ADTS file (bad sync word)")
        return Aac(data = source)
    }
}
