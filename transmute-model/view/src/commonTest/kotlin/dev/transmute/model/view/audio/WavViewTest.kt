package dev.transmute.model.view.audio

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.BitsPerSample
import dev.transmute.model.core.Channels
import dev.transmute.model.core.Hertz
import dev.transmute.model.identify.RiffChunkId
import dev.transmute.model.structure.audio.*
import dev.transmute.model.structure.common.RiffChunk
import dev.transmute.model.view.StructureView
import dev.transmute.model.view.MutableStructureView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WavViewTest {

    // --- Helpers ---

    /**
     * Build a minimal fmt chunk with PCM 16-bit stereo 44100 Hz.
     * The raw data layout (16 bytes):
     *   audioFormat(2) + numChannels(2) + sampleRate(4) +
     *   byteRate(4) + blockAlign(2) + bitsPerSample(2)
     */
    private fun fmtChunkData(
        audioFormat: UShort = 1u, // PCM
        numChannels: UShort = 2u,
        sampleRate: UInt = 44100u,
        bitsPerSample: UShort = 16u,
    ): ByteArray {
        val blockAlign = (numChannels.toInt() * bitsPerSample.toInt() / 8).toUShort()
        val byteRate = sampleRate * blockAlign
        val data = ByteArray(16)
        // Little-endian encoding
        fun ByteArray.putU16(offset: Int, v: UShort) {
            this[offset] = (v.toInt() and 0xFF).toByte()
            this[offset + 1] = (v.toInt() shr 8).toByte()
        }
        fun ByteArray.putU32(offset: Int, v: UInt) {
            this[offset] = (v.toInt() and 0xFF).toByte()
            this[offset + 1] = (v.toInt() shr 8 and 0xFF).toByte()
            this[offset + 2] = (v.toInt() shr 16 and 0xFF).toByte()
            this[offset + 3] = (v.toInt() shr 24 and 0xFF).toByte()
        }
        data.putU16(0, audioFormat)
        data.putU16(2, numChannels)
        data.putU32(4, sampleRate)
        data.putU32(8, byteRate)
        data.putU16(12, blockAlign)
        data.putU16(14, bitsPerSample)
        return data
    }

    private fun fmtChunk(
        sampleRate: UInt = 44100u,
        channels: UShort = 2u,
        bitsPerSample: UShort = 16u,
    ) = RiffChunk(
        id = RiffChunkId("fmt "),
        size = 16u,
        data = Bytes(fmtChunkData(sampleRate = sampleRate, numChannels = channels, bitsPerSample = bitsPerSample)),
    )

    private fun dataChunk(size: Int = 1024) = RiffChunk(
        id = RiffChunkId("data"),
        size = size.toUInt(),
        data = Bytes(ByteArray(size)),
    )

    private fun minimalWav(
        children: List<RiffChunk> = listOf(fmtChunk(), dataChunk()),
    ) = Wav(
        riff = RiffChunk(
            id = RiffChunkId("RIFF"),
            size = 4u,
            formType = RiffChunkId("WAVE"),
            children = children,
        ),
    )

    // --- view() tests ---

    @Test
    fun viewReturnsCorrectRiff() {
        val file = minimalWav()
        assertEquals(file.riff, file.inspect().riff)
    }

    @Test
    fun viewReturnsChunks() {
        val file = minimalWav()
        assertEquals(2, file.inspect().chunks.size)
    }

    @Test
    fun viewReturnsFmt() {
        val view = minimalWav().inspect()
        assertNotNull(view.fmt)
        assertEquals(Hertz(44100), view.sampleRate)
        assertEquals(Channels(2), view.channels)
        assertEquals(BitsPerSample(16), view.bitsPerSample)
    }

    @Test
    fun viewReturnsDataChunk() {
        assertNotNull(minimalWav().inspect().dataChunk)
    }

    @Test
    fun viewReturnsNullFmtWhenAbsent() {
        val view = minimalWav(children = listOf(dataChunk())).inspect()
        assertNull(view.fmt)
        assertNull(view.sampleRate)
        assertNull(view.channels)
        assertNull(view.bitsPerSample)
        assertNull(view.audioFormat)
    }

    @Test
    fun viewReturnsAudioFormat() {
        val view = minimalWav().inspect()
        assertEquals(WavAudioFormat.Pcm, view.audioFormat)
    }

    // --- edit() tests ---

    @Test
    fun editNoOpRoundTrip() {
        val original = minimalWav()
        assertEquals(original, original.edit { })
    }

    @Test
    fun editMutatesRiff() {
        val original = minimalWav()
        val edited = original.edit {
            riff = riff.copy(children = riff.children.filter { it.id.value == "fmt " })
        }
        assertNull(edited.dataChunk)
        assertNotNull(edited.fmt)
    }

    @Test
    fun editRemovesFmt() {
        val original = minimalWav()
        val edited = original.edit {
            riff = riff.copy(children = riff.children.filter { it.id.value != "fmt " })
        }
        assertNull(edited.fmt)
        assertNull(edited.sampleRate)
    }

    // --- Type hierarchy tests ---

    @Test
    fun viewIsFileView() {
        assertIs<StructureView<Wav>>(minimalWav().inspect())
    }

    @Test
    fun viewIsWavView() {
        assertIs<WavView>(minimalWav().inspect())
    }

    @Test
    fun mutableViewIsMutableStructureView() {
        val view = MutableWavView(minimalWav())
        assertIs<MutableStructureView<Wav>>(view)
        assertIs<WavView>(view)
    }

    // --- Consistency tests ---

    @Test
    fun viewAndFileReturnSameValues() {
        val file = minimalWav()
        val view = file.inspect()
        assertEquals(file.riff, view.riff)
        // fmt/fmtChunk/dataChunk use Bytes internally (ByteArray reference equality)
        // so we compare derived primitive values instead
        assertEquals(file.sampleRate, view.sampleRate)
        assertEquals(file.channels, view.channels)
        assertEquals(file.bitsPerSample, view.bitsPerSample)
        assertEquals(file.audioFormat, view.audioFormat)
        assertEquals(file.fmt?.audioFormat, view.fmt?.audioFormat)
        assertEquals(file.fmt?.numChannels, view.fmt?.numChannels)
        assertEquals(file.fmt?.sampleRate, view.fmt?.sampleRate)
        assertEquals(file.fmt?.byteRate, view.fmt?.byteRate)
        assertEquals(file.fmt?.blockAlign, view.fmt?.blockAlign)
        assertEquals(file.fmt?.bitsPerSample, view.fmt?.bitsPerSample)
    }
}
