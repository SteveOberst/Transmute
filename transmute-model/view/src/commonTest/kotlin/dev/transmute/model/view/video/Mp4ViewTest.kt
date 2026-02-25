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

class Mp4ViewTest {

    // --- Helpers ---

    private fun ftypBox(majorBrand: String = "isom"): IsoBmffBox {
        val data = ByteArray(8)
        majorBrand.encodeToByteArray().copyInto(data, 0, 0, 4)
        return IsoBmffBox(type = FourCC("ftyp"), data = Bytes(data))
    }

    private fun moovBox() = IsoBmffBox(type = FourCC("moov"), data = Bytes(ByteArray(8)))
    private fun mdatBox() = IsoBmffBox(type = FourCC("mdat"), data = Bytes(ByteArray(64)))
    private fun freeBox() = IsoBmffBox(type = FourCC("free"))
    private fun skipBox() = IsoBmffBox(type = FourCC("skip"))

    private fun minimalMp4(
        boxes: List<IsoBmffBox> = listOf(ftypBox(), moovBox(), mdatBox()),
    ) = Mp4(boxes)

    // --- view() tests ---

    @Test
    fun viewReturnsCorrectBoxes() {
        val file = minimalMp4()
        assertEquals(file.boxes, file.inspect().boxes)
    }

    @Test
    fun viewReturnsFtypBox() {
        assertNotNull(minimalMp4().inspect().ftypBox)
    }

    @Test
    fun viewReturnsMoovBox() {
        assertNotNull(minimalMp4().inspect().moovBox)
    }

    @Test
    fun viewReturnsMdatBox() {
        assertNotNull(minimalMp4().inspect().mdatBox)
    }

    @Test
    fun viewReturnsNullMoovWhenAbsent() {
        assertNull(minimalMp4(listOf(ftypBox())).inspect().moovBox)
    }

    @Test
    fun viewReturnsFreeBoxes() {
        val view = minimalMp4(listOf(ftypBox(), freeBox(), skipBox(), mdatBox())).inspect()
        assertEquals(2, view.freeBoxes.size)
    }

    @Test
    fun viewReturnsEmptyFreeBoxesWhenAbsent() {
        assertTrue(minimalMp4().inspect().freeBoxes.isEmpty())
    }

    // --- edit() tests ---

    @Test
    fun editNoOpRoundTrip() {
        val original = minimalMp4()
        assertEquals(original, original.edit { })
    }

    @Test
    fun editRemovesFreeBoxes() {
        val original = minimalMp4(listOf(ftypBox(), freeBox(), skipBox(), moovBox(), mdatBox()))
        val edited = original.edit {
            boxes = boxes.filter { it.type.value != "free" && it.type.value != "skip" }
        }
        assertTrue(edited.inspect().freeBoxes.isEmpty())
        assertEquals(3, edited.boxes.size)
    }

    @Test
    fun editAddsBoxes() {
        val original = minimalMp4(listOf(ftypBox()))
        val edited = original.edit {
            boxes = boxes + moovBox() + mdatBox()
        }
        assertNotNull(edited.inspect().moovBox)
        assertNotNull(edited.inspect().mdatBox)
    }

    // --- Type hierarchy tests ---

    @Test
    fun viewIsFileView() {
        assertIs<StructureView<Mp4>>(minimalMp4().inspect())
    }

    @Test
    fun viewIsMp4View() {
        assertIs<Mp4View>(minimalMp4().inspect())
    }

    @Test
    fun mutableViewIsMutableStructureView() {
        val view = MutableMp4View(minimalMp4())
        assertIs<MutableStructureView<Mp4>>(view)
        assertIs<Mp4View>(view)
    }

    // --- Consistency tests ---

    @Test
    fun viewAndFileReturnSameValues() {
        val file = minimalMp4(listOf(ftypBox(), moovBox(), freeBox(), mdatBox()))
        val view = file.inspect()
        assertEquals(file.boxes, view.boxes)
        assertEquals(file.ftypBox, view.ftypBox)
        assertEquals(file.ftyp, view.ftyp)
        assertEquals(file.majorBrand, view.majorBrand)
        assertEquals(file.minorVersion, view.minorVersion)
        assertEquals(file.compatibleBrands, view.compatibleBrands)
        assertEquals(file.moovBox, view.moovBox)
        assertEquals(file.mdatBox, view.mdatBox)
        assertEquals(file.freeBoxes, view.freeBoxes)
    }
}
