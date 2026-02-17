package dev.transmute.audio.codecs.jvm

import dev.transmute.core.AudioFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Direct sniff() tests for all JVM audio codecs. */
class JvmAudioCodecSniffTest {

  // ---- JvmMp3Codec ----

  private val mp3 = JvmMp3Codec()

  @Test
  fun mp3SniffsId3v2Tag() {
    val data = byteArrayOf(
      'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
      0x04, 0x00, 0x00, 0x00,
    )
    assertEquals(AudioFormat.MP3, mp3.sniff(data))
  }

  @Test
  fun mp3SniffsFrameSync() {
    // 0xFF 0xFB = MPEG1, Layer III, no CRC
    val data = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00)
    assertEquals(AudioFormat.MP3, mp3.sniff(data))
  }

  @Test
  fun mp3RejectsAacAdts() {
    // 0xFF 0xF1 = MPEG2, Layer 0 (AAC ADTS)
    val data = byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x00, 0x00)
    assertNull(mp3.sniff(data))
  }

  @Test
  fun mp3RejectsFlac() {
    val data = byteArrayOf(0x66, 0x4C, 0x61, 0x43)
    assertNull(mp3.sniff(data))
  }

  @Test
  fun mp3RejectsTooShort() {
    assertNull(mp3.sniff(ByteArray(0)))
    assertNull(mp3.sniff(byteArrayOf(0x49, 0x44))) // "ID" without '3'
  }

  // ---- JvmFlacCodec ----

  private val flac = JvmFlacCodec()

  @Test
  fun flacSniffsFlacMarker() {
    val data = byteArrayOf(0x66, 0x4C, 0x61, 0x43) // "fLaC"
    assertEquals(AudioFormat.FLAC, flac.sniff(data))
  }

  @Test
  fun flacRejectsMp3() {
    val data = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00)
    assertNull(flac.sniff(data))
  }

  @Test
  fun flacRejectsTooShort() {
    assertNull(flac.sniff(byteArrayOf(0x66, 0x4C, 0x61))) // 3 bytes
    assertNull(flac.sniff(ByteArray(0)))
  }

  // ---- JvmOggVorbisCodec ----

  private val ogg = JvmOggVorbisCodec()

  @Test
  fun oggSniffsVorbis() {
    // OGG container with Vorbis identification header
    val data = ByteArray(36)
    data[0] = 'O'.code.toByte(); data[1] = 'g'.code.toByte()
    data[2] = 'g'.code.toByte(); data[3] = 'S'.code.toByte()
    // Vorbis id at byte 28: 0x01 + "vorbis"
    data[28] = 0x01
    data[29] = 'v'.code.toByte(); data[30] = 'o'.code.toByte()
    data[31] = 'r'.code.toByte(); data[32] = 'b'.code.toByte()
    data[33] = 'i'.code.toByte(); data[34] = 's'.code.toByte()
    assertEquals(AudioFormat.OGG, ogg.sniff(data))
  }

  @Test
  fun oggRejectsOpus() {
    // OGG container with Opus header (NOT Vorbis)
    val data = ByteArray(36)
    data[0] = 'O'.code.toByte(); data[1] = 'g'.code.toByte()
    data[2] = 'g'.code.toByte(); data[3] = 'S'.code.toByte()
    data[28] = 'O'.code.toByte(); data[29] = 'p'.code.toByte()
    data[30] = 'u'.code.toByte(); data[31] = 's'.code.toByte()
    assertNull(ogg.sniff(data))
  }

  @Test
  fun oggSniffsShortOggContainer() {
    // With only 4 bytes ("OggS"), OGG Vorbis claims it as generic OGG
    val data = byteArrayOf(
      'O'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(), 'S'.code.toByte(),
    )
    assertEquals(AudioFormat.OGG, ogg.sniff(data))
  }

  @Test
  fun oggRejectsTooShort() {
    assertNull(ogg.sniff(ByteArray(3)))
    assertNull(ogg.sniff(ByteArray(0)))
  }

  // ---- JvmAacCodec ----

  private val aac = JvmAacCodec()

  @Test
  fun aacSniffsAdts() {
    // 0xFF 0xF1 = MPEG-2, Layer 0 (AAC), protection absent
    val data = byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x00, 0x00)
    assertEquals(AudioFormat.AAC, aac.sniff(data))
  }

  @Test
  fun aacSniffsAdtsWithCrc() {
    // 0xFF 0xF0 = MPEG-2, Layer 0 (AAC), protection present
    val data = byteArrayOf(0xFF.toByte(), 0xF0.toByte(), 0x00, 0x00)
    assertEquals(AudioFormat.AAC, aac.sniff(data))
  }

  @Test
  fun aacRejectsMp3Frame() {
    // 0xFF 0xFB = MPEG-1, Layer III (MP3)
    val data = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00)
    assertNull(aac.sniff(data))
  }

  @Test
  fun aacRejectsTooShort() {
    assertNull(aac.sniff(byteArrayOf(0xFF.toByte())))
    assertNull(aac.sniff(ByteArray(0)))
  }

  // ---- JvmM4aCodec ----

  private val m4a = JvmM4aCodec()

  @Test
  fun m4aSniffsFtyp() {
    val data = ByteArray(16)
    data[4] = 0x66; data[5] = 0x74; data[6] = 0x79; data[7] = 0x70 // ftyp
    data[8] = 'M'.code.toByte(); data[9] = '4'.code.toByte()
    data[10] = 'A'.code.toByte(); data[11] = ' '.code.toByte()
    assertEquals(AudioFormat.M4A, m4a.sniff(data))
  }

  @Test
  fun m4aRejectsTooShort() {
    assertNull(m4a.sniff(ByteArray(7)))
    assertNull(m4a.sniff(ByteArray(0)))
  }

  @Test
  fun m4aRejectsNoFtyp() {
    val data = ByteArray(16)
    assertNull(m4a.sniff(data))
  }

  // ---- JvmOpusCodec ----

  private val opus = JvmOpusCodec()

  @Test
  fun opusSniffsOggOpus() {
    val data = ByteArray(36)
    data[0] = 'O'.code.toByte(); data[1] = 'g'.code.toByte()
    data[2] = 'g'.code.toByte(); data[3] = 'S'.code.toByte()
    // "OpusHead" at byte 28
    data[28] = 'O'.code.toByte(); data[29] = 'p'.code.toByte()
    data[30] = 'u'.code.toByte(); data[31] = 's'.code.toByte()
    data[32] = 'H'.code.toByte(); data[33] = 'e'.code.toByte()
    data[34] = 'a'.code.toByte(); data[35] = 'd'.code.toByte()
    assertEquals(AudioFormat.OPUS, opus.sniff(data))
  }

  @Test
  fun opusRejectsVorbis() {
    val data = ByteArray(36)
    data[0] = 'O'.code.toByte(); data[1] = 'g'.code.toByte()
    data[2] = 'g'.code.toByte(); data[3] = 'S'.code.toByte()
    data[28] = 0x01
    data[29] = 'v'.code.toByte(); data[30] = 'o'.code.toByte()
    assertNull(opus.sniff(data))
  }

  @Test
  fun opusRejectsTooShort() {
    assertNull(opus.sniff(ByteArray(35)))
    assertNull(opus.sniff(ByteArray(0)))
  }

  @Test
  fun opusRejectsNonOgg() {
    val data = ByteArray(36)
    data[0] = 'R'.code.toByte(); data[1] = 'I'.code.toByte()
    assertNull(opus.sniff(data))
  }
}
