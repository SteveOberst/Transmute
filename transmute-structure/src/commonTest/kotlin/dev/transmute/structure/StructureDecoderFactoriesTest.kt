package dev.transmute.structure

import dev.transmute.audio.AudioFormat
import dev.transmute.image.ImageFormat
import dev.transmute.model.core.asBytes
import dev.transmute.model.structure.image.toStructure
import dev.transmute.structure.audio.WavStructureReader
import dev.transmute.structure.image.PngStructureReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for [rawDecoderFor] and [structureDecoderFor] factory functions,
 * and a smoke-test that the pre-built [DefaultStructureDecoders] wire up correctly.
 *
 * These tests focus on the synchronous [sniff] / [decodableFormats] contract
 * because the `decode` path simply delegates to the wrapped [StructureReader.read],
 * which has its own per-format tests.
 */
class StructureDecoderFactoriesTest {

    // ── Minimal binary fixtures ───────────────────────────────────────────────

    private fun minimalWav(): ByteArray {
        val fmtData = byteArrayOf(
            0x01, 0x00,             // audioFormat = 1 (PCM)
            0x01, 0x00,             // numChannels = 1
            0x40, 0x1F, 0x00, 0x00, // sampleRate = 8000
            0x40, 0x1F, 0x00, 0x00, // byteRate = 8000
            0x01, 0x00,             // blockAlign = 1
            0x08, 0x00,             // bitsPerSample = 8
        )
        val sampleData = byteArrayOf(0x80.toByte(), 0x80.toByte())
        val subChunksSize = 8 + fmtData.size + 8 + sampleData.size
        val fileSize = 4 + subChunksSize
        val out = ByteArray(12 + subChunksSize)
        var p = 0
        "RIFF".encodeToByteArray().copyInto(out, p); p += 4
        writeU32LE(out, p, fileSize); p += 4
        "WAVE".encodeToByteArray().copyInto(out, p); p += 4
        "fmt ".encodeToByteArray().copyInto(out, p); p += 4
        writeU32LE(out, p, fmtData.size); p += 4
        fmtData.copyInto(out, p); p += fmtData.size
        "data".encodeToByteArray().copyInto(out, p); p += 4
        writeU32LE(out, p, sampleData.size); p += 4
        sampleData.copyInto(out, p)
        return out
    }

    // PNG signature + IHDR + IEND (same fixture used by PngStructureReaderTest).
    private fun minimalPng(): ByteArray {
        val sig = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val ihdrLen  = byteArrayOf(0x00, 0x00, 0x00, 0x0D)
        val ihdrType = "IHDR".encodeToByteArray()
        val ihdrData = byteArrayOf(
            0x00, 0x00, 0x00, 0x01, // width = 1
            0x00, 0x00, 0x00, 0x01, // height = 1
            0x08,                   // bit depth = 8
            0x02,                   // color type = RGB
            0x00, 0x00, 0x00,       // compression, filter, interlace
        )
        val ihdrCrc  = byteArrayOf(0x00, 0x00, 0x00, 0x00)
        val iendLen  = byteArrayOf(0x00, 0x00, 0x00, 0x00)
        val iendType = "IEND".encodeToByteArray()
        val iendCrc  = byteArrayOf(0xAE.toByte(), 0x42, 0x60, 0x82.toByte())
        return sig + ihdrLen + ihdrType + ihdrData + ihdrCrc + iendLen + iendType + iendCrc
    }

    private fun writeU32LE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset    ] = (value         and 0xFF).toByte()
        buf[offset + 1] = ((value shr  8) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    // ── rawDecoderFor ─────────────────────────────────────────────────────────

    @Test
    fun rawDecoderForReportsCorrectDecodableFormats() {
        val decoder = rawDecoderFor(AudioFormat.Wav, WavStructureReader())
        assertEquals(setOf(AudioFormat.Wav), decoder.decodableFormats)
    }

    @Test
    fun rawDecoderForSniffAcceptsMatchingBytes() {
        val decoder = rawDecoderFor(AudioFormat.Wav, WavStructureReader())
        assertEquals(AudioFormat.Wav, decoder.sniff(minimalWav().asBytes()))
    }

    @Test
    fun rawDecoderForSniffRejectsGarbage() {
        val decoder = rawDecoderFor(AudioFormat.Wav, WavStructureReader())
        assertNull(decoder.sniff(ByteArray(16).asBytes()))
    }

    @Test
    fun rawDecoderForSniffRejectsDifferentFormat() {
        val decoder = rawDecoderFor(AudioFormat.Wav, WavStructureReader())
        assertNull(decoder.sniff(minimalPng().asBytes()))
    }

    // ── structureDecoderFor ───────────────────────────────────────────────────

    @Test
    fun structureDecoderForReportsCorrectDecodableFormats() {
        val decoder = structureDecoderFor(ImageFormat.Png, PngStructureReader()) { toStructure() }
        assertEquals(setOf(ImageFormat.Png), decoder.decodableFormats)
    }

    @Test
    fun structureDecoderForSniffAcceptsMatchingBytes() {
        val decoder = structureDecoderFor(ImageFormat.Png, PngStructureReader()) { toStructure() }
        assertEquals(ImageFormat.Png, decoder.sniff(minimalPng().asBytes()))
    }

    @Test
    fun structureDecoderForSniffRejectsGarbage() {
        val decoder = structureDecoderFor(ImageFormat.Png, PngStructureReader()) { toStructure() }
        assertNull(decoder.sniff(ByteArray(16).asBytes()))
    }

    @Test
    fun structureDecoderForSniffRejectsDifferentFormat() {
        val decoder = structureDecoderFor(ImageFormat.Png, PngStructureReader()) { toStructure() }
        assertNull(decoder.sniff(minimalWav().asBytes()))
    }

    // ── DefaultStructureDecoders sanity checks ────────────────────────────────

    @Test
    fun defaultWavRawDecoderSniffsWav() {
        assertEquals(AudioFormat.Wav, DefaultStructureDecoders.wavRaw.sniff(minimalWav().asBytes()))
    }

    @Test
    fun defaultWavRawDecoderRejectsNonWav() {
        assertNull(DefaultStructureDecoders.wavRaw.sniff(minimalPng().asBytes()))
    }

    @Test
    fun defaultPngDecoderSniffsPng() {
        assertEquals(ImageFormat.Png, DefaultStructureDecoders.png.sniff(minimalPng().asBytes()))
    }

    @Test
    fun defaultPngDecoderRejectsNonPng() {
        assertNull(DefaultStructureDecoders.png.sniff(minimalWav().asBytes()))
    }
}
