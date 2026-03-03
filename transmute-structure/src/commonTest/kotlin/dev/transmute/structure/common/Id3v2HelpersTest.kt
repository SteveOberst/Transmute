package dev.transmute.structure.common

import dev.transmute.model.metadata.id3.Id3v2FrameContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the shared [parseId3v2FromBytes] parser and [id3v2TotalSize].
 */
class Id3v2HelpersTest {

    // -- Minimal ID3v2 fixture with one TIT2 frame ----------------------------

    /**
     * Build a minimal ID3v2.3 tag with a single text frame.
     *
     * Layout: "ID3" + version(2) + flags(1) + size(4 syncsafe) + frame(s)
     */
    private fun makeId3v2Tag(
        major: Int = 3,
        revision: Int = 0,
        frames: ByteArray = ByteArray(0),
    ): ByteArray {
        val size = frames.size
        val syncsafe = byteArrayOf(
            ((size shr 21) and 0x7F).toByte(),
            ((size shr 14) and 0x7F).toByte(),
            ((size shr  7) and 0x7F).toByte(),
            ( size          and 0x7F).toByte(),
        )
        return byteArrayOf(
            0x49, 0x44, 0x33, // "ID3"
            major.toByte(), revision.toByte(),
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
            ((size shr  8) and 0xFF).toByte(),
            ( size          and 0xFF).toByte(),
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
            ((size shr  7) and 0x7F).toByte(),
            ( size          and 0x7F).toByte(),
        )
        return id.encodeToByteArray() + syncsafe + byteArrayOf(0x00, 0x00) + payload
    }

    // -- Tests ----------------------------------------------------------------

    @Test
    fun parsesEmptyTag() {
        val tag = makeId3v2Tag()
        val result = parseId3v2FromBytes(tag)
        assertNotNull(result)
        assertEquals(3, result.version.major)
        assertEquals(0, result.version.revision)
        assertTrue(result.frames.isEmpty())
    }

    @Test
    fun parsesSingleTextFrame() {
        val frames = textFrame("TIT2", "Hello World")
        val tag = makeId3v2Tag(frames = frames)
        val result = parseId3v2FromBytes(tag)
        assertNotNull(result)
        assertEquals(1, result.frames.size)
        assertEquals("TIT2", result.frames[0].id)
        val content = result.frames[0].content
        assertTrue(content is Id3v2FrameContent.Text)
        assertEquals("Hello World", content.text)
        assertEquals("UTF-8", content.encoding)
    }

    @Test
    fun parsesMultipleTextFrames() {
        val frames = textFrame("TIT2", "Song Title") +
            textFrame("TPE1", "Artist Name") +
            textFrame("TALB", "Album Name")
        val tag = makeId3v2Tag(frames = frames)
        val result = parseId3v2FromBytes(tag)
        assertNotNull(result)
        assertEquals(3, result.frames.size)
        assertEquals("TIT2", result.frames[0].id)
        assertEquals("TPE1", result.frames[1].id)
        assertEquals("TALB", result.frames[2].id)
    }

    @Test
    fun parsesV24WithSyncsafeSizes() {
        val frames = textFrameV24("TIT2", "V4 Title")
        val tag = makeId3v2Tag(major = 4, frames = frames)
        val result = parseId3v2FromBytes(tag)
        assertNotNull(result)
        assertEquals(4, result.version.major)
        assertEquals(1, result.frames.size)
        val content = result.frames[0].content as Id3v2FrameContent.Text
        assertEquals("V4 Title", content.text)
    }

    @Test
    fun parsesV22With3CharFrameIds() {
        // ID3v2.2: 3-char frame ID + 3-byte size
        val payload = byteArrayOf(0x03) + "Short".encodeToByteArray()
        val size = payload.size
        val frame = "TT2".encodeToByteArray() + byteArrayOf(
            ((size shr 16) and 0xFF).toByte(),
            ((size shr  8) and 0xFF).toByte(),
            ( size          and 0xFF).toByte(),
        ) + payload
        val tag = makeId3v2Tag(major = 2, frames = frame)
        val result = parseId3v2FromBytes(tag)
        assertNotNull(result)
        assertEquals(2, result.version.major)
        assertEquals(1, result.frames.size)
        assertEquals("TT2", result.frames[0].id)
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
            ((size shr  8) and 0xFF).toByte(),
            ( size          and 0xFF).toByte(),
        )
        val frame = "WOAR".encodeToByteArray() + sizeBytes + byteArrayOf(0x00, 0x00) + payload
        val tag = makeId3v2Tag(frames = frame)
        val result = parseId3v2FromBytes(tag)
        assertNotNull(result)
        assertEquals(1, result.frames.size)
        val content = result.frames[0].content
        assertTrue(content is Id3v2FrameContent.Url)
        assertEquals(url, content.url)
    }

    @Test
    fun parsesHeaderFlags() {
        val frames = ByteArray(0)
        val syncsafe = byteArrayOf(0x00, 0x00, 0x00, 0x00)
        val tag = byteArrayOf(
            0x49, 0x44, 0x33, // "ID3"
            0x03, 0x00,       // version 2.3
            0xE0.toByte(),    // flags: unsync + extended + experimental
        ) + syncsafe
        val result = parseId3v2FromBytes(tag)
        assertNotNull(result)
        assertTrue(result.flags.unsynchronisation)
        assertTrue(result.flags.extendedHeader)
        assertTrue(result.flags.experimental)
    }

    @Test
    fun tagSizeBytesIsAccurate() {
        val frames = textFrame("TIT2", "hello") + textFrame("TPE1", "world")
        val tag = makeId3v2Tag(frames = frames)
        val result = parseId3v2FromBytes(tag)
        assertNotNull(result)
        assertEquals(frames.size.toLong(), result.tagSizeBytes)
    }
}
