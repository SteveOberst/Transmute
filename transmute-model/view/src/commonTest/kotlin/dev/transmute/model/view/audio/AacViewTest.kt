package dev.transmute.model.view.audio

import dev.transmute.model.core.Bytes
import dev.transmute.model.structure.audio.*
import dev.transmute.model.view.StructureView
import dev.transmute.model.view.MutableStructureView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AacViewTest {

    // --- Helpers ---

    private fun minimalAac(
        data: Bytes = Bytes(ByteArray(64)),
    ) = Aac(data)

    // --- view() tests ---

    @Test
    fun viewReturnsCorrectData() {
        val file = minimalAac()
        assertEquals(file.data, file.inspect().data)
    }

    @Test
    fun viewReturnsNullComputedForGarbageData() {
        val view = minimalAac(Bytes(ByteArray(4))).inspect()
        assertNull(view.firstFrameHeader)
        assertNull(view.sampleRate)
        assertNull(view.channels)
        assertNull(view.profile)
    }

    // --- edit() tests ---

    @Test
    fun editNoOpRoundTrip() {
        val original = minimalAac()
        assertEquals(original, original.edit { })
    }

    @Test
    fun editReplacesData() {
        val original = minimalAac()
        val newData = Bytes(ByteArray(256) { 0x42.toByte() })
        val edited = original.edit { data = newData }
        assertEquals(256, edited.data.data.size)
    }

    // --- Type hierarchy tests ---

    @Test
    fun viewIsFileView() {
        assertIs<StructureView<Aac>>(minimalAac().inspect())
    }

    @Test
    fun viewIsAacView() {
        assertIs<AacView>(minimalAac().inspect())
    }

    @Test
    fun mutableViewIsMutableStructureView() {
        val view = MutableAacView(minimalAac())
        assertIs<MutableStructureView<Aac>>(view)
        assertIs<AacView>(view)
    }

    // --- Consistency tests ---

    @Test
    fun viewAndFileReturnSameValues() {
        val file = minimalAac()
        val view = file.inspect()
        assertEquals(file.data, view.data)
        assertEquals(file.firstFrameHeader, view.firstFrameHeader)
        assertEquals(file.sampleRate, view.sampleRate)
        assertEquals(file.channels, view.channels)
        assertEquals(file.profile, view.profile)
    }
}
