package dev.transmute.video

import dev.transmute.model.core.asBytes
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
    assertEquals(VideoFormat.Mp4, VideoFormatDetector.detect(mp4Header.asBytes()))
  }

  @Test
  fun detectMp4Mp41() {
    val mp4Header = byteArrayOf(
      0x00, 0x00, 0x00, 0x14,
      'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
      'm'.code.toByte(), 'p'.code.toByte(), '4'.code.toByte(), '1'.code.toByte(),
    )
    assertEquals(VideoFormat.Mp4, VideoFormatDetector.detect(mp4Header.asBytes()))
  }

  @Test
  fun detectMov() {
    // QuickTime "qt  " brand
    val movHeader = byteArrayOf(
      0x00, 0x00, 0x00, 0x14,
      'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
      'q'.code.toByte(), 't'.code.toByte(), ' '.code.toByte(), ' '.code.toByte(),
    )
    assertEquals(VideoFormat.Mov, VideoFormatDetector.detect(movHeader.asBytes()))
  }

  @Test
  fun detectWebM() {
    // Proper EBML header with DocType element (0x4282) containing "webm"
    val data = ByteArray(64)
    data[0] = 0x1A.toByte(); data[1] = 0x45.toByte(); data[2] = 0xDF.toByte(); data[3] = 0xA3.toByte()
    data[4] = 0x87.toByte() // EBML header payload size = 7 bytes
    // DocType element: ID=0x4282, VINT size=0x84 (4 bytes), data="webm"
    data[5] = 0x42.toByte(); data[6] = 0x82.toByte(); data[7] = 0x84.toByte()
    "webm".encodeToByteArray().copyInto(data, destinationOffset = 8)
    assertEquals(VideoFormat.Webm, VideoFormatDetector.detect(data.asBytes()))
  }

  @Test
  fun detectAvi() {
    // RIFF....AVI
    val aviHeader = byteArrayOf(
      'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
      0x00, 0x00, 0x00, 0x00,
      'A'.code.toByte(), 'V'.code.toByte(), 'I'.code.toByte(), ' '.code.toByte(),
    )
    assertEquals(VideoFormat.Avi, VideoFormatDetector.detect(aviHeader.asBytes()))
  }

  @Test
  fun detectMkv() {
    // Proper EBML header with DocType element (0x4282) containing "matroska"
    val data = ByteArray(64)
    data[0] = 0x1A.toByte(); data[1] = 0x45.toByte(); data[2] = 0xDF.toByte(); data[3] = 0xA3.toByte()
    data[4] = 0x8B.toByte() // EBML header payload size = 11 bytes
    // DocType element: ID=0x4282, VINT size=0x88 (8 bytes), data="matroska"
    data[5] = 0x42.toByte(); data[6] = 0x82.toByte(); data[7] = 0x88.toByte()
    "matroska".encodeToByteArray().copyInto(data, destinationOffset = 8)
    assertEquals(VideoFormat.Mkv, VideoFormatDetector.detect(data.asBytes()))
  }

  @Test
  fun detectMkvWhenDocTypeAppearsLaterInHeader() {
    val data = ByteArray(320)
    data[0] = 0x1A.toByte(); data[1] = 0x45.toByte(); data[2] = 0xDF.toByte(); data[3] = 0xA3.toByte()
    data[4] = 0x40.toByte(); data[5] = 0xCE.toByte() // EBML header payload size = 206 bytes
    data[200] = 0x42.toByte(); data[201] = 0x82.toByte(); data[202] = 0x88.toByte()
    "matroska".encodeToByteArray().copyInto(data, destinationOffset = 203)

    assertEquals(VideoFormat.Mkv, VideoFormatDetector.detect(data.asBytes()))
  }

  @Test
  fun detectMkvIgnoresPayloadFalsePositiveAfterHeader() {
    val data = ByteArray(160)
    data[0] = 0x1A.toByte(); data[1] = 0x45.toByte(); data[2] = 0xDF.toByte(); data[3] = 0xA3.toByte()
    data[4] = 0x8B.toByte()
    data[5] = 0x42.toByte(); data[6] = 0x82.toByte(); data[7] = 0x88.toByte()
    "matroska".encodeToByteArray().copyInto(data, destinationOffset = 8)

    data[100] = 0x42.toByte(); data[101] = 0x82.toByte(); data[102] = 0x84.toByte()
    "webm".encodeToByteArray().copyInto(data, destinationOffset = 103)

    assertEquals(VideoFormat.Mkv, VideoFormatDetector.detect(data.asBytes()))
  }

  @Test
  fun detectMkvWhenDocTypeHasTrailingNullPadding() {
    val data = ByteArray(64)
    data[0] = 0x1A.toByte(); data[1] = 0x45.toByte(); data[2] = 0xDF.toByte(); data[3] = 0xA3.toByte()
    data[4] = 0x8C.toByte() // EBML header payload size = 12 bytes
    data[5] = 0x42.toByte(); data[6] = 0x82.toByte(); data[7] = 0x89.toByte()
    "matroska".encodeToByteArray().copyInto(data, destinationOffset = 8)
    data[16] = 0x00

    assertEquals(VideoFormat.Mkv, VideoFormatDetector.detect(data.asBytes()))
  }

  @Test
  fun detectUnknown() {
    val unknown = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05)
    assertEquals(VideoFormat.Unknown, VideoFormatDetector.detect(unknown.asBytes()))
  }

  @Test
  fun detectTooShort() {
    val tooShort = byteArrayOf(0x00, 0x01)
    assertEquals(VideoFormat.Unknown, VideoFormatDetector.detect(tooShort.asBytes()))
  }
}
