package dev.transmute.model.view.image

import dev.transmute.model.core.Bytes
import dev.transmute.model.identify.Endianness
import dev.transmute.model.structure.image.*
import dev.transmute.model.view.StructureView
import dev.transmute.model.view.MutableStructureView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TiffViewTest {

    // --- Helpers ---

    private fun minimalTiff(
        byteOrder: Endianness = Endianness.Little,
        ifds: List<TiffIfd> = emptyList(),
    ) = Tiff(
        byteOrder = byteOrder,
        firstIfdOffset = 8u,
        ifds = ifds,
    )

    // --- view() tests ---

    @Test
    fun viewReturnsCorrectFields() {
        val file = minimalTiff()
        val view = file.view()
        assertEquals(Endianness.Little, view.byteOrder)
        assertEquals(8u, view.firstIfdOffset)
        assertTrue(view.ifds.isEmpty())
    }

    @Test
    fun viewReturnsNullDimensionsWhenNoIfds() {
        val view = minimalTiff().view()
        assertNull(view.width)
        assertNull(view.height)
        assertNull(view.compression)
        assertTrue(view.bitsPerSample.isEmpty())
    }

    // --- edit() tests ---

    @Test
    fun editNoOpRoundTrip() {
        val original = minimalTiff()
        val edited = original.edit { }
        assertEquals(original, edited)
    }

    @Test
    fun editMutatesByteOrder() {
        val original = minimalTiff(byteOrder = Endianness.Little)
        val edited = original.edit { byteOrder = Endianness.Big }
        assertEquals(Endianness.Big, edited.byteOrder)
    }

    @Test
    fun editMutatesFirstIfdOffset() {
        val original = minimalTiff()
        val edited = original.edit { firstIfdOffset = 16u }
        assertEquals(16u, edited.firstIfdOffset)
    }

    @Test
    fun editMutatesImageData() {
        val original = minimalTiff()
        val newData = Bytes(byteArrayOf(1, 2, 3))
        val edited = original.edit { imageData = newData }
        assertEquals(newData, edited.imageData)
    }

    // --- Type hierarchy tests ---

    @Test
    fun viewIsFileView() {
        assertIs<StructureView<Tiff>>(minimalTiff().view())
    }

    @Test
    fun viewIsTiffView() {
        assertIs<TiffView>(minimalTiff().view())
    }

    @Test
    fun mutableViewIsMutableStructureView() {
        val view = MutableTiffView(minimalTiff())
        assertIs<MutableStructureView<Tiff>>(view)
        assertIs<TiffView>(view)
    }

    // --- Consistency tests ---

    @Test
    fun viewAndFileReturnSameValues() {
        val file = minimalTiff()
        val view = file.view()
        assertEquals(file.byteOrder, view.byteOrder)
        assertEquals(file.firstIfdOffset, view.firstIfdOffset)
        assertEquals(file.ifds, view.ifds)
        assertEquals(file.imageData, view.imageData)
        assertEquals(file.extraData, view.extraData)
        assertEquals(file.width, view.width)
        assertEquals(file.height, view.height)
        assertEquals(file.bitsPerSample, view.bitsPerSample)
        assertEquals(file.compression, view.compression)
    }
}
