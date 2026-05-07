package dev.transmute.structure.audio

import dev.transmute.model.core.Bytes
import dev.transmute.model.identify.RiffChunkId
import dev.transmute.model.metadata.id3.Id3v1Metadata
import dev.transmute.model.metadata.id3.Id3v2FrameContent
import dev.transmute.model.metadata.id3.Id3v2Metadata
import dev.transmute.model.metadata.riff.RiffInfoMetadata
import dev.transmute.model.metadata.vorbis.VorbisCommentMetadata
import dev.transmute.model.structure.audio.types.*
import dev.transmute.model.structure.common.OggPage
import dev.transmute.model.structure.common.OggSerialNumber
import dev.transmute.model.structure.common.RiffChunk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for audio format metadata extractors.
 *
 * Covers: MP3 (ID3v1 + ID3v2), AAC (ID3v2), WAV (RIFF INFO),
 * FLAC / Ogg Vorbis / Opus (Vorbis Comment).
 */
class AudioMetadataExtractorTest {

  // ===
  //  Shared fixture builders
  // ===

  /** Build a minimal ID3v2.3 tag with a single TIT2 frame. */
  private fun minimalId3v2Tag(title: String = "Test Song"): ByteArray {
    val payload = byteArrayOf(0x03) + title.encodeToByteArray()
    val frameId = "TIT2".encodeToByteArray()
    val frameSize = byteArrayOf(
      ((payload.size shr 24) and 0xFF).toByte(),
      ((payload.size shr 16) and 0xFF).toByte(),
      ((payload.size shr 8) and 0xFF).toByte(),
      (payload.size and 0xFF).toByte(),
    )
    val frame = frameId + frameSize + byteArrayOf(0x00, 0x00) + payload

    val tagSize = frame.size
    val syncsafe = byteArrayOf(
      ((tagSize shr 21) and 0x7F).toByte(),
      ((tagSize shr 14) and 0x7F).toByte(),
      ((tagSize shr 7) and 0x7F).toByte(),
      (tagSize and 0x7F).toByte(),
    )
    return byteArrayOf(0x49, 0x44, 0x33, 0x03, 0x00, 0x00) + syncsafe + frame
  }

  /** Build a minimal ID3v1 tag (128 bytes). */
  private fun minimalId3v1Tag(
    title: String = "Title",
    artist: String = "Artist",
    album: String = "Album",
    year: String = "2024",
    comment: String = "Comment",
    track: Int = 1,
    genre: Int = 0, // Blues
  ): ByteArray {
    val tag = ByteArray(128)
    tag[0] = 'T'.code.toByte()
    tag[1] = 'A'.code.toByte()
    tag[2] = 'G'.code.toByte()
    title.encodeToByteArray().copyInto(tag, 3, 0, minOf(title.length, 30))
    artist.encodeToByteArray().copyInto(tag, 33, 0, minOf(artist.length, 30))
    album.encodeToByteArray().copyInto(tag, 63, 0, minOf(album.length, 30))
    year.encodeToByteArray().copyInto(tag, 93, 0, minOf(year.length, 4))
    comment.encodeToByteArray().copyInto(tag, 97, 0, minOf(comment.length, 28))
    tag[125] = 0 // must be zero for track number
    tag[126] = track.toByte()
    tag[127] = genre.toByte()
    return tag
  }

