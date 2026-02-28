package dev.transmute.model.structure.image

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes
import dev.transmute.model.identify.FourCC
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// --- CRC-32 helper (ISO 3309 / PNG spec) ---

private val crcTable: IntArray = IntArray(256) { n ->
    var c = n
    repeat(8) {
        c = if (c and 1 != 0) (0xEDB88320.toInt() xor (c ushr 1)) else (c ushr 1)
    }
    c
}

private fun crc32(data: ByteArray): UInt {
    var crc = 0xFFFFFFFF.toInt()
    for (b in data) crc = crcTable[(crc xor b.toInt()) and 0xFF] xor (crc ushr 8)
    return (crc xor 0xFFFFFFFF.toInt()).toUInt()
}

// --- Chunk builder helpers ---

private fun UInt.be(): ByteArray = byteArrayOf(
    (this shr 24).toByte(),
    (this shr 16).toByte(),
    (this shr 8).toByte(),
    this.toByte(),
)

private fun UShort.be(): ByteArray = byteArrayOf(
    (this.toInt() shr 8).toByte(),
    this.toByte(),
)

/**
 * Builds the raw bytes of a single PNG chunk and returns a [PngChunk].
 */
private fun buildChunk(type: String, data: ByteArray = ByteArray(0)): PngChunk {
    val typeBytes = type.encodeToByteArray()
    val crc = crc32(typeBytes + data)
    return PngChunk(
        length = data.size.toUInt(),
        type = FourCC(type),
        data = data.asBytes(),
        crc = crc,
    )
}

/**
 * Builds a valid 13-byte IHDR data payload.
 */
