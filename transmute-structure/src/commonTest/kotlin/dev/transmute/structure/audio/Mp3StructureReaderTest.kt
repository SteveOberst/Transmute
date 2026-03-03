package dev.transmute.structure.audio

import dev.transmute.model.core.asBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Mp3StructureReaderTest {

    private val reader = Mp3StructureReader()

    /**
     * Minimal MP3 with MPEG sync word.
     * Sync = 0xFF 0xFB (MPEG1 Layer3) + 2 more header bytes + a few dummy frame bytes.
     */
    private fun minimalMp3SyncWord(): ByteArray {
        // 0xFF 0xFB = sync, MPEG1, Layer III, no CRC
        // 0x90 0x00 = 128kbps, 44100Hz, stereo (approx, just for canRead)
        return byteArrayOf(
            0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // dummy frame bytes
        )
    }

    /** Minimal MP3 with ID3v2 tag. */
    private fun minimalMp3Id3v2(): ByteArray {
        // ID3v2 header: "ID3" + version 2.3 + flags 0 + size (syncsafe) = 0
        val id3Header = byteArrayOf(
            0x49, 0x44, 0x33, // "ID3"
            0x03, 0x00,       // version 2.3
            0x00,             // flags
            0x00, 0x00, 0x00, 0x00, // size = 0 (syncsafe)
        )
        // After the empty ID3v2, we need an MPEG sync word
        val frame = byteArrayOf(
            0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00,
            0x00, 0x00, 0x00, 0x00,
        )
        return id3Header + frame
    }

    @Test
    fun readSyncWordHasNoId3v2() {
        val mp3 = reader.read(minimalMp3SyncWord().asBytes())
        assertNull(mp3.id3v2Tag)
        assertTrue(mp3.audioData.size > 0)
    }

    @Test
    fun readId3v2ParsesTag() {
        val mp3 = reader.read(minimalMp3Id3v2().asBytes())
        assertNotNull(mp3.id3v2Tag)
        assertEquals(10, mp3.id3v2Tag!!.size) // 10-byte ID3v2 header with size=0
    }

    @Test
    fun roundTripPreservesSize() {
        val bytes = minimalMp3SyncWord().asBytes()
        val mp3 = reader.read(bytes)
        val written = mp3.toBytes()
        assertEquals(bytes.size, written.size)
    }
}
