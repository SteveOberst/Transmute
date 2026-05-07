package dev.transmute.structure.common

import dev.transmute.model.metadata.id3.Id3v2FrameContent
import dev.transmute.model.metadata.id3.Id3v2FrameRef
import dev.transmute.model.metadata.id3.Id3v2KnownFrameId
import dev.transmute.model.metadata.id3.Id3TextEncoding
import dev.transmute.model.metadata.id3.frames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the shared [parseId3v2FromBytes] parser and [id3v2TotalSize].
 */
class Id3v2HelpersTest {

  // -- Minimal ID3v2 fixture with one TIT2 frame ---

  /**
   * Build a minimal ID3v2.3 tag with a single text frame.
   *
   * Layout: "ID3" + version(2) + flags(1) + size(4 syncsafe) + frame(s)
   */
  private fun makeId3v2Tag(major: Int = 3, revision: Int = 0, frames: ByteArray = ByteArray(0)): ByteArray {
    val size = frames.size
    val syncsafe = byteArrayOf(
      ((size shr 21) and 0x7F).toByte(),
      ((size shr 14) and 0x7F).toByte(),
      ((size shr 7) and 0x7F).toByte(),
      (size and 0x7F).toByte(),
    )
    return byteArrayOf(
      0x49,
      0x44,
      0x33, // "ID3"
      major.toByte(),
      revision.toByte(),
      0x00, // flags
    ) + syncsafe + frames
  }

  /** Build a single ID3v2.3 text frame (4-byte ID, 4-byte size, 2-byte flags, payload). */
  private fun textFrame(id: String, text: String): ByteArray {
    val payload = byteArrayOf(0x03) + text.encodeToByteArray() // UTF-8 encoding byte + text
    val size = payload.size
    val sizeBytes = byteArrayOf(
      ((size shr 24) and 0xFF).toByte(),
      ((size shr 16) and 0xFF).toByte(),
      ((size shr 8) and 0xFF).toByte(),
      (size and 0xFF).toByte(),
    )
    return id.encodeToByteArray() + sizeBytes + byteArrayOf(0x00, 0x00) + payload
  }

  /** Build a v2.3 text frame with syncsafe size (for v2.4 format) */
  private fun textFrameV24(id: String, text: String): ByteArray {
    val payload = byteArrayOf(0x03) + text.encodeToByteArray()
    val size = payload.size
    val syncsafe = byteArrayOf(
      ((size shr 21) and 0x7F).toByte(),
      ((size shr 14) and 0x7F).toByte(),
      ((size shr 7) and 0x7F).toByte(),
      (size and 0x7F).toByte(),
    )
    return id.encodeToByteArray() + syncsafe + byteArrayOf(0x00, 0x00) + payload
  }

  // -- Tests ---

  @Test
  fun parsesEmptyTag() {
    val tag = makeId3v2Tag()
    val result = parseId3v2FromBytes(tag)
    assertNotNull(result)
    assertEquals(3.toUByte(), result.header.version.major)
    assertEquals(0.toUByte(), result.header.version.revision)
    assertTrue(result.content.order.isEmpty())
    assertTrue(result.content.extra.isEmpty())
    @Suppress("DEPRECATION") assertTrue(result.frames.isEmpty())
  }

  @Test
  fun parsesSingleTextFrame() {
    val frames = textFrame("TIT2", "Hello World")
    val tag = makeId3v2Tag(frames = frames)
    val result = parseId3v2FromBytes(tag)
    assertNotNull(result)
    // Typed slot: title
    assertEquals(1, result.content.title.size)
    assertEquals("TIT2", result.content.title[0].id.value)
    val content = result.content.title[0].content
    assertTrue(content is Id3v2FrameContent.Text)
    assertEquals("Hello World", content.text)
    assertEquals(Id3TextEncoding.Utf8, content.encoding)
    // Order preserved
    assertEquals(1, result.content.order.size)
    assertEquals(Id3v2FrameRef.Known(Id3v2KnownFrameId.Title, 0u), result.content.order[0])
    // Extra is empty
    assertTrue(result.content.extra.isEmpty())
    // Deprecated accessor still works
    @Suppress("DEPRECATION") assertEquals(1, result.frames.size)
  }

