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

class MkvViewTest {

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

    private fun minimalMkv(
        elements: List<EbmlElement> = listOf(ebmlHeaderElement(), segmentElement()),
    ) = Mkv(elements)

    // --- view() tests ---

    @Test
    fun viewReturnsCorrectElements() {
        val file = minimalMkv()
        assertEquals(file.elements, file.inspect().elements)
    }

    @Test
    fun viewReturnsEbmlHeader() {
        assertNotNull(minimalMkv().inspect().ebmlHeader)
    }

    @Test
    fun viewReturnsSegment() {
        assertNotNull(minimalMkv().inspect().segment)
    }

    @Test
    fun viewReturnsInfoElement() {
        assertNotNull(minimalMkv().inspect().infoElement)
    }

    @Test
    fun viewReturnsTracksElement() {
        assertNotNull(minimalMkv().inspect().tracksElement)
    }

    @Test
    fun viewReturnsNullEbmlHeaderWhenAbsent() {
        assertNull(minimalMkv(listOf(segmentElement())).inspect().ebmlHeader)
    }

    @Test
    fun viewReturnsNullSegmentWhenAbsent() {
        assertNull(minimalMkv(listOf(ebmlHeaderElement())).inspect().segment)
    }

    @Test
    fun viewReturnsNullInfoWhenSegmentHasNoChildren() {
        val seg = segmentElement(children = emptyList())
        assertNull(minimalMkv(listOf(ebmlHeaderElement(), seg)).inspect().infoElement)
    }

    // --- edit() tests ---

    @Test
    fun editNoOpRoundTrip() {
        val original = minimalMkv()
        assertEquals(original, original.edit { })
    }

    @Test
    fun editRemovesSegment() {
        val original = minimalMkv()
        val edited = original.edit {
            elements = elements.filter { it.id != MatroskaIds.Segment }
        }
        assertNull(edited.inspect().segment)
    }

    @Test
    fun editAddsElement() {
        val original = minimalMkv(listOf(ebmlHeaderElement()))
        val edited = original.edit {
            elements = elements + segmentElement()
        }
        assertNotNull(edited.inspect().segment)
    }

    // --- Type hierarchy tests ---

    @Test
    fun viewIsFileView() {
        assertIs<StructureView<Mkv>>(minimalMkv().inspect())
    }

    @Test
    fun viewIsMkvView() {
        assertIs<MkvView>(minimalMkv().inspect())
    }

    @Test
    fun mutableViewIsMutableStructureView() {
        val view = MutableMkvView(minimalMkv())
        assertIs<MutableStructureView<Mkv>>(view)
        assertIs<MkvView>(view)
    }

    // --- Consistency tests ---

    @Test
    fun viewAndFileReturnSameValues() {
        val file = minimalMkv()
        val view = file.inspect()
        assertEquals(file.elements, view.elements)
        assertEquals(file.ebmlHeader, view.ebmlHeader)
        assertEquals(file.segment, view.segment)
        assertEquals(file.infoElement, view.infoElement)
        assertEquals(file.tracksElement, view.tracksElement)
    }
}
