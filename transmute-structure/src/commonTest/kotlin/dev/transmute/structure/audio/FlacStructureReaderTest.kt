package dev.transmute.structure.audio

import dev.transmute.model.core.asBytes
import dev.transmute.model.structure.audio.FlacMetadataBlockType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlacStructureReaderTest {

    private val reader = FlacStructureReader()

    /**
     * Minimal FLAC: "fLaC" marker + one STREAMINFO metadata block (34 bytes body, isLast=true).
     */
    private fun minimalFlac(): ByteArray {
        val marker = "fLaC".encodeToByteArray() // 4 bytes
        val streamInfoBody = ByteArray(34) // all zeroes - valid structurally
        val blockHeader = byteArrayOf(
            (0x80 or 0x00).toByte(), // isLast=true, type=0 (STREAMINFO)
            0x00, 0x00, 0x22,       // length = 34 (Big-Endian 24-bit)
        )
        return marker + blockHeader + streamInfoBody
    }

    @Test
    fun canReadAcceptsFlac() {
        assertTrue(reader.canRead(minimalFlac().asBytes()))
    }

    @Test
    fun canReadRejectsGarbage() {
        assertFalse(reader.canRead(ByteArray(16).asBytes()))
    }

    @Test
    fun canReadRejectsTooShort() {
        assertFalse(reader.canRead(ByteArray(2).asBytes()))
    }

    @Test
    fun readParsesStreamInfoBlock() {
        val flac = reader.read(minimalFlac().asBytes())
        assertEquals(1, flac.metadataBlocks.size)
        assertEquals(FlacMetadataBlockType.StreamInfo, flac.metadataBlocks[0].type)
        assertTrue(flac.metadataBlocks[0].isLast)
        assertEquals(34, flac.metadataBlocks[0].data.size)
    }

    @Test
    fun readCapturesAudioData() {
        // Add some dummy audio bytes after the STREAMINFO
        val base = minimalFlac()
        val audio = byteArrayOf(0x01, 0x02, 0x03)
        val full = base + audio
        val flac = reader.read(full.asBytes())
        assertEquals(3, flac.audioData.size)
    }

    @Test
    fun roundTripPreservesSize() {
        val bytes = minimalFlac().asBytes()
        val flac = reader.read(bytes)
        val written = flac.toBytes()
        assertEquals(bytes.size, written.size)
    }
}