  /** Build a Vorbis Comment block payload: vendor + comments. */
  private fun vorbisCommentPayload(
    vendor: String = "TestEncoder",
    comments: List<String> = listOf("TITLE=Test Track", "ARTIST=Test Artist"),
  ): ByteArray {
    var buf = ByteArray(0)
    fun appendU32LE(v: Int) {
      buf += byteArrayOf(
        (v and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 24) and 0xFF).toByte(),
      )
    }
    val vendorBytes = vendor.encodeToByteArray()
    appendU32LE(vendorBytes.size)
    buf += vendorBytes
    appendU32LE(comments.size)
    for (c in comments) {
      val bytes = c.encodeToByteArray()
      appendU32LE(bytes.size)
      buf += bytes
    }
    return buf
  }

  /** Build a minimal OggPage. */
  private fun oggPage(data: ByteArray): OggPage = OggPage(
    headerType = 0u,
    granulePosition = 0L,
    serialNumber = OggSerialNumber(1),
    pageSequence = 0u,
    crc = 0u,
    segmentTable = Bytes(byteArrayOf(data.size.toByte())),
    data = Bytes(data),
  )

  // ===
  //  MP3 tests
  // ===

  @Test
  fun mp3ExtractsId3v2Metadata() {
    val mp3 = Mp3Raw(
      id3v2Tag = Bytes(minimalId3v2Tag("Hello World")),
      audioData = Bytes(ByteArray(0)),
      id3v1TagData = null,
    )
    val metadata = mp3.extractMetadata()
    assertTrue(metadata.isNotEmpty())
    val id3v2 = metadata.filterIsInstance<Id3v2Metadata>().firstOrNull()
    assertNotNull(id3v2)
    assertEquals(3.toUByte(), id3v2.header.version.major)
    assertEquals(1, id3v2.content.title.size)
    val content = id3v2.content.title[0].content
    assertIs<Id3v2FrameContent.Text>(content)
    assertEquals("Hello World", content.text)
  }

  @Test
  fun mp3ExtractsId3v1Metadata() {
    val mp3 = Mp3Raw(
      id3v2Tag = null,
      audioData = Bytes(ByteArray(0)),
      id3v1TagData = Bytes(minimalId3v1Tag(title = "My Song", artist = "The Band", year = "2023", track = 3)),
    )
    val metadata = mp3.extractMetadata()
    assertTrue(metadata.isNotEmpty())
    val id3v1 = metadata.filterIsInstance<Id3v1Metadata>().firstOrNull()
    assertNotNull(id3v1)
    assertEquals("My Song", id3v1.title)
    assertEquals("The Band", id3v1.artist)
    assertEquals("2023", id3v1.year)
    assertEquals(3.toUByte(), id3v1.track)
    assertEquals("Blues", id3v1.genreName) // genre=0
  }

  @Test
  fun mp3ExtractsBothTags() {
    val mp3 = Mp3Raw(
      id3v2Tag = Bytes(minimalId3v2Tag("Song Via v2")),
      audioData = Bytes(ByteArray(0)),
      id3v1TagData = Bytes(minimalId3v1Tag(title = "Song Via v1")),
    )
    val metadata = mp3.extractMetadata()
    assertEquals(2, metadata.size)
    assertIs<Id3v2Metadata>(metadata[0])
    assertIs<Id3v1Metadata>(metadata[1])
  }

  @Test
  fun mp3EmptyTagsReturnsEmpty() {
    val mp3 = Mp3Raw(
      id3v2Tag = null,
      audioData = Bytes(ByteArray(0)),
      id3v1TagData = null,
    )
    assertTrue(mp3.extractMetadata().isEmpty())
  }

  // ===
  //  AAC tests
  // ===

  @Test
  fun aacExtractsId3v2FromPrependedTag() {
    val id3Data = minimalId3v2Tag("AAC Title")
    val aacFrames = ByteArray(20) // dummy ADTS
    val raw = AacRaw(data = Bytes(id3Data + aacFrames))
    val metadata = raw.extractMetadata()
    assertTrue(metadata.isNotEmpty())
    val id3v2 = metadata.filterIsInstance<Id3v2Metadata>().firstOrNull()
    assertNotNull(id3v2)
    val content = id3v2.content.title[0].content
    assertIs<Id3v2FrameContent.Text>(content)
    assertEquals("AAC Title", content.text)
  }

  @Test
  fun aacReturnsEmptyIfNoId3() {
    val raw = AacRaw(data = Bytes(ByteArray(20) { 0xFF.toByte() }))
    assertTrue(raw.extractMetadata().isEmpty())
  }

  @Test
  fun aacReturnsEmptyForTooShortData() {
    val raw = AacRaw(data = Bytes(ByteArray(5)))
    assertTrue(raw.extractMetadata().isEmpty())
  }

  // ===
  //  WAV tests
  // ===

  @Test
  fun wavExtractsRiffInfo() {
    val infoChildren = listOf(
      RiffChunk(
        id = RiffChunkId("INAM"),
        size = 10u,
        data = Bytes("Test Song\u0000".encodeToByteArray()),
      ),
      RiffChunk(
        id = RiffChunkId("IART"),
        size = 11u,
        data = Bytes("Test Artist\u0000".encodeToByteArray()),
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
      formType = RiffChunkId("WAVE"),
      children = listOf(listChunk),
    )
    val wav = WavRaw(riff = riff)
    val metadata = wav.extractMetadata()
    assertTrue(metadata.isNotEmpty())
    val info = metadata.filterIsInstance<RiffInfoMetadata>().firstOrNull()
    assertNotNull(info)
    assertEquals(2, info.info.entries.order.size)
    assertEquals(1, info.info.entries.title.size)
    assertEquals(1, info.info.entries.artist.size)
    assertEquals("INAM", info.info.entries.title[0].tag.value)
    assertEquals("Test Song", info.info.entries.title[0].value.value)
    assertEquals("Title", info.info.entries.title[0].name)
    assertEquals("IART", info.info.entries.artist[0].tag.value)
    assertEquals("Test Artist", info.info.entries.artist[0].value.value)
    assertEquals("Artist", info.info.entries.artist[0].name)
  }

  @Test
  fun wavReturnsEmptyWhenNoInfoList() {
    val riff = RiffChunk(
      id = RiffChunkId("RIFF"),
      size = 0u,
      formType = RiffChunkId("WAVE"),
      children = emptyList(),
    )
    assertTrue(WavRaw(riff = riff).extractMetadata().isEmpty())
  }

  // ===
  //  FLAC tests
  // ===

  @Test
  fun flacExtractsVorbisComment() {
    val payload = vorbisCommentPayload(
      vendor = "Lavf58.76",
      comments = listOf("TITLE=My FLAC Song", "ALBUM=Test Album"),
    )
    val block = FlacMetadataBlock(
      type = FlacMetadataBlockType.VorbisComment,
      isLast = false,
      data = Bytes(payload),
    )
    val flac = FlacRaw(
      metadataBlocks = listOf(block),
      audioData = Bytes(ByteArray(0)),
    )
    val metadata = flac.extractMetadata()
    assertTrue(metadata.isNotEmpty())
    val vc = metadata.filterIsInstance<VorbisCommentMetadata>().firstOrNull()
    assertNotNull(vc)
    assertEquals("Lavf58.76", vc.vendor.value)
    assertEquals(1, vc.fields.title.size)
    assertEquals(1, vc.fields.album.size)
    assertEquals("TITLE", vc.fields.title[0].field.value)
    assertEquals("My FLAC Song", vc.fields.title[0].value.value)
    assertEquals("ALBUM", vc.fields.album[0].field.value)
    assertEquals("Test Album", vc.fields.album[0].value.value)
  }

  @Test
  fun flacReturnsEmptyWhenNoVorbisCommentBlock() {
    val block = FlacMetadataBlock(
      type = FlacMetadataBlockType.StreamInfo,
      isLast = true,
      data = Bytes(ByteArray(34)),
    )
    val flac = FlacRaw(
      metadataBlocks = listOf(block),
      audioData = Bytes(ByteArray(0)),
    )
    assertTrue(flac.extractMetadata().isEmpty())
  }

  // ===
  //  Ogg Vorbis tests
  // ===

  @Test
  fun oggVorbisExtractsVorbisComment() {
    // Vorbis comment header: 0x03 + "vorbis" + comment payload
    val magic = byteArrayOf(0x03, 0x76, 0x6F, 0x72, 0x62, 0x69, 0x73)
    val payload = vorbisCommentPayload(comments = listOf("TITLE=Ogg Song"))
    val page = oggPage(magic + payload)

    val ogg = OggAudioRaw(pages = listOf(page))
    val metadata = ogg.extractMetadata()
    assertTrue(metadata.isNotEmpty())
    val vc = metadata.filterIsInstance<VorbisCommentMetadata>().firstOrNull()
    assertNotNull(vc)
    assertEquals(1, vc.fields.title.size)
    assertEquals("TITLE", vc.fields.title[0].field.value)
    assertEquals("Ogg Song", vc.fields.title[0].value.value)
  }

  @Test
  fun oggVorbisReturnsEmptyWithNoCommentPage() {
    // Page without the vorbis comment magic
    val page = oggPage(ByteArray(20))
    assertTrue(OggAudioRaw(pages = listOf(page)).extractMetadata().isEmpty())
  }

  // ===
  //  Opus tests
  // ===

  @Test
  fun opusExtractsVorbisComment() {
    val magic = "OpusTags".encodeToByteArray()
    val payload = vorbisCommentPayload(comments = listOf("ARTIST=Opus Artist"))
    val page = oggPage(magic + payload)

    val opus = OpusRaw(pages = listOf(page))
    val metadata = opus.extractMetadata()
    assertTrue(metadata.isNotEmpty())
    val vc = metadata.filterIsInstance<VorbisCommentMetadata>().firstOrNull()
    assertNotNull(vc)
    assertEquals(1, vc.fields.artist.size)
    assertEquals("ARTIST", vc.fields.artist[0].field.value)
    assertEquals("Opus Artist", vc.fields.artist[0].value.value)
  }

  @Test
  fun opusReturnsEmptyWithNoTagsPage() {
    val page = oggPage(ByteArray(20))
    assertTrue(OpusRaw(pages = listOf(page)).extractMetadata().isEmpty())
  }
}
