package dev.transmute.video.codecs.jvm

import dev.transmute.core.Bytes
import dev.transmute.core.asBytes
import dev.transmute.video.VideoFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Direct sniff() tests for all JVM video codecs. */
class JvmVideoCodecSniffTest {

  // ---- JvmMp4Codec ----

  private val mp4 = JvmMp4Codec()

  @Test
  fun mp4SniffsIsom() {
    val data = buildFtyp("isom")
    assertEquals(VideoFormat.Mp4, mp4.sniff(data))
  }

  @Test
  fun mp4SniffsMp41() {
    val data = buildFtyp("mp41")
    assertEquals(VideoFormat.Mp4, mp4.sniff(data))
  }

  @Test
  fun mp4SniffsMp42() {
    val data = buildFtyp("mp42")
    assertEquals(VideoFormat.Mp4, mp4.sniff(data))
  }

  @Test
  fun mp4SniffsAvc1() {
    val data = buildFtyp("avc1")
    assertEquals(VideoFormat.Mp4, mp4.sniff(data))
  }

  @Test
  fun mp4SniffsIso2() {
    val data = buildFtyp("iso2")
    assertEquals(VideoFormat.Mp4, mp4.sniff(data))
  }

  @Test
  fun mp4Sniffs3gp() {
    val data = buildFtyp("3gp4")
    assertEquals(VideoFormat.Mp4, mp4.sniff(data))
  }

  @Test
  fun mp4RejectsMov() {
    val data = buildFtyp("qt  ")
    assertNull(mp4.sniff(data))
  }

  @Test
  fun mp4RejectsTooShort() {
    assertNull(mp4.sniff(ByteArray(8).asBytes()))
    assertNull(mp4.sniff(ByteArray(0).asBytes()))
  }

  // ---- JvmMovCodec ----

  private val mov = JvmMovCodec()

  @Test
  fun movSniffsQt() {
    val data = buildFtyp("qt  ")
    assertEquals(VideoFormat.Mov, mov.sniff(data))
  }

  @Test
  fun movRejectsMp4() {
    val data = buildFtyp("isom")
    assertNull(mov.sniff(data))
  }

  @Test
  fun movRejectsTooShort() {
    assertNull(mov.sniff(ByteArray(8).asBytes()))
    assertNull(mov.sniff(ByteArray(0).asBytes()))
  }

  // ---- JvmWebmCodec ----

  private val webm = JvmWebmCodec()

  @Test
  fun webmSniffsEbmlShort() {
    // Short EBML header without doctype - defaults to WebM
    val data = byteArrayOf(
      0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte(),
      0x00, 0x00, 0x00, 0x00,
    )
    assertEquals(VideoFormat.Webm, webm.sniff(data.asBytes()))
  }

  @Test
  fun webmSniffsWebmDoctype() {
    // Full EBML header with "webm" doctype
    val data = buildEbml("webm")
    assertEquals(VideoFormat.Webm, webm.sniff(data))
  }

  @Test
  fun webmRejectsMatroska() {
    // EBML with "matroska" doctype should NOT match WebM
    val data = buildEbml("matroska")
    assertNull(webm.sniff(data))
  }

  @Test
  fun webmRejectsNonEbml() {
    val data = byteArrayOf(0x00, 0x01, 0x02, 0x03)
    assertNull(webm.sniff(data.asBytes()))
  }

  @Test
  fun webmRejectsTooShort() {
    assertNull(webm.sniff(ByteArray(3).asBytes()))
    assertNull(webm.sniff(ByteArray(0).asBytes()))
  }

  // ---- JvmAviCodec ----

  private val avi = JvmAviCodec()

  @Test
  fun aviSniffsRiffAvi() {
    val data = byteArrayOf(
      'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
      0x00, 0x00, 0x00, 0x00,
      'A'.code.toByte(), 'V'.code.toByte(), 'I'.code.toByte(), ' '.code.toByte(),
    )
    assertEquals(VideoFormat.Avi, avi.sniff(data.asBytes()))
  }

  @Test
  fun aviRejectsRiffWave() {
    val data = byteArrayOf(
      'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
      0x00, 0x00, 0x00, 0x00,
      'W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte(),
    )
    assertNull(avi.sniff(data.asBytes()))
  }

  @Test
  fun aviRejectsTooShort() {
    assertNull(avi.sniff(ByteArray(8).asBytes()))
    assertNull(avi.sniff(ByteArray(0).asBytes()))
  }

  // ---- JvmMkvCodec ----

  private val mkv = JvmMkvCodec()

  @Test
  fun mkvSniffsMatroskaDoctype() {
    val data = buildEbml("matroska")
    assertEquals(VideoFormat.Mkv, mkv.sniff(data))
  }

  @Test
  fun mkvRejectsWebmDoctype() {
    val data = buildEbml("webm")
    assertNull(mkv.sniff(data))
  }

  @Test
  fun mkvRejectsShortEbml() {
    // Short EBML without doctype - MKV requires explicit "matroska"
    val data = byteArrayOf(
      0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte(),
      0x00, 0x00, 0x00, 0x00,
    )
    assertNull(mkv.sniff(data.asBytes()))
  }

  @Test
  fun mkvRejectsTooShort() {
    assertNull(mkv.sniff(ByteArray(3).asBytes()))
    assertNull(mkv.sniff(ByteArray(0).asBytes()))
  }

  // ---- Helpers ----

  /** Builds a minimal ISO BMFF ftyp box with the given 4-char major brand. */
  private fun buildFtyp(brand: String): Bytes {
    require(brand.length == 4)
    val data = ByteArray(16)
    data[0] = 0x00; data[1] = 0x00; data[2] = 0x00; data[3] = 0x10 // box size = 16
    data[4] = 'f'.code.toByte(); data[5] = 't'.code.toByte()
    data[6] = 'y'.code.toByte(); data[7] = 'p'.code.toByte()
    for (i in brand.indices) data[8 + i] = brand[i].code.toByte()
    return data.asBytes()
  }

  /**
   * Builds a minimal EBML header with the given doctype string embedded.
   * Produces at least 48 bytes so the codec's doctype scan (>=40 bytes) triggers.
   */
  private fun buildEbml(doctype: String): Bytes {
    val data = ByteArray(48 + doctype.length)
    // EBML Element ID
    data[0] = 0x1A.toByte(); data[1] = 0x45.toByte()
    data[2] = 0xDF.toByte(); data[3] = 0xA3.toByte()
    // Write doctype string starting at offset 10 (within the first 64 bytes)
    val offset = 10
    for (i in doctype.indices) data[offset + i] = doctype[i].code.toByte()
    return data.asBytes()
  }
}
