package dev.transmute.model.structure.image

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.Pixels
import dev.transmute.model.core.asBytes
import dev.transmute.model.identify.Brand
import dev.transmute.model.identify.Endianness
import dev.transmute.model.identify.FourCC
import dev.transmute.model.identify.RiffChunkId
import dev.transmute.model.structure.common.IsoBmffBox
import dev.transmute.model.structure.common.RiffChunk
import dev.transmute.model.structure.image.types.AvifRaw
import dev.transmute.model.structure.image.types.BmpCompression
import dev.transmute.model.structure.image.types.BmpDibHeader
import dev.transmute.model.structure.image.types.BmpFileHeader
import dev.transmute.model.structure.image.types.BmpRaw
import dev.transmute.model.structure.image.types.GifBlock
import dev.transmute.model.structure.image.types.GifDisposalMethod
import dev.transmute.model.structure.image.types.GifLogicalScreenDescriptor
import dev.transmute.model.structure.image.types.GifRaw
import dev.transmute.model.structure.image.types.GifVersion
import dev.transmute.model.structure.image.types.HeifRaw
import dev.transmute.model.structure.image.types.JpegComponent
import dev.transmute.model.structure.image.types.JpegFrame
import dev.transmute.model.structure.image.types.JpegMarkerType
import dev.transmute.model.structure.image.types.JpegRaw
import dev.transmute.model.structure.image.types.JpegSegment
import dev.transmute.model.structure.image.types.parseSofData
import dev.transmute.model.structure.image.types.TiffFieldType
import dev.transmute.model.structure.image.types.TiffRaw
import dev.transmute.model.structure.image.types.TiffTag
import dev.transmute.model.structure.image.types.WebpFormat
import dev.transmute.model.structure.image.types.WebpRaw
import dev.transmute.model.structure.image.types.bitsPerPixel
import dev.transmute.model.structure.image.types.chunks
import dev.transmute.model.structure.image.types.compatibleBrands
import dev.transmute.model.structure.image.types.compression
import dev.transmute.model.structure.image.types.format
import dev.transmute.model.structure.image.types.frameCount
import dev.transmute.model.structure.image.types.ftypBox
import dev.transmute.model.structure.image.types.height
import dev.transmute.model.structure.image.types.isAnimated
import dev.transmute.model.structure.image.types.isTopDown
import dev.transmute.model.structure.image.types.majorBrand
import dev.transmute.model.structure.image.types.sofData
import dev.transmute.model.structure.image.types.width
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for all image [MediaStructure] implementations except [Png]
 * (which has its own dedicated test class).
 */
class ImageFileTests {

    // -- helpers --

    private fun buildFtypData(major: String, minorVer: Int, compat: List<String>): Bytes {
        val out = ByteArray(8 + compat.size * 4)
        major.encodeToByteArray().copyInto(out, 0)
        out[4] = ((minorVer shr 24) and 0xFF).toByte()
        out[5] = ((minorVer shr 16) and 0xFF).toByte()
        out[6] = ((minorVer shr 8) and 0xFF).toByte()
        out[7] = (minorVer and 0xFF).toByte()
        var pos = 8
        for (b in compat) { b.encodeToByteArray().copyInto(out, pos); pos += 4 }
        return out.asBytes()
    }

    // -- Bmp --

    @Test
    fun bmpFileConstructionAndToBytes() {
        val fh = BmpFileHeader(fileSize = 70u, dataOffset = 54u)
        val dh = BmpDibHeader(headerSize = 40u, width = 640, height = 480, bitsPerPixel = 24u.toUShort())
        val file = BmpRaw(fileHeader = fh, dibHeader = dh, pixelData = ByteArray(16).asBytes())

        assertEquals(Pixels(640), file.width)
        assertEquals(Pixels(480), file.height)
        assertEquals(24, file.bitsPerPixel)
        assertEquals(BmpCompression.Rgb, file.compression)

        val bytes = file.toBytes()
        assertEquals(0x42, bytes.data[0].toInt()) // 'B'
        assertEquals(0x4D, bytes.data[1].toInt()) // 'M'
    }

    @Test
    fun bmpCompressionEnum() {
        assertEquals(7, BmpCompression.entries.size)
        assertEquals(0u, BmpCompression.Rgb.code)
        assertEquals(BmpCompression.Bitfields, BmpCompression.fromCode(3u))
        assertNull(BmpCompression.fromCode(99u))
    }

