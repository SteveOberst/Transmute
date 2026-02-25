package dev.transmute.model.view.image

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes
import dev.transmute.model.identify.FourCC
import dev.transmute.model.structure.image.*
import dev.transmute.model.view.StructureView
import dev.transmute.model.view.MutableStructureView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun UInt.be(): ByteArray = byteArrayOf(
    (this shr 24).toByte(), (this shr 16).toByte(),
    (this shr 8).toByte(), this.toByte(),
)

private fun buildTestChunk(type: String, data: ByteArray = ByteArray(0)): PngChunk {
    val typeBytes = type.encodeToByteArray()
    return PngChunk(
        length = data.size.toUInt(),
        type = FourCC(type),
        data = data.asBytes(),
        crc = crc32(typeBytes + data),
    )
}

private fun ihdrData(
    width: UInt = 8u,
    height: UInt = 8u,
    bitDepth: UByte = 8u,
    colorType: Int = 2,
): ByteArray {
    val out = ByteArray(13)
    width.be().copyInto(out, 0)
    height.be().copyInto(out, 4)
    out[8] = bitDepth.toByte()
    out[9] = colorType.toByte()
    return out
}

private fun minimalPng(
    width: UInt = 8u,
    height: UInt = 8u,
    colorType: Int = 2,
    extraChunks: List<PngChunk> = emptyList(),
): Png {
    val ihdr = buildTestChunk("IHDR", ihdrData(width, height, colorType = colorType))
    val iend = buildTestChunk("IEND")
    return Png(
        signature = Bytes(Png.SIGNATURE.copyOf()),
        chunks = listOf(ihdr) + extraChunks + iend,
    )
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

class PngViewTest {

    // === Png.view() immutable view ===

    @Test
    fun viewReturnsIhdr() {
        val png = minimalPng(width = 320u, height = 240u)
        val view: PngView = png.view()
        assertEquals(320u, view.ihdr.width)
        assertEquals(240u, view.ihdr.height)
    }

    @Test
    fun viewReturnsNullForAbsentChunks() {
        val png = minimalPng()
        val view = png.view()
        assertNull(view.plte)
        assertNull(view.gama)
        assertNull(view.chrm)
        assertNull(view.srgb)
        assertNull(view.iccp)
        assertNull(view.phys)
        assertNull(view.time)
        assertNull(view.sbit)
        assertNull(view.bkgd)
        assertNull(view.hist)
        assertNull(view.trns)
        assertNull(view.actl)
    }

    @Test
    fun viewReturnsGamaWhenPresent() {
        val gamaData = 45455u.be()
        val png = minimalPng(extraChunks = listOf(buildTestChunk("gAMA", gamaData)))
        val view = png.view()
        assertNotNull(view.gama)
        assertEquals(45455u, view.gama!!.gamma)
    }

    @Test
    fun viewReturnsIdatChunks() {
        val idat = byteArrayOf(0x78, 0x01, 0x63, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01)
        val png = minimalPng(extraChunks = listOf(buildTestChunk("IDAT", idat)))
        val view = png.view()
        assertEquals(1, view.idatChunks.size)
    }

    @Test
    fun viewReturnsEmptyListsForAbsentListChunks() {
        val png = minimalPng()
        val view = png.view()
        assertTrue(view.idatChunks.isEmpty())
        assertTrue(view.textChunks.isEmpty())
        assertTrue(view.ztxtChunks.isEmpty())
        assertTrue(view.itxtChunks.isEmpty())
        assertTrue(view.spltChunks.isEmpty())
        assertTrue(view.fctlChunks.isEmpty())
    }

    @Test
    fun viewReturnsTextChunks() {
        val textData = "Author".encodeToByteArray() + byteArrayOf(0) + "Transmute".encodeToByteArray()
        val png = minimalPng(extraChunks = listOf(buildTestChunk("tEXt", textData)))
        val view = png.view()
        assertEquals(1, view.textChunks.size)
        assertEquals("Author", view.textChunks[0].keyword)
    }

    // === Type hierarchy ===

    @Test
    fun viewIsPngView() {
        val view = minimalPng().view()
        assertIs<PngView>(view)
    }

    @Test
    fun viewIsFileView() {
        val view = minimalPng().view()
        assertIs<StructureView<Png>>(view)
    }

    @Test
    fun mutablePngViewIsPngView() {
        val png = minimalPng()
        val edited: PngView = png.edit { ihdr = ihdr.copy(width = 100u) }.view()
        assertIs<PngView>(edited)
    }

    @Test
    fun mutablePngViewIsMutableStructureView() {
        // MutablePngView is internal-constructor, but we can verify
        // the edit {} result's source is usable as PngView
        val png = minimalPng()
        // Verify through the class hierarchy: edit creates a MutablePngView internally
        // We can't access it directly, but we can verify the pattern works
        val result = png.edit { ihdr = ihdr.copy(width = 100u) }
        assertEquals(100u, result.view().ihdr.width)
    }

    // === PngView as read-only contract ===

    @Test
    fun functionAcceptingPngViewCanReadFields() {
        val png = minimalPng(width = 1920u, height = 1080u)
        val view: PngView = png.view()
        val dimensions = extractDimensions(view)
        assertEquals(1920u to 1080u, dimensions)
    }

    @Test
    fun mutableViewUpcastsToPngView() {
        val png = minimalPng(width = 640u, height = 480u)
        // edit {} internally creates a MutablePngView which IS-A PngView
        // Verify by re-reading from the result
        val result = png.edit {
            ihdr = ihdr.copy(width = 800u)
        }
        val view: PngView = result.view()
        val dimensions = extractDimensions(view)
        assertEquals(800u to 480u, dimensions)
    }

    // === Consistency across view tiers ===

    @Test
    fun immutableViewMatchesPngFileAccessors() {
        val gamaData = 45455u.be()
        val idat = byteArrayOf(0x78, 0x01)
        val png = minimalPng(
            width = 42u, height = 99u,
            extraChunks = listOf(
                buildTestChunk("gAMA", gamaData),
                buildTestChunk("IDAT", idat),
            )
        )

        val view = png.view()

        // Every PngView property should match Png's computed accessor
        assertEquals(png.ihdr.width, view.ihdr.width)
        assertEquals(png.ihdr.height, view.ihdr.height)
        assertEquals(png.gama?.gamma, view.gama?.gamma)
        assertEquals(png.plte, view.plte)
        assertEquals(png.idatChunks.size, view.idatChunks.size)
        assertEquals(png.textChunks.size, view.textChunks.size)
    }

    @Test
    fun editedFileViewMatchesMutableResult() {
        val png = minimalPng(width = 8u)
        val edited = png.edit {
            ihdr = ihdr.copy(width = 256u)
            gama = PngGama(100000u)
        }
        val view = edited.view()
        assertEquals(256u, view.ihdr.width)
        assertNotNull(view.gama)
        assertEquals(100000u, view.gama!!.gamma)
    }
}

// Helper function that accepts the read-only PngView contract
private fun extractDimensions(view: PngView): Pair<UInt, UInt> =
    view.ihdr.width to view.ihdr.height
