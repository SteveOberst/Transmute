package dev.transmute.audio.codecs

import dev.transmute.core.AudioFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WavDecoderSniffTest {

  private val decoder = WavDecoder()

  @Test
  fun sniffWavHeader() {
    val data = byteArrayOf(
      'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
      0x00, 0x00, 0x00, 0x00,
      'W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte(),
    )
    assertEquals(AudioFormat.WAV, decoder.sniff(data))
  }

  @Test
  fun sniffRejectsAvi() {
    // RIFF + AVI is not WAV
    val data = byteArrayOf(
      'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
      0x00, 0x00, 0x00, 0x00,
      'A'.code.toByte(), 'V'.code.toByte(), 'I'.code.toByte(), ' '.code.toByte(),
    )
    assertNull(decoder.sniff(data))
  }

  @Test
  fun sniffRejectsWebp() {
    // RIFF + WEBP is not WAV
    val data = byteArrayOf(
      'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
      0x00, 0x00, 0x00, 0x00,
      'W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte(),
    )
    assertNull(decoder.sniff(data))
  }

  @Test
  fun sniffRejectsTooShort() {
    assertNull(decoder.sniff(ByteArray(0)))
    assertNull(decoder.sniff(byteArrayOf(0x52, 0x49, 0x46, 0x46))) // just RIFF
    assertNull(decoder.sniff(ByteArray(11))) // one byte short
  }

  @Test
  fun sniffRejectsRandomData() {
    val garbage = ByteArray(64) { (it * 7 + 3).toByte() }
    assertNull(decoder.sniff(garbage))
  }
}
