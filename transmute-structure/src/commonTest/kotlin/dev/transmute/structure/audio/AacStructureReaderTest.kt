package dev.transmute.structure.audio

import dev.transmute.model.core.asBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AacStructureReaderTest {

    private val reader = AacStructureReader()

    /**
     * Minimal AAC ADTS frame: sync word 0xFFF + header bits.
     * ADTS header is 7 bytes when no CRC (protection absent = 1).
     */
    private fun minimalAac(): ByteArray {
        // Byte 0: 0xFF
        // Byte 1: 0xF1 = sync(4 bits) + ID=0(MPEG-4) + layer=00 + protection_absent=1
        // Byte 2: profile(2)=01(AAC-LC) + sampling_freq_index(4)=0100(44100) + private(1)=0 + channel_config_hi(1)=0 → 0x50
        // Byte 3: channel_config_lo(2)=10 + original(1)=0 + home(1)=0 + copyright_id(1)=0 + copyright_start(1)=0 + frame_length_hi(2)=0 → 0x80
        // Byte 4-5: frame_length (13 bits total, value=7 for header-only), buffer_fullness (11 bits)
        // Let frame_length = 7 → split across bytes 3..5
        //   byte3 bits [1:0] = 0, byte4 = frame_len[10:3] = 0, byte5_hi = frame_len[2:0]<<5 | buffulness[10:6]
        // For simplicity, just construct a valid sync word prefix
        return byteArrayOf(
            0xFF.toByte(), 0xF1.toByte(),  // sync + header
            0x50.toByte(), 0x80.toByte(),  // profile/freq/channel
            0x00, 0x1C.toByte(), 0x00,      // frame length embedded
        )
    }

    @Test
    fun canReadAcceptsAdts() {
        assertTrue(reader.canRead(minimalAac().asBytes()))
    }

    @Test
    fun canReadRejectsGarbage() {
        assertFalse(reader.canRead(ByteArray(16).asBytes()))
    }

    @Test
    fun canReadRejectsTooShort() {
        assertFalse(reader.canRead(ByteArray(1).asBytes()))
    }

    @Test
    fun readWrapsData() {
        val aac = reader.read(minimalAac().asBytes())
        assertEquals(minimalAac().size, aac.data.size)
    }

    @Test
    fun roundTripPreservesSize() {
        val bytes = minimalAac().asBytes()
        val aac = reader.read(bytes)
        val written = aac.toBytes()
        assertEquals(bytes.size, written.size)
    }
}
