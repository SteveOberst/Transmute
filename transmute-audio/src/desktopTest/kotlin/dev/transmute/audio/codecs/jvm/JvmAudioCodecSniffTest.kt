package dev.transmute.audio.codecs.jvm

import dev.transmute.audio.AudioFormat
import dev.transmute.model.core.asBytes
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
    assertEquals(AudioFormat.Mp3, mp3.sniff(data.asBytes()))
  }

  @Test
  fun mp3SniffsFrameSync() {
    // 0xFF 0xFB = MPEG1, Layer III, no CRC
    val data = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00)
    assertEquals(AudioFormat.Mp3, mp3.sniff(data.asBytes()))
  }

  @Test
  fun mp3RejectsAacAdts() {
    // 0xFF 0xF1 = MPEG2, Layer 0 (AAC ADTS)
    val data = byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x00, 0x00)
    assertNull(mp3.sniff(data.asBytes()))
  }

  @Test
  fun mp3RejectsFlac() {
    val data = byteArrayOf(0x66, 0x4C, 0x61, 0x43)
    assertNull(mp3.sniff(data.asBytes()))
  }

  @Test
  fun mp3RejectsTooShort() {
    assertNull(mp3.sniff(ByteArray(0).asBytes()))
    assertNull(mp3.sniff(byteArrayOf(0x49, 0x44).asBytes())) // "ID" without '3'
  }

  // ---- JvmFlacCodec ----

  private val flac = JvmFlacCodec()

  @Test
  fun flacSniffsFlacMarker() {
    val data = byteArrayOf(0x66, 0x4C, 0x61, 0x43) // "fLaC"
    assertEquals(AudioFormat.Flac, flac.sniff(data.asBytes()))
  }

  @Test
  fun flacRejectsMp3() {
    val data = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00)
    assertNull(flac.sniff(data.asBytes()))
  }

  @Test
  fun flacRejectsTooShort() {
    assertNull(flac.sniff(byteArrayOf(0x66, 0x4C, 0x61).asBytes())) // 3 bytes
    assertNull(flac.sniff(ByteArray(0).asBytes()))
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
    assertEquals(AudioFormat.Ogg, ogg.sniff(data.asBytes()))
  }

  @Test
  fun oggRejectsOpus() {
    // OGG container with Opus header (NOT Vorbis)
    val data = ByteArray(36)
    data[0] = 'O'.code.toByte(); data[1] = 'g'.code.toByte()
    data[2] = 'g'.code.toByte(); data[3] = 'S'.code.toByte()
    data[28] = 'O'.code.toByte(); data[29] = 'p'.code.toByte()
    data[30] = 'u'.code.toByte(); data[31] = 's'.code.toByte()
    assertNull(ogg.sniff(data.asBytes()))
  }

  @Test
  fun oggSniffsShortOggContainer() {
    // With only 4 bytes ("OggS"), OGG Vorbis claims it as generic OGG
    val data = byteArrayOf(
      'O'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(), 'S'.code.toByte(),
    )
    assertEquals(AudioFormat.Ogg, ogg.sniff(data.asBytes()))
  }

  @Test
  fun oggRejectsTooShort() {
    assertNull(ogg.sniff(ByteArray(3).asBytes()))
    assertNull(ogg.sniff(ByteArray(0).asBytes()))
  }

  // Note: AAC, M4A, and Opus sniff tests live in the transmute-gstreamer module
  // alongside the GStreamer-backed codec implementations.
}