    @Test
    fun bmpIsTopDown() {
        val fh = BmpFileHeader(fileSize = 54u, dataOffset = 54u)
        val topDown =
            BmpRaw(fh, BmpDibHeader(40u, 1, -1, bitsPerPixel = 24u.toUShort()), pixelData = Bytes(ByteArray(0)))
        val bottomUp =
            BmpRaw(fh, BmpDibHeader(40u, 1, 1, bitsPerPixel = 24u.toUShort()), pixelData = Bytes(ByteArray(0)))
        assertTrue(topDown.isTopDown)
        assertFalse(bottomUp.isTopDown)
    }

    @Test
    fun bmpDibHeaderCopy() {
        val h = BmpDibHeader(headerSize = 40u, width = 640, height = 480, bitsPerPixel = 24u.toUShort())
        val h2 = h.copy(width = 800)
        assertEquals(800, h2.width)
        assertEquals(480, h2.height)
    }

    @Test
    fun bmpFileHeaderConstant() {
        assertEquals(14, BmpFileHeader.SIZE)
    }

    // -- Gif --

    @Test
    fun gifFileConstruction() {
        val sd = GifLogicalScreenDescriptor(
            100u.toUShort(), 100u.toUShort(),
            0x87u.toUByte(), 0u.toUByte(), 0u.toUByte(),
        )
        val file = GifRaw(GifVersion.Gif89a, sd)

        assertEquals(Pixels(100), file.width)
        assertEquals(Pixels(100), file.height)
        assertTrue(sd.hasGlobalColorTable)
        assertEquals(0, file.frameCount)
        assertFalse(file.isAnimated)
    }

    @Test
    fun gifFileToBytesSignature() {
        val sd = GifLogicalScreenDescriptor(1u.toUShort(), 1u.toUShort(), 0u.toUByte(), 0u.toUByte(), 0u.toUByte())
        val bytes = GifRaw(GifVersion.Gif89a, sd).toBytes()
        assertEquals("GIF89a", bytes.data.decodeToString(0, 6))
        // trailer byte
        assertEquals(0x3B, bytes.data.last().toInt())
    }

    @Test
    fun gifVersionEnum() {
        assertEquals(2, GifVersion.entries.size)
        assertEquals("GIF87a", GifVersion.Gif87a.signature)
        assertEquals(GifVersion.Gif89a, GifVersion.fromSignature("GIF89a"))
        assertNull(GifVersion.fromSignature("GIF90a"))
    }

    @Test
    fun gifDisposalMethodEnum() {
        assertEquals(4, GifDisposalMethod.entries.size)
        assertEquals(GifDisposalMethod.RestoreToBackground, GifDisposalMethod.fromCode(2))
    }

    @Test
    fun gifFrameCount() {
        val sd = GifLogicalScreenDescriptor(1u.toUShort(), 1u.toUShort(), 0u.toUByte(), 0u.toUByte(), 0u.toUByte())
        val blocks = listOf(
            GifBlock(0x2Cu.toUByte()), // image block
            GifBlock(0x2Cu.toUByte()), // another image block
        )
        val file = GifRaw(GifVersion.Gif89a, sd, blocks = blocks)
        assertEquals(2, file.frameCount)
        assertTrue(file.isAnimated)
    }

    // -- Jpeg --

    @Test
    fun jpegFileMinimal() {
        val soi = JpegSegment(marker = 0xD8u.toUByte())
        val eoi = JpegSegment(marker = 0xD9u.toUByte())
        val file = JpegRaw(
            headerSegments = listOf(soi),
            trailerSegments = listOf(eoi),
        )

        val bytes = file.toBytes()
        assertEquals(0xFF.toByte(), bytes.data[0])
        assertEquals(0xD8.toByte(), bytes.data[1])
        assertEquals(0xFF.toByte(), bytes.data[2])
        assertEquals(0xD9.toByte(), bytes.data[3])
    }

    @Test
    fun jpegMarkerTypeEnum() {
        assertEquals(JpegMarkerType.SOF0, JpegMarkerType.fromCode(0xC0u.toUByte()))
        assertTrue(JpegMarkerType.isStandalone(0xD8u.toUByte()))
        assertFalse(JpegMarkerType.isStandalone(0xC0u.toUByte()))
    }

