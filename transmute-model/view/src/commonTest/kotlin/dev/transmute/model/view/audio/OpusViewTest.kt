package dev.transmute.model.view.audio

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.Hertz
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

class OpusViewTest {

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

    private fun minimalOpus(
        pages: List<OggPage> = listOf(oggPage()),
    ) = Opus(pages)

    // --- view() tests ---

    @Test
    fun viewReturnsCorrectPages() {
        val file = minimalOpus()
        assertEquals(file.pages, file.inspect().pages)
    }

    @Test
    fun viewReturnsStreamSerialNumbers() {
        val file = minimalOpus(listOf(oggPage(serial = 1), oggPage(serial = 2)))
        assertEquals(2, file.inspect().streamSerialNumbers.size)
    }

    @Test
    fun viewReturnsNullOpusIdForNonOpusData() {
        val view = minimalOpus().inspect()
        assertNull(view.opusIdentification)
        assertNull(view.channels)
    }

    @Test
    fun viewDefaultsSampleRateTo48kHz() {
        // Opus spec: sample rate defaults to 48000 when no identification header
        assertEquals(Hertz(48000), minimalOpus().inspect().sampleRate)
    }

    @Test
    fun viewReturnsDefaultPreSkipSamples() {
        assertEquals(0, minimalOpus().inspect().preSkipSamples)
    }

    @Test
    fun viewReturnsDefaultOutputGain() {
        assertEquals(0.toShort(), minimalOpus().inspect().outputGain)
    }

    @Test
    fun viewReturnsEmptySerialNumbersForNoPages() {
        assertTrue(minimalOpus(emptyList()).inspect().streamSerialNumbers.isEmpty())
    }

    // --- edit() tests ---

    @Test
    fun editNoOpRoundTrip() {
        val original = minimalOpus()
        assertEquals(original, original.edit { })
    }

    @Test
    fun editFiltersBySerial() {
        val original = minimalOpus(listOf(oggPage(serial = 1), oggPage(serial = 2)))
        val edited = original.edit {
            pages = pages.filter { it.serialNumber == OggSerialNumber(1) }
        }
        assertEquals(1, edited.pages.size)
    }

    @Test
    fun editAddsPage() {
        val original = minimalOpus(listOf(oggPage()))
        val edited = original.edit {
            pages = pages + oggPage(serial = 2)
        }
        assertEquals(2, edited.pages.size)
    }

    // --- Type hierarchy tests ---

    @Test
    fun viewIsFileView() {
        assertIs<StructureView<Opus>>(minimalOpus().inspect())
    }

    @Test
    fun viewIsOpusView() {
        assertIs<OpusView>(minimalOpus().inspect())
    }

    @Test
    fun mutableViewIsMutableStructureView() {
        val view = MutableOpusView(minimalOpus())
        assertIs<MutableStructureView<Opus>>(view)
        assertIs<OpusView>(view)
    }

    // --- Consistency tests ---

    @Test
    fun viewAndFileReturnSameValues() {
        val file = minimalOpus(listOf(oggPage(serial = 1), oggPage(serial = 2)))
        val view = file.inspect()
        assertEquals(file.pages, view.pages)
        assertEquals(file.streamSerialNumbers, view.streamSerialNumbers)
        assertEquals(file.opusIdentification, view.opusIdentification)
        assertEquals(file.sampleRate, view.sampleRate)
        assertEquals(file.channels, view.channels)
        assertEquals(file.preSkipSamples, view.preSkipSamples)
        assertEquals(file.outputGain, view.outputGain)
    }
}
