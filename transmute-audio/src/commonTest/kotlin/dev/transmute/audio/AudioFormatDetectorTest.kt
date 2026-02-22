package dev.transmute.audio

import dev.transmute.core.asBytes
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
    assertEquals(AudioFormat.Wav, AudioFormatDetector.detect(wavHeader.asBytes()))
  }

  @Test
  fun detectFlac() {
    val flacHeader = byteArrayOf(
      'f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte(),
    )
    assertEquals(AudioFormat.Flac, AudioFormatDetector.detect(flacHeader.asBytes()))
  }

  @Test
  fun detectOgg() {
    val oggHeader = byteArrayOf(
      'O'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(), 'S'.code.toByte(),
    )
    assertEquals(AudioFormat.Ogg, AudioFormatDetector.detect(oggHeader.asBytes()))
  }

  @Test
  fun detectOpus() {
    val data = ByteArray(36)
    data[0] = 'O'.code.toByte(); data[1] = 'g'.code.toByte(); data[2] = 'g'.code.toByte(); data[3] = 'S'.code.toByte()
    val opus = "OpusHead".encodeToByteArray()
    opus.copyInto(data, destinationOffset = 28)
    assertEquals(AudioFormat.Opus, AudioFormatDetector.detect(data.asBytes()))
  }

  @Test
  fun detectMp3WithId3() {
    val mp3Id3Header = byteArrayOf(
      'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
      0x04, 0x00, 0x00, 0x00,
    )
    assertEquals(AudioFormat.Mp3, AudioFormatDetector.detect(mp3Id3Header.asBytes()))
  }

  @Test
  fun detectMp3FrameSync() {
    val mp3FrameSync = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00)
    assertEquals(AudioFormat.Mp3, AudioFormatDetector.detect(mp3FrameSync.asBytes()))
  }

  @Test
  fun detectAacAdts() {
    val aacHeader = byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x00, 0x00)
    assertEquals(AudioFormat.Aac, AudioFormatDetector.detect(aacHeader.asBytes()))
  }

  @Test
  fun detectM4aFtyp() {
    val data = ByteArray(16)
    // 4-byte size, then "ftyp"
    data[0] = 0x00; data[1] = 0x00; data[2] = 0x00; data[3] = 0x18
    data[4] = 'f'.code.toByte(); data[5] = 't'.code.toByte(); data[6] = 'y'.code.toByte(); data[7] = 'p'.code.toByte()
    data[8] = 'M'.code.toByte(); data[9] = '4'.code.toByte(); data[10] = 'A'.code.toByte(); data[11] = ' '.code.toByte()
    assertEquals(AudioFormat.M4a, AudioFormatDetector.detect(data.asBytes()))
  }

  @Test
  fun detectUnknownFormat() {
    val random = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05)
    assertEquals(AudioFormat.Unknown, AudioFormatDetector.detect(random.asBytes()))
  }

  @Test
  fun detectTooShort() {
    val tooShort = byteArrayOf(0x00, 0x01)
    assertEquals(AudioFormat.Unknown, AudioFormatDetector.detect(tooShort.asBytes()))
  }
}
