package dev.transmute.model.structure.video

import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes
import dev.transmute.model.identify.Brand
import dev.transmute.model.identify.EbmlId
import dev.transmute.model.identify.FourCC
import dev.transmute.model.identify.RiffChunkId
import dev.transmute.model.structure.common.EbmlElement
import dev.transmute.model.structure.common.EbmlElementRef
import dev.transmute.model.structure.common.IsoBmffBox
import dev.transmute.model.structure.common.IsoBmffBoxRef
import dev.transmute.model.structure.common.RiffChunk
import dev.transmute.model.core.ByteLength
import dev.transmute.model.core.ByteOffset
import dev.transmute.model.core.ByteRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests for all video [MediaStructure] implementations.
 */
class VideoFileTests {

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

    // -- Avi --

    @Test
    fun aviFileConstruction() {
        val avih = RiffChunk(RiffChunkId("avih"), 56u, data = ByteArray(56).asBytes())
        val hdrl = RiffChunk(RiffChunkId("LIST"), 68u, formType = RiffChunkId("hdrl"), children = listOf(avih))
        val movi = RiffChunk(RiffChunkId("LIST"), 12u, formType = RiffChunkId("movi"))
        val riff = RiffChunk(RiffChunkId("RIFF"), 100u, formType = RiffChunkId("AVI "), children = listOf(hdrl, movi))
        val file = AviRaw(riff)

        assertNotNull(file.headerList)
        assertNotNull(file.movieList)
        assertEquals(2, file.chunks.size)
    }

    @Test
    fun aviMainHeaderParsing() {
        // Build a 56-byte avih chunk with known values (all LE)
        val d = ByteArray(56)
        // microSecPerFrame at offset 0 = 33333 (30fps) -> LE 0x2535_8200 no, 33333=0x8235
        d[0] = 0x35; d[1] = 0x82.toByte()
        // width at offset 32 = 640 -> LE 0x0280
        d[32] = 0x80.toByte(); d[33] = 0x02
        // height at offset 36 = 480 -> LE 0x01E0
        d[36] = 0xE0.toByte(); d[37] = 0x01

        val avih = RiffChunk(RiffChunkId("avih"), 56u, data = d.asBytes())
        val hdrl = RiffChunk(RiffChunkId("LIST"), 68u, formType = RiffChunkId("hdrl"), children = listOf(avih))
        val riff = RiffChunk(RiffChunkId("RIFF"), 80u, formType = RiffChunkId("AVI "), children = listOf(hdrl))
        val file = AviRaw(riff)

        val hdr = file.mainHeader
        assertNotNull(hdr)
        assertEquals(640u, hdr.width)
        assertEquals(480u, hdr.height)
    }

    // -- Mkv --

    @Test
    fun mkvFileConstruction() {
        val docTypeData = "matroska".encodeToByteArray().asBytes()
        val docTypeElem = EbmlElement(id = MatroskaIds.DocType, data = docTypeData)
        val versionData = byteArrayOf(4).asBytes()
        val versionElem = EbmlElement(id = MatroskaIds.DocTypeVersion, data = versionData)
        val ebmlHeader = EbmlElement(id = MatroskaIds.EBML, children = listOf(docTypeElem, versionElem))
        val segment = EbmlElement(id = MatroskaIds.Segment)

        val file = MkvRaw(elements = listOf(ebmlHeader, segment))

        assertNotNull(file.ebmlHeader)
        assertNotNull(file.segment)

        val hdr = file.headerData
        assertNotNull(hdr)
        assertEquals("matroska", hdr.docType)
        assertEquals(4, hdr.docTypeVersion)
    }

    @Test
    fun mkvEmptyFile() {
        val file = MkvRaw(elements = emptyList())
        assertEquals(0, file.toBytes().data.size)
    }

    // -- Mov --

    @Test
    fun movFileWithFtyp() {
        val ftypData = buildFtypData("qt  ", 0, listOf("qt  "))
        val file = MovRaw(boxes = listOf(
            IsoBmffBox(FourCC("ftyp"), ftypData),
            IsoBmffBox(FourCC("moov")),
        ))

        assertEquals(Brand(FourCC("qt  ")), file.majorBrand)
        assertEquals(2, file.boxes.size)
        assertNotNull(file.moovBox)
    }

    // -- Mp4 --

    @Test
    fun mp4FileWithFtyp() {
        val ftypData = buildFtypData("isom", 512, listOf("isom", "iso2", "mp41"))
        val file = Mp4Raw(boxes = listOf(IsoBmffBox(FourCC("ftyp"), ftypData)))

        assertEquals(Brand(FourCC("isom")), file.majorBrand)
        assertEquals(512u, file.minorVersion)
        assertEquals(3, file.compatibleBrands.size)
    }

    @Test
    fun mp4FileDataClassEquality() {
        val ftypData = buildFtypData("isom", 512, listOf("isom"))
        val f1 = Mp4Raw(listOf(IsoBmffBox(FourCC("ftyp"), ftypData)))
        val f2 = Mp4Raw(listOf(IsoBmffBox(FourCC("ftyp"), ftypData)))
        assertEquals(f1, f2)
    }

    // -- Webm --

    @Test
    fun webmFileConstruction() {
        val docTypeData = "webm".encodeToByteArray().asBytes()
        val docTypeElem = EbmlElement(id = MatroskaIds.DocType, data = docTypeData)
        val ebmlHeader = EbmlElement(id = MatroskaIds.EBML, children = listOf(docTypeElem))

        val file = WebmRaw(elements = listOf(ebmlHeader))

        val hdr = file.headerData
        assertNotNull(hdr)
        assertEquals("webm", hdr.docType)
    }

    @Test
    fun webmEmptyFile() {
        val file = WebmRaw(elements = emptyList())
        assertEquals(0, file.toBytes().data.size)
    }

    // -- Ref types still work --

    @Test
    fun isoBmffBoxRefNestedChildren() {
        val leaf = IsoBmffBoxRef(FourCC("mdat"), ByteRange(ByteOffset(100), ByteLength(50)))
        val mid = IsoBmffBoxRef(FourCC("trak"), ByteRange(ByteOffset(40), ByteLength(60)), children = listOf(leaf))
        val root = IsoBmffBoxRef(FourCC("moov"), ByteRange(ByteOffset(0), ByteLength(200)), children = listOf(mid))

        assertEquals(1, root.children.size)
        assertEquals(1, root.children[0].children.size)
        assertEquals(FourCC("mdat"), root.children[0].children[0].type)
    }

    @Test
    fun ebmlElementRefNestedChildren() {
        val child = EbmlElementRef(EbmlId(0x1654AE6B), ByteRange(ByteOffset(10), ByteLength(100)))
        val parent = EbmlElementRef(EbmlId(0x18538067), ByteRange(ByteOffset(0), ByteLength(200)), children = listOf(child))

        assertEquals(1, parent.children.size)
        assertEquals(EbmlId(0x1654AE6B), parent.children[0].id)
    }
}
