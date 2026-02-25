package dev.transmute.model.view.image

import dev.transmute.model.core.Bytes
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

class HeifViewTest {

    // --- Helpers ---

    private fun ftypBox(majorBrand: String = "heic"): IsoBmffBox {
        val data = ByteArray(8)
        majorBrand.encodeToByteArray().copyInto(data, 0, 0, 4)
        return IsoBmffBox(type = FourCC("ftyp"), data = Bytes(data))
    }

    private fun mdatBox() = IsoBmffBox(type = FourCC("mdat"), data = Bytes(ByteArray(16)))
    private fun metaBox() = IsoBmffBox(type = FourCC("meta"))

    private fun minimalHeif(
        boxes: List<IsoBmffBox> = listOf(ftypBox(), mdatBox()),
    ) = Heif(boxes)

    // --- view() tests ---

    @Test
    fun viewReturnsCorrectBoxes() {
        val file = minimalHeif()
        assertEquals(file.boxes, file.view().boxes)
    }

    @Test
    fun viewReturnsFtypBox() {
        assertNotNull(minimalHeif().view().ftypBox)
    }

    @Test
    fun viewReturnsNullMetaWhenAbsent() {
        assertNull(minimalHeif(listOf(ftypBox())).view().metaBox)
    }

    @Test
    fun viewReturnsMetaWhenPresent() {
        assertNotNull(minimalHeif(listOf(ftypBox(), metaBox())).view().metaBox)
    }

    // --- edit() tests ---

    @Test
    fun editNoOpRoundTrip() {
        val original = minimalHeif()
        assertEquals(original, original.edit { })
    }

    @Test
    fun editRemovesBoxes() {
        val original = minimalHeif(listOf(ftypBox(), metaBox(), mdatBox()))
        val edited = original.edit {
            boxes = boxes.filter { it.type.value != "meta" }
        }
        assertNull(edited.view().metaBox)
    }

    @Test
    fun editAddsBoxes() {
        val original = minimalHeif(listOf(ftypBox()))
        val edited = original.edit {
            boxes = boxes + mdatBox()
        }
        assertNotNull(edited.view().mdatBox)
    }

    // --- Type hierarchy tests ---

    @Test
    fun viewIsFileView() {
        assertIs<StructureView<Heif>>(minimalHeif().view())
    }

    @Test
    fun viewIsHeifView() {
        assertIs<HeifView>(minimalHeif().view())
    }

    @Test
    fun mutableViewIsMutableStructureView() {
        val view = MutableHeifView(minimalHeif())
        assertIs<MutableStructureView<Heif>>(view)
        assertIs<HeifView>(view)
    }

    // --- Consistency tests ---

    @Test
    fun viewAndFileReturnSameValues() {
        val file = minimalHeif(listOf(ftypBox(), metaBox(), mdatBox()))
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
