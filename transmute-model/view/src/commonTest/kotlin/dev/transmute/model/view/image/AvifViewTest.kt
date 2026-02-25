package dev.transmute.model.view.image

import dev.transmute.model.core.Bytes
import dev.transmute.model.identify.Brand
import dev.transmute.model.identify.FourCC
import dev.transmute.model.structure.common.IsoBmffBox
import dev.transmute.model.structure.image.*
import dev.transmute.model.view.StructureView
import dev.transmute.model.view.MutableStructureView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AvifViewTest {

    // --- Helpers ---

    /** Build a minimal ftyp box with the given major brand. */
    private fun ftypBox(majorBrand: String = "avif"): IsoBmffBox {
        val brandBytes = majorBrand.encodeToByteArray()
        val data = ByteArray(8) // majorBrand(4) + minorVersion(4)
        brandBytes.copyInto(data, 0, 0, 4)
        return IsoBmffBox(type = FourCC("ftyp"), data = Bytes(data))
    }

    private fun mdatBox(data: ByteArray = ByteArray(16)) =
        IsoBmffBox(type = FourCC("mdat"), data = Bytes(data))

    private fun metaBox() =
        IsoBmffBox(type = FourCC("meta"))

    private fun minimalAvif(
        boxes: List<IsoBmffBox> = listOf(ftypBox(), mdatBox()),
    ) = Avif(boxes)

    // --- view() tests ---

    @Test
    fun viewReturnsCorrectBoxes() {
        val file = minimalAvif()
        val view = file.view()
        assertEquals(file.boxes, view.boxes)
    }

    @Test
    fun viewReturnsFtypBox() {
        val view = minimalAvif().view()
        assertNotNull(view.ftypBox)
        assertEquals("ftyp", view.ftypBox?.type?.value)
    }

    @Test
    fun viewReturnsMdatBox() {
        val view = minimalAvif().view()
        assertNotNull(view.mdatBox)
    }

    @Test
    fun viewReturnsNullMetaWhenAbsent() {
        val view = minimalAvif(listOf(ftypBox())).view()
        assertNull(view.metaBox)
    }

    @Test
    fun viewReturnsMetaWhenPresent() {
        val view = minimalAvif(listOf(ftypBox(), metaBox(), mdatBox())).view()
        assertNotNull(view.metaBox)
    }

    // --- edit() tests ---

    @Test
    fun editNoOpRoundTrip() {
        val original = minimalAvif()
        val edited = original.edit { }
        assertEquals(original, edited)
    }

    @Test
    fun editRemovesBoxes() {
        val original = minimalAvif(listOf(ftypBox(), metaBox(), mdatBox()))
        val edited = original.edit {
            boxes = boxes.filter { it.type.value != "meta" }
        }
        assertNull(edited.view().metaBox)
        assertEquals(2, edited.boxes.size)
    }

    @Test
    fun editAddsBoxes() {
        val original = minimalAvif(listOf(ftypBox()))
        val edited = original.edit {
            boxes = boxes + mdatBox()
        }
        assertNotNull(edited.view().mdatBox)
    }

    // --- Type hierarchy tests ---

    @Test
    fun viewIsFileView() {
        assertIs<StructureView<Avif>>(minimalAvif().view())
    }

    @Test
    fun viewIsAvifView() {
        assertIs<AvifView>(minimalAvif().view())
    }

    @Test
    fun mutableViewIsMutableStructureView() {
        val view = MutableAvifView(minimalAvif())
        assertIs<MutableStructureView<Avif>>(view)
        assertIs<AvifView>(view)
    }

    // --- Consistency tests ---

    @Test
    fun viewAndFileReturnSameValues() {
        val file = minimalAvif(listOf(ftypBox(), metaBox(), mdatBox()))
        val view = file.view()
        assertEquals(file.boxes, view.boxes)
        assertEquals(file.ftypBox, view.ftypBox)
        assertEquals(file.ftyp, view.ftyp)
        assertEquals(file.majorBrand, view.majorBrand)
        assertEquals(file.minorVersion, view.minorVersion)
        assertEquals(file.compatibleBrands, view.compatibleBrands)
        assertEquals(file.metaBox, view.metaBox)
        assertEquals(file.mdatBox, view.mdatBox)
    }
}
