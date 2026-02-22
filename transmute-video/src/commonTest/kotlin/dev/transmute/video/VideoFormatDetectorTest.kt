package dev.transmute.video

import dev.transmute.core.VideoFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class VideoFormatDetectorTest {

  @Test
  fun detectMp4Isom() {
    // ftyp box with "isom" brand
    val mp4Header = byteArrayOf(
      0x00, 0x00, 0x00, 0x18,  // box size
      'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
      'i'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
      0x00, 0x00, 0x02, 0x00,  // minor version
    )
    assertEquals(VideoFormat.MP4, VideoFormatDetector.detect(mp4Header))
  }

  @Test
  fun detectMp4Mp41() {
    val mp4Header = byteArrayOf(
      0x00, 0x00, 0x00, 0x14,
      'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
      'm'.code.toByte(), 'p'.code.toByte(), '4'.code.toByte(), '1'.code.toByte(),
    )
    assertEquals(VideoFormat.MP4, VideoFormatDetector.detect(mp4Header))
  }

  @Test
  fun detectMov() {
    // QuickTime "qt  " brand
    val movHeader = byteArrayOf(
      0x00, 0x00, 0x00, 0x14,
      'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
      'q'.code.toByte(), 't'.code.toByte(), ' '.code.toByte(), ' '.code.toByte(),
    )
    assertEquals(VideoFormat.MOV, VideoFormatDetector.detect(movHeader))
  }

  @Test
  fun detectWebM() {
    val data = ByteArray(64)
    // EBML header magic bytes
    data[0] = 0x1A.toByte(); data[1] = 0x45.toByte(); data[2] = 0xDF.toByte(); data[3] = 0xA3.toByte()
    "webm".encodeToByteArray().copyInto(data, destinationOffset = 24)
    assertEquals(VideoFormat.WEBM, VideoFormatDetector.detect(data))
  }

  @Test
  fun detectAvi() {
    // RIFF....AVI
    val aviHeader = byteArrayOf(
      'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
      0x00, 0x00, 0x00, 0x00,
      'A'.code.toByte(), 'V'.code.toByte(), 'I'.code.toByte(), ' '.code.toByte(),
    )
    assertEquals(VideoFormat.AVI, VideoFormatDetector.detect(aviHeader))
  }

  @Test
  fun detectMkv() {
    val data = ByteArray(64)
    data[0] = 0x1A.toByte(); data[1] = 0x45.toByte(); data[2] = 0xDF.toByte(); data[3] = 0xA3.toByte()
    "matroska".encodeToByteArray().copyInto(data, destinationOffset = 24)
    assertEquals(VideoFormat.MKV, VideoFormatDetector.detect(data))
  }

  @Test
  fun detectUnknown() {
    val unknown = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05)
    assertEquals(VideoFormat.UNKNOWN, VideoFormatDetector.detect(unknown))
  }

  @Test
  fun detectTooShort() {
    val tooShort = byteArrayOf(0x00, 0x01)
    assertEquals(VideoFormat.UNKNOWN, VideoFormatDetector.detect(tooShort))
  }
}