  @Test
  fun parsesMultipleTextFrames() {
    val frames = textFrame("TIT2", "Song Title") +
      textFrame("TPE1", "Artist Name") +
      textFrame("TALB", "Album Name")
    val tag = makeId3v2Tag(frames = frames)
    val result = parseId3v2FromBytes(tag)
    assertNotNull(result)
    // Each frame goes to its typed slot
    assertEquals(1, result.content.title.size)
    assertEquals(1, result.content.artist.size)
    assertEquals(1, result.content.album.size)
    assertTrue(result.content.extra.isEmpty())
    // Order matches on-disk sequence
    assertEquals(3, result.content.order.size)
    assertEquals(Id3v2FrameRef.Known(Id3v2KnownFrameId.Title, 0u), result.content.order[0])
    assertEquals(Id3v2FrameRef.Known(Id3v2KnownFrameId.Artist, 0u), result.content.order[1])
    assertEquals(Id3v2FrameRef.Known(Id3v2KnownFrameId.Album, 0u), result.content.order[2])
    // Deprecated accessor preserves order
    @Suppress("DEPRECATION") run {
      assertEquals(3, result.frames.size)
      assertEquals("TIT2", result.frames[0].id.value)
      assertEquals("TPE1", result.frames[1].id.value)
      assertEquals("TALB", result.frames[2].id.value)
    }
  }

  @Test
  fun parsesV24WithSyncsafeSizes() {
    val frames = textFrameV24("TIT2", "V4 Title")
    val tag = makeId3v2Tag(major = 4, frames = frames)
    val result = parseId3v2FromBytes(tag)
    assertNotNull(result)
    assertEquals(4.toUByte(), result.header.version.major)
    assertEquals(1, result.content.title.size)
    val content = result.content.title[0].content as Id3v2FrameContent.Text
    assertEquals("V4 Title", content.text)
  }

  @Test
  fun parsesV22With3CharFrameIds() {
    // ID3v2.2: 3-char frame ID + 3-byte size
    val payload = byteArrayOf(0x03) + "Short".encodeToByteArray()
    val size = payload.size
    val frame = "TT2".encodeToByteArray() + byteArrayOf(
      ((size shr 16) and 0xFF).toByte(),
      ((size shr 8) and 0xFF).toByte(),
      (size and 0xFF).toByte(),
    ) + payload
    val tag = makeId3v2Tag(major = 2, frames = frame)
    val result = parseId3v2FromBytes(tag)
    assertNotNull(result)
    assertEquals(2.toUByte(), result.header.version.major)
    // v2.2 TT2 maps to title typed slot
    assertEquals(1, result.content.title.size)
    assertEquals("TT2", result.content.title[0].id.value)
  }

