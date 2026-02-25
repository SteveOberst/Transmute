package dev.transmute.model.view.audio

import dev.transmute.model.core.Bytes
import dev.transmute.model.identify.FourCC
import dev.transmute.model.structure.common.IsoBmffBox
import dev.transmute.model.structure.audio.*
import dev.transmute.model.view.StructureView
import dev.transmute.model.view.MutableStructureView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class M4aViewTest {

    // --- Helpers ---

    private fun ftypBox(majorBrand: String = "M4A "): IsoBmffBox {
        val data = ByteArray(8)
        majorBrand.encodeToByteArray().copyInto(data, 0, 0, 4)
        return IsoBmffBox(type = FourCC("ftyp"), data = Bytes(data))
    }

    private fun moovBox() = IsoBmffBox(type = FourCC("moov"), data = Bytes(ByteArray(8)))
    private fun mdatBox() = IsoBmffBox(type = FourCC("mdat"), data = Bytes(ByteArray(64)))

    private fun minimalM4a(
        boxes: List<IsoBmffBox> = listOf(ftypBox(), moovBox(), mdatBox()),
    ) = M4a(boxes)

    // --- view() tests ---

    @Test
    fun viewReturnsCorrectBoxes() {
        val file = minimalM4a()
        assertEquals(file.boxes, file.inspect().boxes)
    }

    @Test
    fun viewReturnsFtypBox() {
        assertNotNull(minimalM4a().inspect().ftypBox)
    }

    @Test
    fun viewReturnsMoovBox() {
        assertNotNull(minimalM4a().inspect().moovBox)
    }

    @Test
    fun viewReturnsMdatBox() {
        assertNotNull(minimalM4a().inspect().mdatBox)
    }

    @Test
    fun viewReturnsNullMoovWhenAbsent() {
        assertNull(minimalM4a(listOf(ftypBox())).inspect().moovBox)
    }

    @Test
    fun viewReturnsEmptyCompatibleBrandsWhenNoFtyp() {
        assertTrue(minimalM4a(listOf(moovBox())).inspect().compatibleBrands.isEmpty())
    }

    // --- edit() tests ---

    @Test
    fun editNoOpRoundTrip() {
        val original = minimalM4a()
        assertEquals(original, original.edit { })
    }

    @Test
    fun editRemovesBoxes() {
        val original = minimalM4a()
        val edited = original.edit {
            boxes = boxes.filter { it.type.value != "mdat" }
        }
        assertNull(edited.inspect().mdatBox)
        assertEquals(2, edited.boxes.size)
    }

    @Test
    fun editAddsBoxes() {
        val original = minimalM4a(listOf(ftypBox()))
        val edited = original.edit {
            boxes = boxes + moovBox() + mdatBox()
        }
        assertNotNull(edited.inspect().moovBox)
        assertNotNull(edited.inspect().mdatBox)
    }

    // --- Type hierarchy tests ---

    @Test
    fun viewIsFileView() {
        assertIs<StructureView<M4a>>(minimalM4a().inspect())
    }

    @Test
    fun viewIsM4aView() {
        assertIs<M4aView>(minimalM4a().inspect())
    }

    @Test
    fun mutableViewIsMutableStructureView() {
        val view = MutableM4aView(minimalM4a())
        assertIs<MutableStructureView<M4a>>(view)
        assertIs<M4aView>(view)
    }

    // --- Consistency tests ---

    @Test
    fun viewAndFileReturnSameValues() {
        val file = minimalM4a()
        val view = file.inspect()
        assertEquals(file.boxes, view.boxes)
        assertEquals(file.ftypBox, view.ftypBox)
        assertEquals(file.ftyp, view.ftyp)
        assertEquals(file.majorBrand, view.majorBrand)
        assertEquals(file.minorVersion, view.minorVersion)
        assertEquals(file.compatibleBrands, view.compatibleBrands)
        assertEquals(file.moovBox, view.moovBox)
        assertEquals(file.mdatBox, view.mdatBox)
    }
}
