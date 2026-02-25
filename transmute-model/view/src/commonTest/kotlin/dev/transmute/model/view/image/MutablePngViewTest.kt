package dev.transmute.model.view.image

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes
import dev.transmute.model.identify.FourCC
import dev.transmute.model.structure.image.*
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Helpers — mirrors the helpers in PngFileTest but kept self-contained
// ---------------------------------------------------------------------------

private fun UInt.be(): ByteArray = byteArrayOf(
    (this shr 24).toByte(), (this shr 16).toByte(),
    (this shr 8).toByte(), this.toByte(),
)

private fun UShort.be(): ByteArray = byteArrayOf(
    (this.toInt() shr 8).toByte(), this.toByte(),
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
    compressionMethod: Int = 0,
    filterMethod: Int = 0,
    interlaceMethod: Int = 0,
): ByteArray {
    val out = ByteArray(13)
    width.be().copyInto(out, 0)
    height.be().copyInto(out, 4)
    out[8] = bitDepth.toByte()
    out[9] = colorType.toByte()
    out[10] = compressionMethod.toByte()
    out[11] = filterMethod.toByte()
    out[12] = interlaceMethod.toByte()
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

class MutablePngViewTest {

    // --- edit identity: no-op should round-trip ---

    @Test
    fun editNoOpPreservesIhdr() {
        val original = minimalPng(width = 16u, height = 32u)
        val edited = original.edit {}
        assertEquals(original.ihdr, edited.ihdr)
    }

    @Test
    fun editNoOpPreservesSignature() {
        val original = minimalPng()
        val edited = original.edit {}
        assertContentEquals(Png.SIGNATURE, edited.signature.data)
    }

    @Test
    fun editNoOpHasIhdrFirstAndIendLast() {
        val original = minimalPng()
        val edited = original.edit {}
        assertEquals("IHDR", edited.chunks.first().type.value)
        assertEquals("IEND", edited.chunks.last().type.value)
    }

    // --- mutating IHDR ---

    @Test
    fun editIhdrWidth() {
        val original = minimalPng(width = 8u, height = 8u)
        val edited = original.edit {
            ihdr = ihdr.copy(width = 1920u)
        }
        assertEquals(1920u, edited.ihdr.width)
        assertEquals(8u, edited.ihdr.height) // unchanged
    }

    @Test
    fun editIhdrHeightAndBitDepth() {
        val original = minimalPng()
        val edited = original.edit {
            ihdr = ihdr.copy(height = 1080u, bitDepth = 16u)
        }
        assertEquals(1080u, edited.ihdr.height)
        assertEquals(16.toUByte(), edited.ihdr.bitDepth)
    }

    @Test
    fun editIhdrColorType() {
        val original = minimalPng(colorType = 2) // RGB
        val edited = original.edit {
            ihdr = ihdr.copy(colorType = PngColorType.RgbAlpha)
        }
        assertEquals(PngColorType.RgbAlpha, edited.ihdr.colorType)
    }

    // --- CRC correctness ---

    @Test
    fun editProducesCorrectCrcForIhdr() {
        val original = minimalPng()
        val edited = original.edit {
            ihdr = ihdr.copy(width = 256u)
        }
        val ihdrChunk = edited.chunks.first { it.type.value == "IHDR" }
        val expectedCrc = crc32("IHDR".encodeToByteArray() + ihdrChunk.data.data)
        assertEquals(expectedCrc, ihdrChunk.crc)
    }

    @Test
    fun editProducesCorrectCrcForIend() {
        val original = minimalPng()
        val edited = original.edit {}
        val iendChunk = edited.chunks.last { it.type.value == "IEND" }
        val expectedCrc = crc32("IEND".encodeToByteArray())
        assertEquals(expectedCrc, iendChunk.crc)
    }

    // --- optional chunk: gAMA ---

    @Test
    fun editAddGama() {
        val original = minimalPng()
        assertNull(original.gama)
        val edited = original.edit {
            gama = PngGama(45455u) // 1/2.2
        }
        assertNotNull(edited.gama)
        assertEquals(45455u, edited.gama!!.gamma)
    }

    @Test
    fun editRemoveGama() {
        val gamaData = 45455u.be()
        val withGama = minimalPng(extraChunks = listOf(buildTestChunk("gAMA", gamaData)))
        assertNotNull(withGama.gama)
        val edited = withGama.edit {
            gama = null
        }
        assertNull(edited.gama)
    }

    // --- optional chunk: pHYs ---

    @Test
    fun editAddPhys() {
        val original = minimalPng()
        val edited = original.edit {
            phys = PngPhys(3780u, 3780u, 1u) // 96 DPI metric
        }
        val p = edited.phys
        assertNotNull(p)
        assertEquals(3780u, p.pixelsPerUnitX)
        assertEquals(3780u, p.pixelsPerUnitY)
        assertEquals(1.toUByte(), p.unitSpecifier)
    }

    // --- optional chunk: tIME ---

    @Test
    fun editSetTime() {
        val original = minimalPng()
        val edited = original.edit {
            time = PngTime(
                year = 2026u,
                month = 2u,
                day = 24u,
                hour = 14u,
                minute = 30u,
                second = 0u,
            )
        }
        val t = edited.time
        assertNotNull(t)
        assertEquals(2026.toUShort(), t.year)
        assertEquals(2.toUByte(), t.month)
        assertEquals(24.toUByte(), t.day)
    }

    // --- text chunks ---

    @Test
    fun editAddTextChunk() {
        val original = minimalPng()
        val edited = original.edit {
            textChunks = listOf(
                PngTextChunk("Author", "Transmute"),
                PngTextChunk("Description", "A test image"),
            )
        }
        assertEquals(2, edited.textChunks.size)
        assertEquals("Author", edited.textChunks[0].keyword)
        assertEquals("Transmute", edited.textChunks[0].text)
    }

    @Test
    fun editClearTextChunks() {
        val textData = "Title".encodeToByteArray() + byteArrayOf(0) + "Test".encodeToByteArray()
        val withText = minimalPng(extraChunks = listOf(buildTestChunk("tEXt", textData)))
        assertEquals(1, withText.textChunks.size)
        val edited = withText.edit {
            textChunks = emptyList()
        }
        assertTrue(edited.textChunks.isEmpty())
    }

    // --- IDAT ---

    @Test
    fun editPreservesIdatData() {
        val idatPayload = byteArrayOf(0x78, 0x01, 0x63, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01)
        val withIdat = minimalPng(
            extraChunks = listOf(buildTestChunk("IDAT", idatPayload))
        )
        val edited = withIdat.edit {}
        assertEquals(1, edited.idatChunks.size)
        assertContentEquals(idatPayload, edited.idatChunks[0].compressedData.data)
    }

    @Test
    fun editReplaceIdatData() {
        val idatPayload = byteArrayOf(0x78, 0x01, 0x63, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01)
        val withIdat = minimalPng(
            extraChunks = listOf(buildTestChunk("IDAT", idatPayload))
        )
        val newPayload = byteArrayOf(0x78, 0x9C.toByte(), 0x63, 0x60, 0x00, 0x00, 0x00, 0x02, 0x00, 0x01)
        val edited = withIdat.edit {
            idatChunks = listOf(PngIdat(newPayload.asBytes()))
        }
        assertContentEquals(newPayload, edited.idatChunks[0].compressedData.data)
    }

    // --- PLTE ---

    @Test
    fun editAddPlte() {
        val original = minimalPng(colorType = 3) // indexed
        val edited = original.edit {
            plte = PngPlte(
                listOf(
                    PngPlteEntry(255u, 0u, 0u),
                    PngPlteEntry(0u, 255u, 0u),
                    PngPlteEntry(0u, 0u, 255u),
                )
            )
        }
        val p = edited.plte
        assertNotNull(p)
        assertEquals(3, p.entries.size)
        assertEquals(255.toUByte(), p.entries[0].red)
    }

    // --- sRGB ---

    @Test
    fun editAddSrgb() {
        val original = minimalPng()
        val edited = original.edit {
            srgb = PngSrgb(PngRenderingIntent.Perceptual)
        }
        assertNotNull(edited.srgb)
        assertEquals(PngRenderingIntent.Perceptual, edited.srgb!!.renderingIntent)
    }

    // --- unknown chunk preservation ---

    @Test
    fun editPreservesUnknownChunks() {
        val customData = byteArrayOf(1, 2, 3, 4)
        val customChunk = buildTestChunk("xYzZ", customData)
        val original = minimalPng(extraChunks = listOf(customChunk))
        val edited = original.edit {
            ihdr = ihdr.copy(width = 100u)
        }
        val found = edited.chunks.filter { it.type.value == "xYzZ" }
        assertEquals(1, found.size)
        assertContentEquals(customData, found[0].data.data)
    }

    // --- chunk ordering ---

    @Test
    fun editMaintainsSpecChunkOrder() {
        val gamaData = 45455u.be()
        val physData = ByteArray(9).also {
            3780u.be().copyInto(it, 0)
            3780u.be().copyInto(it, 4)
            it[8] = 1
        }
        val textData = "Key".encodeToByteArray() + byteArrayOf(0) + "Value".encodeToByteArray()
        val idatData = byteArrayOf(0x78, 0x01, 0x63, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01)

        val original = minimalPng(
            extraChunks = listOf(
                buildTestChunk("IDAT", idatData),
                buildTestChunk("tEXt", textData),
                buildTestChunk("pHYs", physData),
                buildTestChunk("gAMA", gamaData),
            )
        )

        val edited = original.edit {}

        val types = edited.chunks.map { it.type.value }

        // IHDR must be first, IEND must be last
        assertEquals("IHDR", types.first())
        assertEquals("IEND", types.last())

        // gAMA before pHYs, pHYs before tEXt, tEXt before IDAT
        val gamaIdx = types.indexOf("gAMA")
        val physIdx = types.indexOf("pHYs")
        val textIdx = types.indexOf("tEXt")
        val idatIdx = types.indexOf("IDAT")
        assertTrue(gamaIdx < physIdx, "gAMA should come before pHYs")
        assertTrue(physIdx < textIdx, "pHYs should come before tEXt")
        assertTrue(textIdx < idatIdx, "tEXt should come before IDAT")
    }

    // --- binary round-trip ---

    @Test
    fun editResultProducesValidBytes() {
        val idatData = byteArrayOf(0x78, 0x01, 0x63, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01)
        val original = minimalPng(extraChunks = listOf(buildTestChunk("IDAT", idatData)))
        val edited = original.edit {
            ihdr = ihdr.copy(width = 64u)
        }
        val bytes = edited.toBytes().data

        // Must start with PNG signature
        assertContentEquals(Png.SIGNATURE, bytes.sliceArray(0..7))

        // Total size should be: 8 (sig) + sum of chunks (4+4+data+4 each)
        val expectedSize = 8 + edited.chunks.sumOf { 4 + 4 + it.data.size + 4 }
        assertEquals(expectedSize, bytes.size)
    }

    // --- multiple edits chain ---

    @Test
    fun chainedEditsAccumulate() {
        val original = minimalPng(width = 8u, height = 8u)
        val step1 = original.edit { ihdr = ihdr.copy(width = 100u) }
        val step2 = step1.edit { ihdr = ihdr.copy(height = 200u) }

        assertEquals(100u, step2.ihdr.width)
        assertEquals(200u, step2.ihdr.height)
    }

    // --- APNG: acTL ---

    @Test
    fun editAddActl() {
        val original = minimalPng()
        val edited = original.edit {
            actl = PngActl(numFrames = 10u, numPlays = 0u)
        }
        assertNotNull(edited.actl)
        assertEquals(10u, edited.actl!!.numFrames)
        assertEquals(0u, edited.actl!!.numPlays)
    }
}
