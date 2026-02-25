package dev.transmute.model.view.audio

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.BitsPerSample
import dev.transmute.model.core.Channels
import dev.transmute.model.core.Hertz
import dev.transmute.model.structure.audio.*
import dev.transmute.model.view.StructureView
import dev.transmute.model.view.MutableStructureView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlacViewTest {

    // --- Helpers ---

    /**
     * Build a minimal STREAMINFO block (34 bytes).
     * Layout: minBlockSize(2) + maxBlockSize(2) + minFrameSize(3) +
     *   maxFrameSize(3) + sampleRate:channels:bps:totalSamples (8 bytes packed) +
     *   md5(16 bytes)
     *
     * For simplicity: 44100 Hz, 2 channels, 16 bits/sample, 0 total samples.
     */
    private fun streamInfoData(
        sampleRate: Int = 44100,
        channels: Int = 2,
        bitsPerSample: Int = 16,
    ): ByteArray {
        val data = ByteArray(34)
        // Bytes 0-1: minBlockSize = 4096
        data[0] = 0x10.toByte(); data[1] = 0x00.toByte()
        // Bytes 2-3: maxBlockSize = 4096
        data[2] = 0x10.toByte(); data[3] = 0x00.toByte()
        // Bytes 4-6: minFrameSize = 0
        // Bytes 7-9: maxFrameSize = 0
        // Bytes 10-13: packed sampleRate(20) | channels-1(3) | bps-1(5) | totalSamples high(4)
        val packed = (sampleRate.toLong() shl 12) or
            ((channels - 1).toLong() shl 9) or
            ((bitsPerSample - 1).toLong() shl 4)
        data[10] = (packed shr 24 and 0xFF).toByte()
        data[11] = (packed shr 16 and 0xFF).toByte()
        data[12] = (packed shr 8 and 0xFF).toByte()
        data[13] = (packed and 0xFF).toByte()
        // Bytes 14-17: totalSamples low(32) = 0
        // Bytes 18-33: MD5 = zeros
        return data
    }

    private fun streamInfoBlock(
        sampleRate: Int = 44100,
        channels: Int = 2,
        bitsPerSample: Int = 16,
    ) = FlacMetadataBlock(
        type = FlacMetadataBlockType.StreamInfo,
        isLast = false,
        data = Bytes(streamInfoData(sampleRate, channels, bitsPerSample)),
    )

    private fun vorbisCommentBlock() = FlacMetadataBlock(
        type = FlacMetadataBlockType.VorbisComment,
        isLast = false,
        data = Bytes(ByteArray(8)),
    )

    private fun pictureBlock() = FlacMetadataBlock(
        type = FlacMetadataBlockType.Picture,
        isLast = true,
        data = Bytes(ByteArray(32)),
    )

    private fun minimalFlac(
        metadataBlocks: List<FlacMetadataBlock> = listOf(streamInfoBlock()),
        audioData: Bytes = Bytes(ByteArray(256)),
    ) = Flac(metadataBlocks, audioData)

    // --- view() tests ---

    @Test
    fun viewReturnsCorrectFields() {
        val file = minimalFlac()
        val view = file.inspect()
        assertEquals(file.metadataBlocks, view.metadataBlocks)
        assertEquals(file.audioData, view.audioData)
    }

    @Test
    fun viewReturnsStreamInfo() {
        val view = minimalFlac().inspect()
        assertNotNull(view.streamInfoBlock)
        assertNotNull(view.streamInfo)
        assertEquals(Hertz(44100), view.sampleRate)
        assertEquals(Channels(2), view.channels)
        assertEquals(BitsPerSample(16), view.bitsPerSample)
    }

    @Test
    fun viewReturnsNullStreamInfoWhenAbsent() {
        val view = minimalFlac(metadataBlocks = emptyList()).inspect()
        assertNull(view.streamInfoBlock)
        assertNull(view.streamInfo)
        assertNull(view.sampleRate)
    }

    @Test
    fun viewReturnsVorbisComment() {
        val view = minimalFlac(
            metadataBlocks = listOf(streamInfoBlock(), vorbisCommentBlock()),
        ).inspect()
        assertNotNull(view.vorbisCommentBlock)
    }

    @Test
    fun viewReturnsPictureBlocks() {
        val view = minimalFlac(
            metadataBlocks = listOf(streamInfoBlock(), pictureBlock(), pictureBlock()),
        ).inspect()
        assertEquals(2, view.pictureBlocks.size)
    }

    @Test
    fun viewReturnsEmptyPictureBlocksWhenAbsent() {
        assertTrue(minimalFlac().inspect().pictureBlocks.isEmpty())
    }

    // --- edit() tests ---

    @Test
    fun editNoOpRoundTrip() {
        val original = minimalFlac()
        assertEquals(original, original.edit { })
    }

    @Test
    fun editRemovesPictureBlocks() {
        val original = minimalFlac(
            metadataBlocks = listOf(streamInfoBlock(), pictureBlock()),
        )
        val edited = original.edit {
            metadataBlocks = metadataBlocks.filter { it.type != FlacMetadataBlockType.Picture }
        }
        assertTrue(edited.inspect().pictureBlocks.isEmpty())
    }

    @Test
    fun editReplacesAudioData() {
        val original = minimalFlac()
        val newAudio = Bytes(ByteArray(512) { 0xAB.toByte() })
        val edited = original.edit { audioData = newAudio }
        assertEquals(512, edited.audioData.data.size)
    }

    @Test
    fun editAddsMetadataBlock() {
        val original = minimalFlac()
        val edited = original.edit {
            metadataBlocks = metadataBlocks + vorbisCommentBlock()
        }
        assertNotNull(edited.inspect().vorbisCommentBlock)
    }

    // --- Type hierarchy tests ---

    @Test
    fun viewIsFileView() {
        assertIs<StructureView<Flac>>(minimalFlac().inspect())
    }

    @Test
    fun viewIsFlacView() {
        assertIs<FlacView>(minimalFlac().inspect())
    }

    @Test
    fun mutableViewIsMutableStructureView() {
        val view = MutableFlacView(minimalFlac())
        assertIs<MutableStructureView<Flac>>(view)
        assertIs<FlacView>(view)
    }

    // --- Consistency tests ---

    @Test
    fun viewAndFileReturnSameValues() {
        val file = minimalFlac(
            metadataBlocks = listOf(streamInfoBlock(), vorbisCommentBlock(), pictureBlock()),
        )
        val view = file.inspect()
        // Bytes-containing objects use ByteArray reference equality, so compare
        // derived primitive values instead of full object equality
        assertEquals(file.metadataBlocks.size, view.metadataBlocks.size)
        assertEquals(file.sampleRate, view.sampleRate)
        assertEquals(file.channels, view.channels)
        assertEquals(file.bitsPerSample, view.bitsPerSample)
        assertEquals(file.streamInfo?.sampleRate, view.streamInfo?.sampleRate)
        assertEquals(file.streamInfo?.channels, view.streamInfo?.channels)
        assertEquals(file.streamInfo?.bitsPerSample, view.streamInfo?.bitsPerSample)
        assertEquals(file.streamInfo?.totalSamples, view.streamInfo?.totalSamples)
        assertEquals(file.vorbisCommentBlock?.type, view.vorbisCommentBlock?.type)
        assertEquals(file.pictureBlocks.size, view.pictureBlocks.size)
    }
}