    @Test
    fun jpegSofAccessor() {
        val sofData = byteArrayOf(
            8,                           // precision
            0x04, 0x38.toByte(),         // height = 1080
            0x07, 0x80.toByte(),         // width  = 1920
            3,                           // numComponents
            1, 0x22, 0,                  // comp 1
            2, 0x11, 1,                  // comp 2
            3, 0x11, 1,                  // comp 3
        ).asBytes()
        val soi = JpegSegment(marker = 0xD8u.toUByte())
        val sof = JpegSegment(marker = 0xC0u.toUByte(), data = sofData)
        val eoi = JpegSegment(marker = 0xD9u.toUByte())
        val file = JpegRaw(
            headerSegments = listOf(soi),
            frame = JpegFrame(
                sofMarker = 0xC0u.toUByte(),
                sofSegment = sof,
                sofData = parseSofData(sofData.data),
            ),
            trailerSegments = listOf(eoi),
        )

        val sofInfo = file.sofData
        assertNotNull(sofInfo)
        assertEquals(1920.toUShort(), sofInfo.width)
        assertEquals(1080.toUShort(), sofInfo.height)
        assertEquals(8, sofInfo.precision)
        assertEquals(3, sofInfo.components.size)
    }

    @Test
    fun jpegComponentDataClassEquality() {
        assertEquals(JpegComponent(1, 2, 2, 0), JpegComponent(1, 2, 2, 0))
    }

    // -- Heif --

    @Test
    fun heifFileWithFtyp() {
        val ftypData = buildFtypData("heic", 0, listOf("heic", "mif1"))
        val file = HeifRaw(boxes = listOf(IsoBmffBox(FourCC("ftyp"), ftypData)))

        assertNotNull(file.ftypBox)
        assertEquals(Brand(FourCC("heic")), file.majorBrand)
        assertEquals(2, file.compatibleBrands.size)

        val bytes = file.toBytes()
        assertTrue(bytes.data.isNotEmpty())
    }

    // -- Avif --

    @Test
    fun avifFileWithFtyp() {
        val ftypData = buildFtypData("avif", 0, listOf("avif", "mif1"))
        val file = AvifRaw(boxes = listOf(IsoBmffBox(FourCC("ftyp"), ftypData)))

        assertEquals(Brand(FourCC("avif")), file.majorBrand)
        assertTrue(file.toBytes().data.isNotEmpty())
    }

    // -- Tiff --

    @Test
    fun tiffFileLittleEndian() {
        val file = TiffRaw(byteOrder = Endianness.Little, firstIfdOffset = 8u, imageData = Bytes(ByteArray(0)))
        val bytes = file.toBytes()
        assertEquals(0x49, bytes.data[0].toInt())
        assertEquals(0x49, bytes.data[1].toInt())
        assertEquals(42, bytes.data[2].toInt())
        assertEquals(0, bytes.data[3].toInt())
    }

    @Test
    fun tiffFileBigEndian() {
        val file = TiffRaw(byteOrder = Endianness.Big, firstIfdOffset = 8u, imageData = Bytes(ByteArray(0)))
        val bytes = file.toBytes()
        assertEquals(0x4D, bytes.data[0].toInt())
        assertEquals(0x4D, bytes.data[1].toInt())
        assertEquals(0, bytes.data[2].toInt())
        assertEquals(42, bytes.data[3].toInt())
    }

    @Test
    fun tiffFieldTypeEnum() {
        assertEquals(12, TiffFieldType.entries.size)
        assertEquals(2, TiffFieldType.Short.bytesPerValue)
        assertEquals(TiffFieldType.Long, TiffFieldType.fromCode(4u.toUShort()))
    }

    @Test
    fun tiffTagEnum() {
        assertTrue(TiffTag.entries.size >= 18)
        assertEquals(256u.toUShort(), TiffTag.ImageWidth.code)
        assertEquals(TiffTag.Compression, TiffTag.fromCode(259u.toUShort()))
    }

    // -- Webp --

    @Test
    fun webpFileConstruction() {
        val vp8 = RiffChunk(id = RiffChunkId("VP8 "), size = 10u, data = ByteArray(10).asBytes())
        val riff = RiffChunk(id = RiffChunkId("RIFF"), size = 22u, formType = RiffChunkId("WEBP"), children = listOf(vp8))
        val file = WebpRaw(riff)

        assertEquals(WebpFormat.Lossy, file.format)
        assertEquals(1, file.chunks.size)
    }

    @Test
    fun webpFormatEnum() {
        assertEquals(3, WebpFormat.entries.size)
        assertEquals(WebpFormat.Lossy, WebpFormat.fromChunkId(RiffChunkId("VP8 ")))
        assertEquals(WebpFormat.Lossless, WebpFormat.fromChunkId(RiffChunkId("VP8L")))
        assertEquals(WebpFormat.Extended, WebpFormat.fromChunkId(RiffChunkId("VP8X")))
    }
}
