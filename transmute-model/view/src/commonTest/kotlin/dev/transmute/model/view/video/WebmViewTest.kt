package dev.transmute.model.view.video

import dev.transmute.model.core.Bytes
import dev.transmute.model.structure.common.EbmlElement
import dev.transmute.model.structure.video.*
import dev.transmute.model.view.StructureView
import dev.transmute.model.view.MutableStructureView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WebmViewTest {

    // --- Helpers ---

    private fun ebmlHeaderElement() = EbmlElement(
        id = MatroskaIds.EBML,
        data = Bytes(ByteArray(0)),
        children = emptyList(),
    )

    private fun infoElement() = EbmlElement(
        id = MatroskaIds.Info,
        data = Bytes(ByteArray(0)),
    )

    private fun tracksElement() = EbmlElement(
        id = MatroskaIds.Tracks,
        data = Bytes(ByteArray(0)),
    )

    private fun segmentElement(
        children: List<EbmlElement> = listOf(infoElement(), tracksElement()),
    ) = EbmlElement(
        id = MatroskaIds.Segment,
        data = Bytes(ByteArray(0)),
        children = children,
    )

    private fun minimalWebm(
        elements: List<EbmlElement> = listOf(ebmlHeaderElement(), segmentElement()),
    ) = Webm(elements)

    // --- view() tests ---

    @Test
    fun viewReturnsCorrectElements() {
        val file = minimalWebm()
        assertEquals(file.elements, file.inspect().elements)
    }

    @Test
    fun viewReturnsEbmlHeader() {
        assertNotNull(minimalWebm().inspect().ebmlHeader)
    }

    @Test
    fun viewReturnsSegment() {
        assertNotNull(minimalWebm().inspect().segment)
    }

    @Test
    fun viewReturnsInfoElement() {
        assertNotNull(minimalWebm().inspect().infoElement)
    }

    @Test
    fun viewReturnsTracksElement() {
        assertNotNull(minimalWebm().inspect().tracksElement)
    }

    @Test
    fun viewReturnsNullEbmlHeaderWhenAbsent() {
        assertNull(minimalWebm(listOf(segmentElement())).inspect().ebmlHeader)
    }

    @Test
    fun viewReturnsNullSegmentWhenAbsent() {
        assertNull(minimalWebm(listOf(ebmlHeaderElement())).inspect().segment)
    }

    @Test
    fun viewReturnsNullInfoWhenSegmentHasNoChildren() {
        val seg = segmentElement(children = emptyList())
        assertNull(minimalWebm(listOf(ebmlHeaderElement(), seg)).inspect().infoElement)
    }

    // --- edit() tests ---

    @Test
    fun editNoOpRoundTrip() {
        val original = minimalWebm()
        assertEquals(original, original.edit { })
    }

    @Test
    fun editRemovesSegment() {
        val original = minimalWebm()
        val edited = original.edit {
            elements = elements.filter { it.id != MatroskaIds.Segment }
        }
        assertNull(edited.inspect().segment)
    }

    @Test
    fun editAddsElement() {
        val original = minimalWebm(listOf(ebmlHeaderElement()))
        val edited = original.edit {
            elements = elements + segmentElement()
        }
        assertNotNull(edited.inspect().segment)
    }

    // --- Type hierarchy tests ---

    @Test
    fun viewIsFileView() {
        assertIs<StructureView<Webm>>(minimalWebm().inspect())
    }

    @Test
    fun viewIsWebmView() {
        assertIs<WebmView>(minimalWebm().inspect())
    }

    @Test
    fun mutableViewIsMutableStructureView() {
        val view = MutableWebmView(minimalWebm())
        assertIs<MutableStructureView<Webm>>(view)
        assertIs<WebmView>(view)
    }

    // --- Consistency tests ---

    @Test
    fun viewAndFileReturnSameValues() {
        val file = minimalWebm()
        val view = file.inspect()
        assertEquals(file.elements, view.elements)
        assertEquals(file.ebmlHeader, view.ebmlHeader)
        assertEquals(file.segment, view.segment)
        assertEquals(file.infoElement, view.infoElement)
        assertEquals(file.tracksElement, view.tracksElement)
    }
}
