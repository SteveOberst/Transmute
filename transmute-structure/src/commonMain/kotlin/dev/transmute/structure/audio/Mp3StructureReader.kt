@file:Suppress("unused")

package dev.transmute.structure.audio

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes
import dev.transmute.model.structure.StructureReadException
import dev.transmute.model.structure.StructureReader
import dev.transmute.model.structure.audio.Mp3Raw

/**
 * Parses raw Mp3Raw file bytes into an [Mp3Raw] structure.
 *
 * Mp3Raw layout:
 * ```
 * | [ID3v2 tag] | MPEG frame₁ | MPEG frame₂ | … | [ID3v1 tag (128 B)] |
 * ```
 *
 * The reader extracts the optional ID3v2 header, the raw audio frames
 * as a single blob, and the optional 128-byte ID3v1 trailer.
 */
class Mp3StructureReader : StructureReader<Mp3Raw> {

    override fun canRead(source: Bytes): Boolean {
        val d = source.data
        if (d.size < 4) return false
        // ID3v2 tag at start
        if (d[0] == 0x49.toByte() && d[1] == 0x44.toByte() && d[2] == 0x33.toByte()) return true
        // MPEG sync word (0xFF followed by 0xE0+ mask)
        if ((d[0].toInt() and 0xFF) == 0xFF && (d[1].toInt() and 0xE0) == 0xE0) return true
        return false
    }

    override fun read(source: Bytes): Mp3Raw {
        val d = source.data
        if (!canRead(source)) throw StructureReadException("Not an MP3 file")

        var audioStart = 0

        // --- Parse optional ID3v2 tag ---
        val id3v2: Bytes? = if (d.size >= 10 &&
            d[0] == 0x49.toByte() && d[1] == 0x44.toByte() && d[2] == 0x33.toByte()
        ) {
            val tagSize = syncsafeInt(d, 6)
            val headerSize = 10
            val totalSize = headerSize + tagSize
            if (totalSize <= d.size) {
                audioStart = totalSize
                d.copyOfRange(0, totalSize).asBytes()
            } else {
                null
            }
        } else {
            null
        }

        // --- Parse optional ID3v1 tag (last 128 bytes, starts with "TAG") ---
        val id3v1: Bytes? = if (d.size >= 128) {
            val tagStart = d.size - 128
            if (d[tagStart] == 0x54.toByte() &&       // 'T'
                d[tagStart + 1] == 0x41.toByte() &&    // 'A'
                d[tagStart + 2] == 0x47.toByte()       // 'G'
            ) {
                d.copyOfRange(tagStart, d.size).asBytes()
            } else {
                null
            }
        } else {
            null
        }

        val audioEnd = if (id3v1 != null) d.size - 128 else d.size
        if (audioStart >= audioEnd) throw StructureReadException("No audio data found in MP3 file")

        val audioData = d.copyOfRange(audioStart, audioEnd).asBytes()

        return Mp3Raw(
            id3v2Tag = id3v2,
            audioData = audioData,
            id3v1TagData = id3v1,
        )
    }

    /**
     * Decode a 4-byte syncsafe integer (ID3v2 tag size).
     * Each byte uses only the lower 7 bits.
     */
    private fun syncsafeInt(d: ByteArray, off: Int): Int =
        ((d[off].toInt() and 0x7F) shl 21) or
            ((d[off + 1].toInt() and 0x7F) shl 14) or
            ((d[off + 2].toInt() and 0x7F) shl 7) or
            (d[off + 3].toInt() and 0x7F)
}
