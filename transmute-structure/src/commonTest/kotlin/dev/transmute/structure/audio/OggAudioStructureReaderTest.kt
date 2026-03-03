package dev.transmute.structure.audio

import dev.transmute.model.core.asBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OggAudioStructureReaderTest {

    private val reader = OggAudioStructureReader()

    /**
     * Build a minimal Ogg page with a single segment containing [payload].
     *
     * Ogg page layout (27 + segCount + data):
     * ```
     * OggS | version(1) | headerType(1) | granulePosition(8) | serialNumber(4)
     * | pageSequence(4) | crc(4) | segCount(1) | segTable(segCount) | data(...)
     * ```
     */
    private fun oggPage(payload: ByteArray, headerType: Int = 0x02 /* BOS */): ByteArray {
        val segCount = 1
        val segSize = payload.size
        val pageSize = 27 + segCount + segSize
        val out = ByteArray(pageSize)
        // Capture pattern "OggS"
        "OggS".encodeToByteArray().copyInto(out, 0)
        out[4] = 0 // version
        out[5] = headerType.toByte()
        // granule position (8 bytes) = 0
        // serial number (4 bytes) = 1
        out[14] = 1
        // page sequence (4 bytes) = 0
        // CRC (4 bytes) = 0 (not validated by reader)
        out[26] = segCount.toByte()
        out[27] = segSize.toByte()
        payload.copyInto(out, 28)
        return out
    }

    /** Vorbis identification header: 0x01 + "vorbis" + padding. */
    private fun vorbisIdPacket(): ByteArray {
        val prefix = byteArrayOf(0x01) + "vorbis".encodeToByteArray()
        // Pad to at least a few more bytes for a valid-ish ID header
        return prefix + ByteArray(23) // total ~30 bytes
    }

    private fun minimalOggVorbis(): ByteArray = oggPage(vorbisIdPacket())

    @Test
    fun readParsesPages() {
        val ogg = reader.read(minimalOggVorbis().asBytes())
        assertTrue(ogg.pages.isNotEmpty())
    }

    @Test
    fun roundTripPreservesSize() {
        val bytes = minimalOggVorbis().asBytes()
        val ogg = reader.read(bytes)
        val written = ogg.toBytes()
        assertEquals(bytes.size, written.size)
    }
}
