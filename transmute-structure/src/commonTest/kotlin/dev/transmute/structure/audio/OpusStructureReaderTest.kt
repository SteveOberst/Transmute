package dev.transmute.structure.audio

import dev.transmute.model.core.asBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpusStructureReaderTest {

    private val reader = OpusStructureReader()

    /** Build a minimal Ogg page with a single segment containing [payload]. */
    private fun oggPage(payload: ByteArray, headerType: Int = 0x02): ByteArray {
        val segCount = 1
        val segSize = payload.size
        val pageSize = 27 + segCount + segSize
        val out = ByteArray(pageSize)
        "OggS".encodeToByteArray().copyInto(out, 0)
        out[4] = 0 // version
        out[5] = headerType.toByte()
        out[14] = 1 // serial number
        out[26] = segCount.toByte()
        out[27] = segSize.toByte()
        payload.copyInto(out, 28)
        return out
    }

    /** OpusHead packet: "OpusHead" + version(1) + channel count(1) + padding. */
    private fun opusHeadPacket(): ByteArray {
        val prefix = "OpusHead".encodeToByteArray()
        return prefix + byteArrayOf(
            0x01,       // version
            0x02,       // channel count
            0x00, 0x00, // pre-skip
            0x80.toByte(), 0xBB.toByte(), 0x00, 0x00, // sample rate = 48000 LE
            0x00, 0x00, // output gain
            0x00,       // mapping family
        )
    }

    private fun minimalOggOpus(): ByteArray = oggPage(opusHeadPacket())

    @Test
    fun readParsesPages() {
        val opus = reader.read(minimalOggOpus().asBytes())
        assertTrue(opus.pages.isNotEmpty())
    }

    @Test
    fun roundTripPreservesSize() {
        val bytes = minimalOggOpus().asBytes()
        val opus = reader.read(bytes)
        val written = opus.toBytes()
        assertEquals(bytes.size, written.size)
    }
}