  @Test
  fun rejectsNonId3Data() {
    val garbage = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09)
    assertNull(parseId3v2FromBytes(garbage))
  }

  @Test
  fun rejectsTooShortData() {
    assertNull(parseId3v2FromBytes(byteArrayOf(0x49, 0x44, 0x33)))
  }

  @Test
  fun id3v2TotalSizeCalculatesCorrectly() {
    val frames = textFrame("TIT2", "Test")
    val tag = makeId3v2Tag(frames = frames)
    val totalSize = id3v2TotalSize(tag)
    assertNotNull(totalSize)
    assertEquals(tag.size, totalSize)
  }

  @Test
  fun id3v2TotalSizeReturnsNullForNonId3() {
    assertNull(id3v2TotalSize(ByteArray(10)))
  }

  @Test
  fun parsesUrlFrame() {
    val url = "https://example.com"
    val payload = url.encodeToByteArray()
    val size = payload.size
    val sizeBytes = byteArrayOf(
      ((size shr 24) and 0xFF).toByte(),
      ((size shr 16) and 0xFF).toByte(),
      ((size shr 8) and 0xFF).toByte(),
      (size and 0xFF).toByte(),
    )
    val frame = "WOAR".encodeToByteArray() + sizeBytes + byteArrayOf(0x00, 0x00) + payload
    val tag = makeId3v2Tag(frames = frame)
    val result = parseId3v2FromBytes(tag)
    assertNotNull(result)
    // URL frames are not well-known typed slots, so they go to extra
    assertEquals(1, result.content.extra.size)
    val content = result.content.extra[0].content
    assertTrue(content is Id3v2FrameContent.Url)
    assertEquals(url, content.url.value)
  }

  @Test
  fun parsesHeaderFlags() {
    val frames = ByteArray(0)
    val syncsafe = byteArrayOf(0x00, 0x00, 0x00, 0x00)
    val tag = byteArrayOf(
      0x49,
      0x44,
      0x33, // "ID3"
      0x03,
      0x00, // version 2.3
      0xE0.toByte(), // flags: unsync + extended + experimental
    ) + syncsafe
    val result = parseId3v2FromBytes(tag)
    assertNotNull(result)
    assertTrue(result.header.flags.unsynchronisation)
    assertTrue(result.header.flags.extendedHeader)
    assertTrue(result.header.flags.experimental)
  }

  @Test
  fun tagSizeBytesIsAccurate() {
    val frames = textFrame("TIT2", "hello") + textFrame("TPE1", "world")
    val tag = makeId3v2Tag(frames = frames)
    val result = parseId3v2FromBytes(tag)
    assertNotNull(result)
    assertEquals(frames.size.toUInt(), result.header.tagSize)
  }

  // -- Typed-slot partitioning tests ---

  @Test
  fun multipleSameSlotFrames() {
    // Two COMM frames -> both go to content.comment; order tracks them
    val frames = textFrame("COMM", "Comment 1") + textFrame("COMM", "Comment 2")
    val tag = makeId3v2Tag(frames = frames)
    val result = parseId3v2FromBytes(tag)
    assertNotNull(result)
    assertEquals(2, result.content.comment.size)
    assertTrue(result.content.extra.isEmpty())
    // Order references have increasing indices
    assertEquals(Id3v2FrameRef.Known(Id3v2KnownFrameId.Comment, 0u), result.content.order[0])
    assertEquals(Id3v2FrameRef.Known(Id3v2KnownFrameId.Comment, 1u), result.content.order[1])
  }

  @Test
  fun mixedKnownAndExtraFrames() {
    // Known-Unknown-Known sequence preserves interleaved order
    val frames = textFrame("TIT2", "Title") +
      textFrame("PRIV", "Private Data") +
      textFrame("TPE1", "Artist")
    val tag = makeId3v2Tag(frames = frames)
    val result = parseId3v2FromBytes(tag)
    assertNotNull(result)
    assertEquals(1, result.content.title.size)
    assertEquals(1, result.content.artist.size)
    assertEquals(1, result.content.extra.size)
    assertEquals("PRIV", result.content.extra[0].id.value)
    // Order: Title -> Extra(0) -> Artist
    assertEquals(3, result.content.order.size)
    assertEquals(Id3v2FrameRef.Known(Id3v2KnownFrameId.Title, 0u), result.content.order[0])
    assertEquals(Id3v2FrameRef.Extra(0u), result.content.order[1])
    assertEquals(Id3v2FrameRef.Known(Id3v2KnownFrameId.Artist, 0u), result.content.order[2])
    // Deprecated accessor preserves interleaved order
    @Suppress("DEPRECATION") run {
      assertEquals(3, result.frames.size)
      assertEquals("TIT2", result.frames[0].id.value)
      assertEquals("PRIV", result.frames[1].id.value)
      assertEquals("TPE1", result.frames[2].id.value)
    }
  }

  @Test
  fun allTypedSlotIds() {
    // Every well-known frame ID should go to its typed slot
    val frames = textFrame("TIT2", "t") +
      textFrame("TPE1", "a") +
      textFrame("TALB", "al") +
      textFrame("TYER", "y") +
      textFrame("TCON", "g") +
      textFrame("COMM", "c") +
      textFrame("TRCK", "1") +
      textFrame("TPOS", "1") +
      textFrame("TCOM", "co") +
      textFrame("TPE2", "aa") +
      textFrame("APIC", "p") +
      textFrame("USLT", "l")
    val tag = makeId3v2Tag(frames = frames)
    val result = parseId3v2FromBytes(tag)
    assertNotNull(result)
    assertEquals(1, result.content.title.size)
    assertEquals(1, result.content.artist.size)
    assertEquals(1, result.content.album.size)
    assertEquals(1, result.content.year.size)
    assertEquals(1, result.content.genre.size)
    assertEquals(1, result.content.comment.size)
    assertEquals(1, result.content.trackNumber.size)
    assertEquals(1, result.content.discNumber.size)
    assertEquals(1, result.content.composer.size)
    assertEquals(1, result.content.albumArtist.size)
    assertEquals(1, result.content.picture.size)
    assertEquals(1, result.content.lyrics.size)
    assertTrue(result.content.extra.isEmpty())
    assertEquals(12, result.content.order.size)
  }

  @Test
  fun v22AllTypedSlotIds() {
    // v2.2 3-char IDs should map to the same typed slots
    fun v22Frame(id: String, text: String): ByteArray {
      val payload = byteArrayOf(0x03) + text.encodeToByteArray()
      val size = payload.size
      return id.encodeToByteArray() + byteArrayOf(
        ((size shr 16) and 0xFF).toByte(),
        ((size shr 8) and 0xFF).toByte(),
        (size and 0xFF).toByte(),
      ) + payload
    }
    val frames = v22Frame("TT2", "t") +
      v22Frame("TP1", "a") +
      v22Frame("TAL", "al") +
      v22Frame("TYE", "y") +
      v22Frame("TCO", "g") +
      v22Frame("COM", "c") +
      v22Frame("TRK", "1") +
      v22Frame("TPA", "1") +
      v22Frame("TCM", "co") +
      v22Frame("TP2", "aa") +
      v22Frame("PIC", "p") +
      v22Frame("ULT", "l")
    val tag = makeId3v2Tag(major = 2, frames = frames)
    val result = parseId3v2FromBytes(tag)
    assertNotNull(result)
    assertEquals(1, result.content.title.size)
    assertEquals(1, result.content.artist.size)
    assertEquals(1, result.content.album.size)
    assertEquals(1, result.content.year.size)
    assertEquals(1, result.content.genre.size)
    assertEquals(1, result.content.comment.size)
    assertEquals(1, result.content.trackNumber.size)
    assertEquals(1, result.content.discNumber.size)
    assertEquals(1, result.content.composer.size)
    assertEquals(1, result.content.albumArtist.size)
    assertEquals(1, result.content.picture.size)
    assertEquals(1, result.content.lyrics.size)
    assertTrue(result.content.extra.isEmpty())
    assertEquals(12, result.content.order.size)
  }

  @Test
  fun onlyUnknownFrames() {
    // All frames are unknown -> everything goes to extra
    val frames = textFrame("PRIV", "x") + textFrame("WXXX", "y")
    val tag = makeId3v2Tag(frames = frames)
    val result = parseId3v2FromBytes(tag)
    assertNotNull(result)
    assertEquals(2, result.content.extra.size)
    assertTrue(result.content.title.isEmpty())
    assertTrue(result.content.artist.isEmpty())
    assertEquals(Id3v2FrameRef.Extra(0u), result.content.order[0])
    assertEquals(Id3v2FrameRef.Extra(1u), result.content.order[1])
  }
}
