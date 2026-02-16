package dev.transmute.audio

import dev.transmute.core.AudioFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class AudioFormatDetectorTest {

  @Test
  fun detectWav() {
    // RIFF....WAVE header
    val wavHeader = byteArrayOf(
      'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
      0x00, 0x00, 0x00, 0x00,
      'W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte(),
    )
    assertEquals(AudioFormat.WAV, AudioFormatDetector.detect(wavHeader))
  }

  @Test
  fun detectFlac() {
    val flacHeader = byteArrayOf(
      'f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte(),
    )
    assertEquals(AudioFormat.FLAC, AudioFormatDetector.detect(flacHeader))
  }

  @Test
  fun detectOgg() {
    val oggHeader = byteArrayOf(
      'O'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(), 'S'.code.toByte(),
    )
    assertEquals(AudioFormat.OGG, AudioFormatDetector.detect(oggHeader))
  }

  @Test
  fun detectMp3WithId3() {
    val mp3Id3Header = byteArrayOf(
      'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
      0x04, 0x00, 0x00, 0x00,
    )
    assertEquals(AudioFormat.MP3, AudioFormatDetector.detect(mp3Id3Header))
  }

  @Test
  fun detectMp3FrameSync() {
    val mp3FrameSync = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00)
    assertEquals(AudioFormat.MP3, AudioFormatDetector.detect(mp3FrameSync))
  }

  @Test
  fun detectAacAdts() {
    val aacHeader = byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x00, 0x00)
    assertEquals(AudioFormat.AAC, AudioFormatDetector.detect(aacHeader))
  }

  @Test
  fun detectUnknownFormat() {
    val random = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05)
    assertEquals(AudioFormat.UNKNOWN, AudioFormatDetector.detect(random))
  }

  @Test
  fun detectTooShort() {
    val tooShort = byteArrayOf(0x00, 0x01)
    assertEquals(AudioFormat.UNKNOWN, AudioFormatDetector.detect(tooShort))
  }
}
