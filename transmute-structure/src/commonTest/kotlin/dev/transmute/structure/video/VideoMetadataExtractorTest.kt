package dev.transmute.structure.video

import dev.transmute.model.core.Bytes
import dev.transmute.model.identify.EbmlId
import dev.transmute.model.identify.FourCC
import dev.transmute.model.identify.RiffChunkId
import dev.transmute.model.metadata.itunes.ItunesMetadata
import dev.transmute.model.metadata.itunes.ItunesValue
import dev.transmute.model.metadata.matroska.MatroskaTagMetadata
import dev.transmute.model.metadata.riff.RiffInfoMetadata
import dev.transmute.model.structure.common.EbmlElement
import dev.transmute.model.structure.common.IsoBmffBox
import dev.transmute.model.structure.common.RiffChunk
import dev.transmute.model.structure.video.types.AviRaw
import dev.transmute.model.structure.video.types.MatroskaIds
import dev.transmute.model.structure.video.types.MkvRaw
import dev.transmute.model.structure.video.types.MovRaw
import dev.transmute.model.structure.video.types.Mp4Raw
import dev.transmute.model.structure.video.types.WebmRaw
import dev.transmute.structure.audio.extractMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for video format metadata extractors.
 *
 * Covers: WebM (Matroska), MKV (Matroska), MP4 (iTunes), MOV (iTunes), AVI (RIFF INFO).
 */
class VideoMetadataExtractorTest {

  // ===========================================================================
  //  Helpers - EBML tree builders
  // ===========================================================================

  private fun ebmlString(id: EbmlId, value: String) = EbmlElement(
    id = id,
    data = Bytes(value.encodeToByteArray()),
  )

  private fun ebmlMaster(id: EbmlId, children: List<EbmlElement>) = EbmlElement(
    id = id,
    children = children,
  )

  /** Build a full Segment > Tags > Tag hierarchy with one SimpleTag. */
  private fun matroskaWithTag(tagName: String, tagValue: String): List<EbmlElement> {
    val simpleTag = ebmlMaster(
      MatroskaIds.SimpleTag,
      listOf(
        ebmlString(MatroskaIds.TagName, tagName),
        ebmlString(MatroskaIds.TagString, tagValue),
      ),
    )
    val tag = ebmlMaster(MatroskaIds.Tag, listOf(simpleTag))
    val tags = ebmlMaster(MatroskaIds.Tags, listOf(tag))
    val segment = ebmlMaster(MatroskaIds.Segment, listOf(tags))
    return listOf(segment)
  }

  // ===========================================================================
  //  Helpers - ISO BMFF tree builders
  // ===========================================================================

  /**
   * Build a moov > udta > meta > ilst tree with a single iTunes text item.
   *
   * `data` sub-box layout: version(1) + flags(3) + locale(4) + value
   *
   * Note: `meta` is a "full box" in ISO BMFF - the parser skips 4 bytes
   * (version + flags) before looking for child boxes.  So we serialize
   * meta's inner boxes into `data` with a 4-byte prefix.
   */
  private fun isoBmffWithItunesTag(key: String = "\u00A9nam", value: String = "Test Title"): List<IsoBmffBox> {
    val valueBytes = value.encodeToByteArray()
    // data box payload: version=0, flags=1 (UTF-8), locale=0, value
    val dataPayload = byteArrayOf(0, 0, 0, 1, 0, 0, 0, 0) + valueBytes
    val dataBox = IsoBmffBox(type = FourCC("data"), data = Bytes(dataPayload))
    val itemBox = IsoBmffBox(type = FourCC(key), children = listOf(dataBox))
    val ilst = IsoBmffBox(type = FourCC("ilst"), children = listOf(itemBox))
    // meta is a full box: 4 bytes version/flags header + serialized child boxes
    val ilstBytes = ilst.toBytes().data
    val metaPayload = ByteArray(4) + ilstBytes // version=0, flags=0x000000
    val meta = IsoBmffBox(type = FourCC("meta"), data = Bytes(metaPayload))
    val udta = IsoBmffBox(type = FourCC("udta"), children = listOf(meta))
    val moov = IsoBmffBox(type = FourCC("moov"), children = listOf(udta))
    return listOf(moov)
  }

  // ===========================================================================
  //  WebM tests
  // ===========================================================================

  @Test
  fun webmExtractsMatroskaTags() {
    val webm = WebmRaw(elements = matroskaWithTag("TITLE", "My WebM Video"))
    val metadata = webm.extractMetadata()
    assertTrue(metadata.isNotEmpty())
    val tags = metadata.filterIsInstance<MatroskaTagMetadata>().firstOrNull()
    assertNotNull(tags)
    assertEquals(1, tags.tags.size)
    assertEquals("TITLE", tags.tags[0].simpleTags[0].name.value)
    assertEquals("My WebM Video", tags.tags[0].simpleTags[0].value?.value)
  }

  @Test
  fun webmReturnsEmptyWithNoTags() {
    val segment = ebmlMaster(
      MatroskaIds.Segment,
      listOf(
        ebmlMaster(MatroskaIds.Info, emptyList()),
      ),
    )
    val webm = WebmRaw(elements = listOf(segment))
    assertTrue(webm.extractMetadata().isEmpty())
  }

  @Test
  fun webmReturnsEmptyWithNoSegment() {
    val header = ebmlMaster(MatroskaIds.EBML, emptyList())
    val webm = WebmRaw(elements = listOf(header))
    assertTrue(webm.extractMetadata().isEmpty())
  }

  // ===========================================================================
  //  MKV tests
  // ===========================================================================

