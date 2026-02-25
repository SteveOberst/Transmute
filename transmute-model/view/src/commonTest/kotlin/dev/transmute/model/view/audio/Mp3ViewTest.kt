package dev.transmute.model.view.audio

import dev.transmute.model.core.Bytes
import dev.transmute.model.structure.audio.*
import dev.transmute.model.view.StructureView
import dev.transmute.model.view.MutableStructureView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class Mp3ViewTest {

    // --- Helpers ---

    private fun minimalMp3(
        id3v2Tag: Bytes? = null,
        audioData: Bytes = Bytes(ByteArray(128)),
        id3v1TagData: Bytes? = null,
    ) = Mp3(id3v2Tag, audioData, id3v1TagData)

    // --- view() tests ---

    @Test
    fun viewReturnsCorrectFields() {
        val tag2 = Bytes(ByteArray(64))
        val audio = Bytes(ByteArray(128))
        val tag1 = Bytes(ByteArray(128))
        val file = minimalMp3(tag2, audio, tag1)
        val view = file.inspect()
        assertEquals(file.id3v2Tag, view.id3v2Tag)
        assertEquals(file.audioData, view.audioData)
        assertEquals(file.id3v1TagData, view.id3v1TagData)
    }

    @Test
    fun viewReturnsNullComputedForEmptyAudio() {
        val view = minimalMp3(audioData = Bytes(ByteArray(0))).inspect()
        assertNull(view.firstFrameHeader)
        assertNull(view.sampleRate)
        assertNull(view.channels)
    }

    @Test
    fun viewReturnsNullId3v1WhenAbsent() {
        assertNull(minimalMp3().inspect().id3v1Tag)
        assertNull(minimalMp3().inspect().id3v1TagData)
    }

    @Test
    fun viewReturnsNullId3v2WhenAbsent() {
        assertNull(minimalMp3().inspect().id3v2Tag)
    }

    // --- edit() tests ---

    @Test
    fun editNoOpRoundTrip() {
        val original = minimalMp3()
        assertEquals(original, original.edit { })
    }

    @Test
    fun editStripsId3v1Tag() {
        val original = minimalMp3(id3v1TagData = Bytes(ByteArray(128)))
        val edited = original.edit { id3v1TagData = null }
        assertNull(edited.id3v1TagData)
    }

    @Test
    fun editStripsId3v2Tag() {
        val original = minimalMp3(id3v2Tag = Bytes(ByteArray(64)))
        val edited = original.edit { id3v2Tag = null }
        assertNull(edited.id3v2Tag)
    }

    @Test
    fun editReplacesAudioData() {
        val original = minimalMp3()
        val newAudio = Bytes(ByteArray(512) { 0xAB.toByte() })
        val edited = original.edit { audioData = newAudio }
        assertEquals(512, edited.audioData.data.size)
    }

    // --- Type hierarchy tests ---

    @Test
    fun viewIsFileView() {
        assertIs<StructureView<Mp3>>(minimalMp3().inspect())
    }

    @Test
    fun viewIsMp3View() {
        assertIs<Mp3View>(minimalMp3().inspect())
    }

    @Test
    fun mutableViewIsMutableStructureView() {
        val view = MutableMp3View(minimalMp3())
        assertIs<MutableStructureView<Mp3>>(view)
        assertIs<Mp3View>(view)
    }

    // --- Consistency tests ---

    @Test
    fun viewAndFileReturnSameValues() {
        val file = minimalMp3(id3v2Tag = Bytes(ByteArray(32)), id3v1TagData = Bytes(ByteArray(128)))
        val view = file.inspect()
        assertEquals(file.id3v2Tag, view.id3v2Tag)
        assertEquals(file.audioData, view.audioData)
        assertEquals(file.id3v1TagData, view.id3v1TagData)
        assertEquals(file.firstFrameHeader, view.firstFrameHeader)
        assertEquals(file.sampleRate, view.sampleRate)
        assertEquals(file.channels, view.channels)
    }
}
