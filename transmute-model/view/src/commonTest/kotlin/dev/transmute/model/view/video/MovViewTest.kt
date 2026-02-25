package dev.transmute.model.view.video

import dev.transmute.model.core.Bytes
import dev.transmute.model.identify.FourCC
import dev.transmute.model.structure.common.IsoBmffBox
import dev.transmute.model.structure.video.*
import dev.transmute.model.view.StructureView
import dev.transmute.model.view.MutableStructureView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MovViewTest {

    // --- Helpers ---

    private fun ftypBox(majorBrand: String = "qt  "): IsoBmffBox {
        val data = ByteArray(8)
        majorBrand.encodeToByteArray().copyInto(data, 0, 0, 4)
        return IsoBmffBox(type = FourCC("ftyp"), data = Bytes(data))
    }

    private fun moovBox() = IsoBmffBox(type = FourCC("moov"), data = Bytes(ByteArray(8)))
    private fun mdatBox() = IsoBmffBox(type = FourCC("mdat"), data = Bytes(ByteArray(64)))

    private fun minimalMov(
        boxes: List<IsoBmffBox> = listOf(ftypBox(), moovBox(), mdatBox()),
    ) = Mov(boxes)

    // --- view() tests ---

    @Test
    fun viewReturnsCorrectBoxes() {
        val file = minimalMov()
        assertEquals(file.boxes, file.inspect().boxes)
    }

    @Test
    fun viewReturnsFtypBox() {
        assertNotNull(minimalMov().inspect().ftypBox)
    }

    @Test
    fun viewReturnsMoovBox() {
        assertNotNull(minimalMov().inspect().moovBox)
    }

    @Test
    fun viewReturnsMdatBox() {
        assertNotNull(minimalMov().inspect().mdatBox)
    }

    @Test
    fun viewReturnsNullMoovWhenAbsent() {
        assertNull(minimalMov(listOf(ftypBox())).inspect().moovBox)
    }

    @Test
    fun viewReturnsEmptyCompatibleBrandsWhenNoFtyp() {
        assertTrue(minimalMov(listOf(moovBox())).inspect().compatibleBrands.isEmpty())
    }

    // --- edit() tests ---

    @Test
    fun editNoOpRoundTrip() {
        val original = minimalMov()
        assertEquals(original, original.edit { })
    }

    @Test
    fun editRemovesMdat() {
        val original = minimalMov()
        val edited = original.edit {
            boxes = boxes.filter { it.type.value != "mdat" }
        }
        assertNull(edited.inspect().mdatBox)
    }

    @Test
    fun editAddsBoxes() {
        val original = minimalMov(listOf(ftypBox()))
        val edited = original.edit {
            boxes = boxes + moovBox() + mdatBox()
        }
        assertNotNull(edited.inspect().moovBox)
        assertNotNull(edited.inspect().mdatBox)
    }

    // --- Type hierarchy tests ---

    @Test
    fun viewIsFileView() {
        assertIs<StructureView<Mov>>(minimalMov().inspect())
    }

    @Test
    fun viewIsMovView() {
        assertIs<MovView>(minimalMov().inspect())
    }

    @Test
    fun mutableViewIsMutableStructureView() {
        val view = MutableMovView(minimalMov())
        assertIs<MutableStructureView<Mov>>(view)
        assertIs<MovView>(view)
    }

    // --- Consistency tests ---

    @Test
    fun viewAndFileReturnSameValues() {
        val file = minimalMov()
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