  @Test
  fun mkvExtractsMatroskaTags() {
    val mkv = MkvRaw(elements = matroskaWithTag("ARTIST", "Test Director"))
    val metadata = mkv.extractMetadata()
    assertTrue(metadata.isNotEmpty())
    val tags = metadata.filterIsInstance<MatroskaTagMetadata>().firstOrNull()
    assertNotNull(tags)
    assertEquals("ARTIST", tags.tags[0].simpleTags[0].name.value)
    assertEquals("Test Director", tags.tags[0].simpleTags[0].value?.value)
  }

  @Test
  fun mkvReturnsEmptyWithNoTags() {
    val segment = ebmlMaster(MatroskaIds.Segment, emptyList())
    assertTrue(MkvRaw(elements = listOf(segment)).extractMetadata().isEmpty())
  }

  // ===========================================================================
  //  MP4 tests
  // ===========================================================================

  @Test
  fun mp4ExtractsItunesMetadata() {
    val mp4 = Mp4Raw(boxes = isoBmffWithItunesTag("aART", "My MP4"))
    val metadata = mp4.extractMetadata()
    assertTrue(metadata.isNotEmpty())
    val itunes = metadata.filterIsInstance<ItunesMetadata>().firstOrNull()
    assertNotNull(itunes)
    assertEquals(1, itunes.ilst.albumArtist.size)
    val item = itunes.ilst.albumArtist[0]
    assertEquals("aART", item.key.value)
    val data0 = item.data[0]
    assertEquals(1u, data0.dataType) // flags=1 = UTF-8
    val v = data0.value
    assertTrue(v is ItunesValue.Utf8Text)
    assertEquals("My MP4", (v as ItunesValue.Utf8Text).text.value)
  }

  @Test
  fun mp4ReturnsEmptyWithNoMoov() {
    val ftyp = IsoBmffBox(type = FourCC("ftyp"), data = Bytes("isom".encodeToByteArray()))
    assertTrue(Mp4Raw(boxes = listOf(ftyp)).extractMetadata().isEmpty())
  }

  // ===========================================================================
  //  MOV tests
  // ===========================================================================

  @Test
  fun movExtractsItunesMetadata() {
    val mov = MovRaw(boxes = isoBmffWithItunesTag("aART", "Test Artist"))
    val metadata = mov.extractMetadata()
    assertTrue(metadata.isNotEmpty())
    val itunes = metadata.filterIsInstance<ItunesMetadata>().firstOrNull()
    assertNotNull(itunes)
    val item = itunes.ilst.albumArtist[0]
    val data0 = item.data[0]
    val v = data0.value
    assertTrue(v is ItunesValue.Utf8Text)
    assertEquals("Test Artist", (v as ItunesValue.Utf8Text).text.value)
    assertEquals(1u, data0.dataType) // flags=1 = UTF-8
    assertEquals("Album Artist", item.name) // well-known name
  }

  @Test
  fun movReturnsEmptyWithNoIlst() {
    val moov = IsoBmffBox(type = FourCC("moov"), children = emptyList())
    assertTrue(MovRaw(boxes = listOf(moov)).extractMetadata().isEmpty())
  }

  // ===========================================================================
  //  AVI tests
  // ===========================================================================

  @Test
  fun aviExtractsRiffInfo() {
    val infoChildren = listOf(
      RiffChunk(
        id = RiffChunkId("INAM"),
        size = 6u,
        data = Bytes("Video\u0000".encodeToByteArray()),
      ),
      RiffChunk(
        id = RiffChunkId("IART"),
        size = 8u,
        data = Bytes("Director\u0000".encodeToByteArray()),
      ),
    )
    val listChunk = RiffChunk(
      id = RiffChunkId("LIST"),
      size = 100u,
      formType = RiffChunkId("INFO"),
      children = infoChildren,
    )
    val riff = RiffChunk(
      id = RiffChunkId("RIFF"),
      size = 200u,
      formType = RiffChunkId("AVI "),
      children = listOf(listChunk),
    )
    val avi = AviRaw(riff = riff)
    val metadata = avi.extractMetadata()
    assertTrue(metadata.isNotEmpty())
    val info = metadata.filterIsInstance<RiffInfoMetadata>().firstOrNull()
    assertNotNull(info)
    assertEquals(1, info.info.entries.title.size)
    assertEquals(1, info.info.entries.artist.size)
    assertEquals("INAM", info.info.entries.title[0].tag.value)
    assertEquals("Video", info.info.entries.title[0].value.value)
    assertEquals("IART", info.info.entries.artist[0].tag.value)
    assertEquals("Director", info.info.entries.artist[0].value.value)
  }

  @Test
  fun aviReturnsEmptyWhenNoInfoList() {
    val riff = RiffChunk(
      id = RiffChunkId("RIFF"),
      size = 0u,
      formType = RiffChunkId("AVI "),
      children = emptyList(),
    )
    assertTrue(AviRaw(riff = riff).extractMetadata().isEmpty())
  }

  // ===========================================================================
  //  M4A tests (ISO BMFF / iTunes)
  // ===========================================================================

  @Test
  fun m4aExtractsItunesMetadata() {
    val m4a = dev.transmute.model.structure.audio.types.M4aRaw(
      boxes = isoBmffWithItunesTag("aART", "My Album"),
    )
    val metadata = m4a.extractMetadata()
    assertTrue(metadata.isNotEmpty())
    val itunes = metadata.filterIsInstance<ItunesMetadata>().firstOrNull()
    assertNotNull(itunes)
    val item = itunes.ilst.albumArtist[0]
    val data0 = item.data[0]
    val v = data0.value
    assertTrue(v is ItunesValue.Utf8Text)
    assertEquals("My Album", (v as ItunesValue.Utf8Text).text.value)
    assertEquals(1u, data0.dataType) // flags=1 = UTF-8
  }
}
