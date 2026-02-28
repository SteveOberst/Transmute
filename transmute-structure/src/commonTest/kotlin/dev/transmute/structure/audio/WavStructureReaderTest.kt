package dev.transmute.structure.audio

import dev.transmute.model.core.asBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WavStructureReaderTest {

    private val reader = WavStructureReader()

    /**
     * Minimal WAV: RIFF header + "WAVE" + fmt chunk (16 B PCM) + data chunk (2 B body).
     */
    private fun minimalWav(): ByteArray {
        // fmt sub-chunk: PCM, 1 channel, 8000 Hz, 8 bit, block align 1
        val fmtData = byteArrayOf(
            0x01, 0x00,                         // audioFormat = 1 (PCM)
            0x01, 0x00,                         // numChannels = 1
            0x40, 0x1F, 0x00, 0x00,             // sampleRate = 8000
            0x40, 0x1F, 0x00, 0x00,             // byteRate = 8000
            0x01, 0x00,                         // blockAlign = 1
            0x08, 0x00,                         // bitsPerSample = 8
        )
        // data sub-chunk: 2 bytes of silence
        val sampleData = byteArrayOf(0x80.toByte(), 0x80.toByte())

        val subChunksSize = 8 + fmtData.size + 8 + sampleData.size // chunk headers (8 each) + body
        val fileSize = 4 + subChunksSize // "WAVE" + sub-chunks
        val out = ByteArray(12 + subChunksSize)
        var p = 0
        // RIFF header
        "RIFF".encodeToByteArray().copyInto(out, p); p += 4
        writeU32LE(out, p, fileSize); p += 4
        "WAVE".encodeToByteArray().copyInto(out, p); p += 4
        // fmt chunk
        "fmt ".encodeToByteArray().copyInto(out, p); p += 4
        writeU32LE(out, p, fmtData.size); p += 4
        fmtData.copyInto(out, p); p += fmtData.size
        // data chunk
        "data".encodeToByteArray().copyInto(out, p); p += 4
        writeU32LE(out, p, sampleData.size); p += 4
        sampleData.copyInto(out, p)
        return out
    }

    private fun writeU32LE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    @Test
    fun canReadAcceptsValidWav() {
        assertTrue(reader.canRead(minimalWav().asBytes()))
    }

    @Test
    fun canReadRejectsGarbage() {
        assertFalse(reader.canRead(ByteArray(16).asBytes()))
    }

    @Test
    fun canReadRejectsTooShort() {
        assertFalse(reader.canRead(ByteArray(6).asBytes()))
    }

    @Test
    fun readParsesChildren() {
        val wav = reader.read(minimalWav().asBytes())
        // Should have at least fmt and data chunks
        assertTrue(wav.riff.children.size >= 2)
    }

    @Test
    fun readHasFmtChunk() {
        val wav = reader.read(minimalWav().asBytes())
        val fmtChunk = wav.riff.children.find { it.id.value == "fmt " }
        assertTrue(fmtChunk != null)
    }

    @Test
    fun roundTripPreservesSize() {
        val bytes = minimalWav().asBytes()
        val wav = reader.read(bytes)
        val written = wav.toBytes()
        assertEquals(bytes.size, written.size)
    }
}
