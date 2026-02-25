package dev.transmute.model.view.image

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.Pixels
import dev.transmute.model.structure.image.*
import dev.transmute.model.view.StructureView
import dev.transmute.model.view.MutableStructureView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class BmpViewTest {

    // --- Helpers ---

    private fun minimalBmp(
        width: Int = 2,
        height: Int = 2,
        bitsPerPixel: UShort = 24u,
        headerSize: UInt = 40u,
    ): Bmp {
        val rowBytes = (width * bitsPerPixel.toInt() / 8 + 3) and 0x7FFFFFFC.toInt()
        val pixelDataSize = rowBytes * kotlin.math.abs(height)
        val dataOffset = 14u + headerSize
        val fileSize = dataOffset + pixelDataSize.toUInt()
        return Bmp(
            fileHeader = BmpFileHeader(
                fileSize = fileSize,
                dataOffset = dataOffset,
            ),
            dibHeader = BmpDibHeader(
                headerSize = headerSize,
                width = width,
                height = height,
                bitsPerPixel = bitsPerPixel,
            ),
            pixelData = Bytes(ByteArray(pixelDataSize)),
        )
    }

    // --- view() tests ---

    @Test
    fun viewReturnsCorrectFields() {
        val file = minimalBmp()
        val view = file.view()
        assertEquals(file.fileHeader, view.fileHeader)
        assertEquals(file.dibHeader, view.dibHeader)
        assertEquals(file.pixelData.data.size, view.pixelData.data.size)
    }

    @Test
    fun viewComputesDimensions() {
        val view = minimalBmp(width = 100, height = 200).view()
        assertEquals(Pixels(100), view.width)
        assertEquals(Pixels(200), view.height)
    }

    @Test
    fun viewComputesTopDown() {
        val topDown = minimalBmp(height = -50).view()
        assertEquals(true, topDown.isTopDown)

        val bottomUp = minimalBmp(height = 50).view()
        assertFalse(bottomUp.isTopDown)
    }

    @Test
    fun viewComputesBitsPerPixel() {
        assertEquals(24, minimalBmp(bitsPerPixel = 24u).view().bitsPerPixel)
        assertEquals(32, minimalBmp(bitsPerPixel = 32u).view().bitsPerPixel)
    }

    // --- edit() tests ---

    @Test
    fun editNoOpRoundTrip() {
        val original = minimalBmp()
        val edited = original.edit { }
        assertEquals(original, edited)
    }

    @Test
    fun editMutatesDibHeader() {
        val original = minimalBmp(width = 10, height = 10)
        val edited = original.edit {
            dibHeader = dibHeader.copy(width = 20)
        }
        assertEquals(20, edited.dibHeader.width)
        assertEquals(Pixels(20), edited.width)
    }

    @Test
    fun editMutatesPixelData() {
        val original = minimalBmp()
        val newData = Bytes(ByteArray(original.pixelData.data.size) { 0xFF.toByte() })
        val edited = original.edit { pixelData = newData }
        assertEquals(newData, edited.pixelData)
    }

    // --- Type hierarchy tests ---

    @Test
    fun viewIsFileView() {
        assertIs<StructureView<Bmp>>(minimalBmp().view())
    }

    @Test
    fun viewIsBmpView() {
        assertIs<BmpView>(minimalBmp().view())
    }

    @Test
    fun mutableViewIsMutableStructureView() {
        val view = MutableBmpView(minimalBmp())
        assertIs<MutableStructureView<Bmp>>(view)
        assertIs<BmpView>(view)
    }

    // --- Consistency tests ---

    @Test
    fun viewAndFileReturnSameValues() {
        val file = minimalBmp()
        val view = file.view()
        assertEquals(file.width, view.width)
        assertEquals(file.height, view.height)
        assertEquals(file.isTopDown, view.isTopDown)
        assertEquals(file.bitsPerPixel, view.bitsPerPixel)
        assertEquals(file.compression, view.compression)
        assertEquals(file.rowStride, view.rowStride)
    }
}
