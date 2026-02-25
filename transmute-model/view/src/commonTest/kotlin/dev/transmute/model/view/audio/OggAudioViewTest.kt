package dev.transmute.model.view.audio

import dev.transmute.model.core.Bytes
import dev.transmute.model.structure.audio.*
import dev.transmute.model.structure.common.OggPage
import dev.transmute.model.structure.common.OggSerialNumber
import dev.transmute.model.view.StructureView
import dev.transmute.model.view.MutableStructureView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OggAudioViewTest {

    // --- Helpers ---

    private fun oggPage(
        serial: Int = 1,
        pageSeq: UInt = 0u,
        data: ByteArray = ByteArray(64),
    ) = OggPage(
        headerType = 0u,
        granulePosition = 0L,
        serialNumber = OggSerialNumber(serial),
        pageSequence = pageSeq,
        crc = 0u,
        segmentTable = Bytes(ByteArray(1) { data.size.toByte() }),
        data = Bytes(data),
    )

    private fun minimalOgg(
        pages: List<OggPage> = listOf(oggPage()),
    ) = OggAudio(pages)

    // --- view() tests ---

    @Test
    fun viewReturnsCorrectPages() {
        val file = minimalOgg()
        assertEquals(file.pages, file.inspect().pages)
    }

    @Test
    fun viewReturnsStreamSerialNumbers() {
        val file = minimalOgg(listOf(oggPage(serial = 1), oggPage(serial = 2)))
        val view = file.inspect()
        assertEquals(2, view.streamSerialNumbers.size)
    }

    @Test
    fun viewReturnsNullVorbisForNonVorbisData() {
        val view = minimalOgg().inspect()
        assertNull(view.vorbisIdentification)
        assertNull(view.sampleRate)
        assertNull(view.channels)
    }

    @Test
    fun viewReturnsEmptySerialNumbersForNoPages() {
        assertTrue(minimalOgg(emptyList()).inspect().streamSerialNumbers.isEmpty())
    }

    // --- edit() tests ---

    @Test
    fun editNoOpRoundTrip() {
        val original = minimalOgg()
        assertEquals(original, original.edit { })
    }

    @Test
    fun editFiltersBySerial() {
        val original = minimalOgg(listOf(oggPage(serial = 1), oggPage(serial = 2)))
        val edited = original.edit {
            pages = pages.filter { it.serialNumber == OggSerialNumber(1) }
        }
        assertEquals(1, edited.pages.size)
        assertEquals(listOf(OggSerialNumber(1)), edited.streamSerialNumbers)
    }

    @Test
    fun editAddsPage() {
        val original = minimalOgg(listOf(oggPage(serial = 1)))
        val edited = original.edit {
            pages = pages + oggPage(serial = 2)
        }
        assertEquals(2, edited.pages.size)
    }

    // --- Type hierarchy tests ---

    @Test
    fun viewIsFileView() {
        assertIs<StructureView<OggAudio>>(minimalOgg().inspect())
    }

    @Test
    fun viewIsOggAudioView() {
        assertIs<OggAudioView>(minimalOgg().inspect())
    }

    @Test
    fun mutableViewIsMutableStructureView() {
        val view = MutableOggAudioView(minimalOgg())
        assertIs<MutableStructureView<OggAudio>>(view)
        assertIs<OggAudioView>(view)
    }

    // --- Consistency tests ---

    @Test
    fun viewAndFileReturnSameValues() {
        val file = minimalOgg(listOf(oggPage(serial = 1), oggPage(serial = 2)))
        val view = file.inspect()
        assertEquals(file.pages, view.pages)
        assertEquals(file.streamSerialNumbers, view.streamSerialNumbers)
        assertEquals(file.vorbisIdentification, view.vorbisIdentification)
        assertEquals(file.sampleRate, view.sampleRate)
        assertEquals(file.channels, view.channels)
    }
}