private fun ihdrData(
    width: UInt = 8u,
    height: UInt = 8u,
    bitDepth: UByte = 8u,
    colorType: Int = 2, // RGB
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

/**
 * Creates a minimal Png with IHDR + IEND.
 */
private fun minimalPng(
    width: UInt = 8u,
    height: UInt = 8u,
    colorType: Int = 2,
    extraChunks: List<PngChunk> = emptyList(),
): PngRaw {
    val ihdr = buildChunk("IHDR", ihdrData(width, height, colorType = colorType))
    val iend = buildChunk("IEND")
    return PngRaw(
        signature = Bytes(PngRaw.SIGNATURE.copyOf()),
        chunks = listOf(ihdr) + extraChunks + iend,
    )
}

// --- Tests ---

class PngFileTest {

    // --- PngChunk ---

    @Test
    fun pngChunkToBytesProducesCorrectLayout() {
        val data = byteArrayOf(0x01, 0x02, 0x03)
        val chunk = buildChunk("tEXt", data)
        val bytes = chunk.toBytes().data

        // 4 (length) + 4 (type) + 3 (data) + 4 (crc) = 15 bytes
        assertEquals(15, bytes.size)

        // Length = 3, big-endian
        assertContentEquals(3u.be(), bytes.sliceArray(0..3))

        // Type = "tEXt"
        assertEquals("tEXt", bytes.sliceArray(4..7).decodeToString())

        // Data
        assertContentEquals(data, bytes.sliceArray(8..10))

        // CRC
        assertContentEquals(chunk.crc.be(), bytes.sliceArray(11..14))
    }

    // --- PngIhdr ---

    @Test
    fun ihdrAccessorParsesCorrectly() {
        val png = minimalPng(width = 320u, height = 240u)

        val ihdr = png.ihdr
        assertEquals(320u, ihdr.width)
        assertEquals(240u, ihdr.height)
        assertEquals(8.toUByte(), ihdr.bitDepth)
        assertEquals(PngColorType.Rgb, ihdr.colorType)
        assertEquals(PngInterlaceMethod.None, ihdr.interlaceMethod)
    }

    @Test
    fun ihdrBitsPerPixelRgb() {
        val png = minimalPng(colorType = 2) // RGB, 8 bit
        assertEquals(24, png.ihdr.bitsPerPixel) // 8 × 3
    }

    @Test
    fun ihdrBitsPerPixelRgba() {
        val png = minimalPng(colorType = 6) // RGBA, 8 bit
        assertEquals(32, png.ihdr.bitsPerPixel) // 8 × 4
    }

    @Test
    fun ihdrToBytesRoundTrip() {
        val original = PngIhdr(
            width = 100u, height = 200u, bitDepth = 16u,
            colorType = PngColorType.RgbAlpha,
            compressionMethod = 0u, filterMethod = 0u,
            interlaceMethod = PngInterlaceMethod.Adam7,
        )
        val bytes = original.toBytes().data
        assertEquals(13, bytes.size)

        // Re-parse through a Png accessor
        val chunk = buildChunk("IHDR", bytes)
        val png = PngRaw(Bytes(PngRaw.SIGNATURE.copyOf()), listOf(chunk, buildChunk("IEND")))
        val parsed = png.ihdr

        assertEquals(original.width, parsed.width)
        assertEquals(original.height, parsed.height)
        assertEquals(original.bitDepth, parsed.bitDepth)
        assertEquals(original.colorType, parsed.colorType)
        assertEquals(original.interlaceMethod, parsed.interlaceMethod)
    }

    // --- PngIend ---

    @Test
    fun iendAccessorReturnsSingleton() {
        val png = minimalPng()
        assertTrue(png.iend === PngIend)
    }

    @Test
    fun iendToBytesIsEmpty() {
        assertEquals(0, PngIend.toBytes().size)
    }

    // --- PLTE ---

    @Test
    fun plteAccessorReturnsNullWhenMissing() {
        val png = minimalPng()
        assertNull(png.plte)
    }

    @Test
    fun plteAccessorParsesEntries() {
        val paletteData = byteArrayOf(
            0xFF.toByte(), 0x00, 0x00,  // red
            0x00, 0xFF.toByte(), 0x00,  // green
            0x00, 0x00, 0xFF.toByte(),  // blue
        )
        val plteChunk = buildChunk("PLTE", paletteData)
        val png = minimalPng(colorType = 3, extraChunks = listOf(plteChunk))

        val plte = png.plte
        assertNotNull(plte)
        assertEquals(3, plte.entries.size)
        assertEquals(0xFF.toUByte(), plte.entries[0].red)
        assertEquals(0x00.toUByte(), plte.entries[0].green)
        assertEquals(0xFF.toUByte(), plte.entries[2].blue)
    }

    @Test
    fun plteToBytesRoundTrip() {
        val entries = listOf(
            PngPlteEntry(10u, 20u, 30u),
            PngPlteEntry(40u, 50u, 60u),
        )
        val plte = PngPlte(entries)
        val bytes = plte.toBytes().data
        assertEquals(6, bytes.size)

        // Verify re-parse
        val chunk = buildChunk("PLTE", bytes)
        val png = minimalPng(extraChunks = listOf(chunk))
        val parsed = png.plte!!
        assertEquals(2, parsed.entries.size)
        assertEquals(entries[0], parsed.entries[0])
        assertEquals(entries[1], parsed.entries[1])
    }

    // --- IDAT ---

    @Test
    fun idatChunksCollectsAll() {
        val d1 = byteArrayOf(1, 2, 3)
        val d2 = byteArrayOf(4, 5)
        val png = minimalPng(
            extraChunks = listOf(
                buildChunk("IDAT", d1),
                buildChunk("IDAT", d2),
            ),
        )

        assertEquals(2, png.idatChunks.size)
        assertContentEquals(d1, png.idatChunks[0].compressedData.data)
        assertContentEquals(d2, png.idatChunks[1].compressedData.data)
    }

    @Test
    fun compressedImageDataConcatenatesIdatChunks() {
        val d1 = byteArrayOf(1, 2, 3)
        val d2 = byteArrayOf(4, 5)
        val png = minimalPng(
            extraChunks = listOf(
                buildChunk("IDAT", d1),
                buildChunk("IDAT", d2),
            ),
        )

        val combined = png.compressedImageData.data
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5), combined)
    }

    // --- gAMA ---

    @Test
    fun gamaAccessorParsesCorrectly() {
        // gamma = 1/2.2 → 45455
        val gammaBytes = 45455u.be()
        val png = minimalPng(extraChunks = listOf(buildChunk("gAMA", gammaBytes)))

        val gama = png.gama
        assertNotNull(gama)
        assertEquals(45455u, gama.gamma)
        assertTrue(gama.gammaValue > 0.45 && gama.gammaValue < 0.46)
    }

    @Test
    fun gamaReturnsNullWhenMissing() {
        assertNull(minimalPng().gama)
    }

    @Test
    fun gamaToBytesRoundTrip() {
        val original = PngGama(55000u)
        val bytes = original.toBytes().data
        assertEquals(4, bytes.size)

        val chunk = buildChunk("gAMA", bytes)
        val png = minimalPng(extraChunks = listOf(chunk))
        assertEquals(55000u, png.gama!!.gamma)
    }

    // --- sRGB ---

    @Test
    fun srgbAccessorParsesCorrectly() {
        val png = minimalPng(extraChunks = listOf(buildChunk("sRGB", byteArrayOf(1))))
        val srgb = png.srgb
        assertNotNull(srgb)
        assertEquals(PngRenderingIntent.RelativeColorimetric, srgb.renderingIntent)
    }

    @Test
    fun srgbToBytesRoundTrip() {
        val original = PngSrgb(PngRenderingIntent.Saturation)
        val bytes = original.toBytes().data
        assertEquals(1, bytes.size)

        val chunk = buildChunk("sRGB", bytes)
        val png = minimalPng(extraChunks = listOf(chunk))
        assertEquals(PngRenderingIntent.Saturation, png.srgb!!.renderingIntent)
    }

    // --- pHYs ---

    @Test
    fun physAccessorParsesCorrectly() {
        val data = ByteArray(9)
        3780u.be().copyInto(data, 0) // ~96 DPI
        3780u.be().copyInto(data, 4)
        data[8] = 1 // metre
        val png = minimalPng(extraChunks = listOf(buildChunk("pHYs", data)))

        val phys = png.phys
        assertNotNull(phys)
        assertEquals(3780u, phys.pixelsPerUnitX)
        assertEquals(3780u, phys.pixelsPerUnitY)
        assertTrue(phys.isMetric)
    }

    @Test
    fun physToBytesRoundTrip() {
        val original = PngPhys(2835u, 2835u, 1u)
        val bytes = original.toBytes().data
        assertEquals(9, bytes.size)

        val chunk = buildChunk("pHYs", bytes)
        val png = minimalPng(extraChunks = listOf(chunk))
        val parsed = png.phys!!
        assertEquals(original.pixelsPerUnitX, parsed.pixelsPerUnitX)
        assertEquals(original.pixelsPerUnitY, parsed.pixelsPerUnitY)
        assertEquals(original.unitSpecifier, parsed.unitSpecifier)
    }

    // --- tIME ---

    @Test
    fun timeAccessorParsesCorrectly() {
        val data = ByteArray(7)
        2025.toUShort().be().copyInto(data, 0)
        data[2] = 6 // June
        data[3] = 15 // 15th
        data[4] = 10 // 10:00
        data[5] = 30 // :30
        data[6] = 45 // :45
        val png = minimalPng(extraChunks = listOf(buildChunk("tIME", data)))

        val time = png.time
        assertNotNull(time)
        assertEquals(2025.toUShort(), time.year)
        assertEquals(6.toUByte(), time.month)
        assertEquals(15.toUByte(), time.day)
        assertEquals(10.toUByte(), time.hour)
        assertEquals(30.toUByte(), time.minute)
        assertEquals(45.toUByte(), time.second)
    }

    @Test
    fun timeToBytesRoundTrip() {
        val original = PngTime(2024u, 12u, 25u, 23u, 59u, 59u)
        val bytes = original.toBytes().data
        assertEquals(7, bytes.size)

        val chunk = buildChunk("tIME", bytes)
        val png = minimalPng(extraChunks = listOf(chunk))
        val parsed = png.time!!
        assertEquals(original.year, parsed.year)
        assertEquals(original.month, parsed.month)
        assertEquals(original.day, parsed.day)
    }

    // --- tEXt ---

    @Test
    fun textChunksAccessorParsesCorrectly() {
        val keyword = "Comment"
        val text = "Hello, world!"
        val data = keyword.encodeToByteArray() + byteArrayOf(0) + text.encodeToByteArray()
        val png = minimalPng(extraChunks = listOf(buildChunk("tEXt", data)))

        val chunks = png.textChunks
        assertEquals(1, chunks.size)
        assertEquals("Comment", chunks[0].keyword)
        assertEquals("Hello, world!", chunks[0].text)
    }

    @Test
    fun textChunkToBytesRoundTrip() {
        val original = PngTextChunk("Author", "Transmute")
        val bytes = original.toBytes().data

        val chunk = buildChunk("tEXt", bytes)
        val png = minimalPng(extraChunks = listOf(chunk))
        val parsed = png.textChunks[0]
        assertEquals(original.keyword, parsed.keyword)
        assertEquals(original.text, parsed.text)
    }

    // --- zTXt ---

    @Test
    fun ztxtChunksAccessorParsesCorrectly() {
        val kw = "Comment".encodeToByteArray()
        val compressed = byteArrayOf(0x78, 0x01, 0x02) // fake compressed data
        val data = kw + byteArrayOf(0) + byteArrayOf(0) + compressed
        val png = minimalPng(extraChunks = listOf(buildChunk("zTXt", data)))

        val chunks = png.ztxtChunks
        assertEquals(1, chunks.size)
        assertEquals("Comment", chunks[0].keyword)
        assertEquals(0.toUByte(), chunks[0].compressionMethod)
        assertContentEquals(compressed, chunks[0].compressedText.data)
    }

    @Test
    fun ztxtToBytesRoundTrip() {
        val original = PngZtxt("Title", 0u, byteArrayOf(1, 2, 3).asBytes())
        val bytes = original.toBytes().data

        val chunk = buildChunk("zTXt", bytes)
        val png = minimalPng(extraChunks = listOf(chunk))
        val parsed = png.ztxtChunks[0]
        assertEquals(original.keyword, parsed.keyword)
        assertEquals(original.compressionMethod, parsed.compressionMethod)
        assertContentEquals(original.compressedText.data, parsed.compressedText.data)
    }

    // --- iTXt ---

    @Test
    fun itxtChunksAccessorParsesCorrectly() {
        val kw = "Title".encodeToByteArray()
        val lang = "en".encodeToByteArray()
        val trKw = "English Title".encodeToByteArray()
        val text = "My Image".encodeToByteArray()
        val data = kw + byteArrayOf(0, 0, 0) + lang + byteArrayOf(0) + trKw + byteArrayOf(0) + text
        val png = minimalPng(extraChunks = listOf(buildChunk("iTXt", data)))

        val chunks = png.itxtChunks
        assertEquals(1, chunks.size)
        assertEquals("Title", chunks[0].keyword)
        assertEquals(0.toUByte(), chunks[0].compressionFlag)
        assertEquals(0.toUByte(), chunks[0].compressionMethod)
        assertEquals("en", chunks[0].languageTag)
        assertEquals("English Title", chunks[0].translatedKeyword)
        assertEquals("My Image", chunks[0].text)
    }

    @Test
    fun itxtToBytesRoundTrip() {
        val original = PngItxt("Desc", 0u, 0u, "de", "Beschreibung", "Ein Bild")
        val bytes = original.toBytes().data

        val chunk = buildChunk("iTXt", bytes)
        val png = minimalPng(extraChunks = listOf(chunk))
        val parsed = png.itxtChunks[0]
        assertEquals(original.keyword, parsed.keyword)
        assertEquals(original.languageTag, parsed.languageTag)
        assertEquals(original.translatedKeyword, parsed.translatedKeyword)
        assertEquals(original.text, parsed.text)
    }

    // --- tRNS ---

    @Test
    fun trnsIndexedAccessor() {
        // Indexed color (type 3): alpha values for each palette entry
        val alphas = byteArrayOf(0xFF.toByte(), 0x80.toByte(), 0x00)
        val plteData = ByteArray(3 * 3) // 3 palette entries
        val png = minimalPng(
            colorType = 3,
            extraChunks = listOf(buildChunk("PLTE", plteData), buildChunk("tRNS", alphas)),
        )

        val trns = png.trns
        assertNotNull(trns)
        assertNotNull(trns.alphaEntries)
        assertEquals(3, trns.alphaEntries!!.size)
        assertEquals(0xFF.toUByte(), trns.alphaEntries!![0])
    }

    @Test
    fun trnsGrayscaleAccessor() {
        val data = 128.toUShort().be()
        val png = minimalPng(
            colorType = 0, // Grayscale
            extraChunks = listOf(buildChunk("tRNS", data)),
        )
        val trns = png.trns
        assertNotNull(trns)
        assertEquals(128.toUShort(), trns.greySample)
    }

    // --- sBIT ---

    @Test
    fun sbitAccessorParsesCorrectly() {
        val data = byteArrayOf(5, 6, 5) // 5-6-5 significant bits (RGB)
        val png = minimalPng(extraChunks = listOf(buildChunk("sBIT", data)))

        val sbit = png.sbit
        assertNotNull(sbit)
        assertEquals(3, sbit.significantBits.size)
        assertEquals(5.toUByte(), sbit.significantBits[0])
        assertEquals(6.toUByte(), sbit.significantBits[1])
    }

    @Test
    fun sbitToBytesRoundTrip() {
        val original = PngSbit(listOf(8u, 8u, 8u, 8u))
        val bytes = original.toBytes().data

        val chunk = buildChunk("sBIT", bytes)
        val png = minimalPng(extraChunks = listOf(chunk))
        assertEquals(original.significantBits, png.sbit!!.significantBits)
    }

    // --- bKGD ---

    @Test
    fun bkgdIndexedAccessor() {
        val plteData = ByteArray(3 * 4) // 4 entries
        val bkgdData = byteArrayOf(2) // palette index 2
        val png = minimalPng(
            colorType = 3,
            extraChunks = listOf(buildChunk("PLTE", plteData), buildChunk("bKGD", bkgdData)),
        )
        val bkgd = png.bkgd
        assertNotNull(bkgd)
        assertEquals(2.toUByte(), bkgd.paletteIndex)
    }

    @Test
    fun bkgdRgbAccessor() {
        val data = ByteArray(6)
        100.toUShort().be().copyInto(data, 0)
        200.toUShort().be().copyInto(data, 2)
        50.toUShort().be().copyInto(data, 4)
        val png = minimalPng(colorType = 2, extraChunks = listOf(buildChunk("bKGD", data)))

        val bkgd = png.bkgd
        assertNotNull(bkgd)
        assertEquals(100.toUShort(), bkgd.red)
        assertEquals(200.toUShort(), bkgd.green)
        assertEquals(50.toUShort(), bkgd.blue)
    }

    // --- hIST ---

    @Test
    fun histAccessorParsesCorrectly() {
        val data = ByteArray(4) // 2 frequencies
        1000.toUShort().be().copyInto(data, 0)
        2000.toUShort().be().copyInto(data, 2)
        val png = minimalPng(extraChunks = listOf(buildChunk("hIST", data)))

        val hist = png.hist
        assertNotNull(hist)
        assertEquals(2, hist.frequencies.size)
        assertEquals(1000.toUShort(), hist.frequencies[0])
        assertEquals(2000.toUShort(), hist.frequencies[1])
    }

    // --- acTL (APNG) ---

    @Test
    fun actlAccessorParsesCorrectly() {
        val data = ByteArray(8)
        10u.be().copyInto(data, 0)  // numFrames
        0u.be().copyInto(data, 4)   // numPlays (infinite)
        val png = minimalPng(extraChunks = listOf(buildChunk("acTL", data)))

        val actl = png.actl
        assertNotNull(actl)
        assertEquals(10u, actl.numFrames)
        assertEquals(0u, actl.numPlays)
    }

    @Test
    fun actlToBytesRoundTrip() {
        val original = PngActl(5u, 3u)
        val bytes = original.toBytes().data
        assertEquals(8, bytes.size)

        val chunk = buildChunk("acTL", bytes)
        val png = minimalPng(extraChunks = listOf(chunk))
        val parsed = png.actl!!
        assertEquals(5u, parsed.numFrames)
        assertEquals(3u, parsed.numPlays)
    }

    // --- fcTL (APNG) ---

    @Test
    fun fctlAccessorParsesCorrectly() {
        val data = ByteArray(26)
        var pos = 0
        0u.be().copyInto(data, pos); pos += 4   // sequenceNumber
        320u.be().copyInto(data, pos); pos += 4  // width
        240u.be().copyInto(data, pos); pos += 4  // height
        0u.be().copyInto(data, pos); pos += 4    // xOffset
        0u.be().copyInto(data, pos); pos += 4    // yOffset
        1.toUShort().be().copyInto(data, pos); pos += 2 // delayNum
        30.toUShort().be().copyInto(data, pos); pos += 2 // delayDen
        data[pos++] = 0 // disposeOp = None
        data[pos] = 1   // blendOp = Over

        val png = minimalPng(extraChunks = listOf(buildChunk("fcTL", data)))
        val fctl = png.fctlChunks
        assertEquals(1, fctl.size)
        assertEquals(320u, fctl[0].width)
        assertEquals(240u, fctl[0].height)
        assertEquals(PngDisposeOp.None, fctl[0].disposeOp)
        assertEquals(PngBlendOp.Over, fctl[0].blendOp)
    }

    @Test
    fun fctlToBytesRoundTrip() {
        val original = PngFctl(
            sequenceNumber = 0u, width = 100u, height = 100u,
            xOffset = 10u, yOffset = 20u,
            delayNum = 1u, delayDen = 60u,
            disposeOp = PngDisposeOp.Background,
            blendOp = PngBlendOp.Source,
        )
        val bytes = original.toBytes().data
        assertEquals(26, bytes.size)

        val chunk = buildChunk("fcTL", bytes)
        val png = minimalPng(extraChunks = listOf(chunk))
        val parsed = png.fctlChunks[0]
        assertEquals(original.width, parsed.width)
        assertEquals(original.height, parsed.height)
        assertEquals(original.xOffset, parsed.xOffset)
        assertEquals(original.yOffset, parsed.yOffset)
        assertEquals(original.delayNum, parsed.delayNum)
        assertEquals(original.delayDen, parsed.delayDen)
        assertEquals(original.disposeOp, parsed.disposeOp)
        assertEquals(original.blendOp, parsed.blendOp)
    }

    // --- iCCP ---

    @Test
    fun iccpAccessorParsesCorrectly() {
        val name = "sRGB".encodeToByteArray()
        val profile = byteArrayOf(0x78, 0x01, 0x02, 0x03) // fake compressed profile
        val data = name + byteArrayOf(0) + byteArrayOf(0) + profile

        val png = minimalPng(extraChunks = listOf(buildChunk("iCCP", data)))
        val iccp = png.iccp
        assertNotNull(iccp)
        assertEquals("sRGB", iccp.profileName)
        assertEquals(0.toUByte(), iccp.compressionMethod)
        assertContentEquals(profile, iccp.compressedProfile.data)
    }

    @Test
    fun iccpToBytesRoundTrip() {
        val original = PngIccp("MyProfile", 0u, byteArrayOf(1, 2, 3, 4, 5).asBytes())
        val bytes = original.toBytes().data

        val chunk = buildChunk("iCCP", bytes)
        val png = minimalPng(extraChunks = listOf(chunk))
        val parsed = png.iccp!!
        assertEquals(original.profileName, parsed.profileName)
        assertEquals(original.compressionMethod, parsed.compressionMethod)
        assertContentEquals(original.compressedProfile.data, parsed.compressedProfile.data)
    }

    // --- cHRM ---

    @Test
    fun chrmToBytesRoundTrip() {
        val original = PngChrm(
            whitePointX = 31270u, whitePointY = 32900u,
            redX = 64000u, redY = 33000u,
            greenX = 30000u, greenY = 60000u,
            blueX = 15000u, blueY = 6000u,
        )
        val bytes = original.toBytes().data
        assertEquals(32, bytes.size)

        val chunk = buildChunk("cHRM", bytes)
        val png = minimalPng(extraChunks = listOf(chunk))
        val parsed = png.chrm!!
        assertEquals(original.whitePointX, parsed.whitePointX)
        assertEquals(original.whitePointY, parsed.whitePointY)
        assertEquals(original.redX, parsed.redX)
        assertEquals(original.blueY, parsed.blueY)
    }

    // --- PngChunkType enum ---

    @Test
    fun chunkTypeFromTagFindsKnownType() {
        assertEquals(PngChunkType.IHDR, PngChunkType.fromTag("IHDR"))
        assertEquals(PngChunkType.tEXt, PngChunkType.fromTag("tEXt"))
        assertEquals(PngChunkType.acTL, PngChunkType.fromTag("acTL"))
    }

    @Test
    fun chunkTypeFromTagReturnsNullForUnknown() {
        assertNull(PngChunkType.fromTag("xYzZ"))
    }

    @Test
    fun chunkTypeCriticalFlag() {
        assertTrue(PngChunkType.IHDR.isCritical)
        assertTrue(PngChunkType.IDAT.isCritical)
        assertTrue(!PngChunkType.tEXt.isCritical)
        assertTrue(!PngChunkType.gAMA.isCritical)
    }

    @Test
    fun chunkTypeFourCC() {
        assertEquals(FourCC("IHDR"), PngChunkType.IHDR.fourCC)
        assertEquals(FourCC("tEXt"), PngChunkType.tEXt.fourCC)
    }

    // --- PngColorType ---

    @Test
    fun colorTypeChannelCounts() {
        assertEquals(1, PngColorType.Grayscale.channelCount)
        assertEquals(3, PngColorType.Rgb.channelCount)
        assertEquals(1, PngColorType.Indexed.channelCount)
        assertEquals(2, PngColorType.GrayscaleAlpha.channelCount)
        assertEquals(4, PngColorType.RgbAlpha.channelCount)
    }

    @Test
    fun colorTypeHasAlpha() {
        assertTrue(!PngColorType.Rgb.hasAlpha)
        assertTrue(PngColorType.RgbAlpha.hasAlpha)
        assertTrue(PngColorType.GrayscaleAlpha.hasAlpha)
    }

    @Test
    fun colorTypeFromCode() {
        assertEquals(PngColorType.Rgb, PngColorType.fromCode(2))
        assertEquals(PngColorType.RgbAlpha, PngColorType.fromCode(6))
        assertNull(PngColorType.fromCode(99))
    }

    // --- Png.toBytes() — full file serialization ---

    @Test
    fun toBytesStartsWithPngSignature() {
        val png = minimalPng()
        val bytes = png.toBytes().data

        assertContentEquals(PngRaw.SIGNATURE, bytes.sliceArray(0..7))
    }

    @Test
    fun toBytesRoundTripPreservesAllBytes() {
        val idatData = byteArrayOf(0x78, 0x01, 0x63, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01)
        val textData = "Author".encodeToByteArray() + byteArrayOf(0) + "Test".encodeToByteArray()
        val png = minimalPng(
            extraChunks = listOf(
                buildChunk("IDAT", idatData),
                buildChunk("tEXt", textData),
            ),
        )

        val bytes1 = png.toBytes().data
        // Re-assemble from the same chunks — bytes should be identical
        val bytes2 = png.toBytes().data
        assertContentEquals(bytes1, bytes2)
    }

    @Test
    fun toBytesTotalSizeIsCorrect() {
        val idatData = byteArrayOf(1, 2, 3, 4, 5)
        val png = minimalPng(extraChunks = listOf(buildChunk("IDAT", idatData)))
        val bytes = png.toBytes().data

        // 8 (sig) + 3 chunks × (4 len + 4 type + data + 4 crc)
        // IHDR: 12 + 13 = 25
        // IDAT: 12 + 5  = 17
        // IEND: 12 + 0  = 12
        val expected = 8 + 25 + 17 + 12
        assertEquals(expected, bytes.size)
    }

    // --- Data class equality ---

    @Test
    fun pngChunkEqualityByContent() {
        val c1 = buildChunk("tEXt", byteArrayOf(1, 2))
        val c2 = buildChunk("tEXt", byteArrayOf(1, 2))
        // PngChunk uses Bytes which is a value class wrapping ByteArray
        // Data class equality checks all fields including Bytes.data reference
        assertEquals(c1.length, c2.length)
        assertEquals(c1.type, c2.type)
        assertEquals(c1.crc, c2.crc)
    }

    // --- Missing chunks return null ---

    @Test
    fun missingOptionalChunksReturnNull() {
        val png = minimalPng()

        assertNull(png.plte)
        assertNull(png.gama)
        assertNull(png.chrm)
        assertNull(png.srgb)
        assertNull(png.iccp)
        assertNull(png.phys)
        assertNull(png.time)
        assertNull(png.trns)
        assertNull(png.sbit)
        assertNull(png.bkgd)
        assertNull(png.hist)
        assertNull(png.actl)
        assertTrue(png.textChunks.isEmpty())
        assertTrue(png.ztxtChunks.isEmpty())
        assertTrue(png.itxtChunks.isEmpty())
        assertTrue(png.idatChunks.isEmpty())
        assertTrue(png.fctlChunks.isEmpty())
        assertTrue(png.spltChunks.isEmpty())
    }
}
