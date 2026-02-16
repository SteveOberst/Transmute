package dev.transmute.video.codecs.android

import dev.transmute.audio.AudioSamples
import dev.transmute.core.VideoFormat
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.PixelFormat
import dev.transmute.video.AudioTrack
import dev.transmute.video.ListFrameStream
import dev.transmute.video.VideoFormatDetector
import dev.transmute.video.VideoFrame
import dev.transmute.video.VideoIR
import dev.transmute.video.VideoTestHelpers
import dev.transmute.video.VideoTrack
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Android instrumented tests for MediaCodec-based video codecs.
 *
 * These tests run on a real device or emulator and exercise the actual
 * Android MediaCodec / MediaMuxer / MediaExtractor APIs. They produce
 * synthetic video frames, encode them into a container, decode back,
 * and verify dimension + frame preservation.
 *
 * Run: ./gradlew :transmute-video:connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class AndroidVideoCodecTest {

  @get:Rule val timeout: Timeout = Timeout.seconds(60)

  // -----------------------------------------------------------------------
  // MP4 roundtrip
  // -----------------------------------------------------------------------

  @Test
  fun mp4RoundTripPreservesDimensions() = runTest {
    println(">>> mp4RoundTripPreservesDimensions: START")
    val original = VideoTestHelpers.syntheticVideo(
      width = 32, height = 32, frameRate = 10.0, durationMs = 300,
    )
    val ctx = VideoTestHelpers.testContext()
    val codec = AndroidMp4Codec()

    println(">>> mp4RoundTripPreservesDimensions: encoding 32x32 3 frames...")
    val encoded = codec.encode(original, ctx)
    assertTrue(encoded.isNotEmpty(), "Encoded MP4 should not be empty")
    println(">>> mp4RoundTripPreservesDimensions: encoded ${encoded.size} bytes, decoding...")

    val decoded = codec.decode(encoded, ctx)
    assertEquals(32, decoded.videoTrack.width, "MP4: width mismatch")
    assertEquals(32, decoded.videoTrack.height, "MP4: height mismatch")
    assertTrue(decoded.videoTrack.frames.frameCount > 0, "MP4: must have frames")
    println(">>> mp4RoundTripPreservesDimensions: PASS (${decoded.videoTrack.frames.frameCount} frames)")
  }

  @Test
  fun mp4EncodedBytesDetectedAsMp4() = runTest {
    println(">>> mp4EncodedBytesDetectedAsMp4: START")
    val ir = VideoTestHelpers.syntheticVideo(width = 32, height = 32, durationMs = 200)
    val ctx = VideoTestHelpers.testContext()
    println(">>> mp4EncodedBytesDetectedAsMp4: encoding...")
    val encoded = AndroidMp4Codec().encode(ir, ctx)

    assertEquals(
      VideoFormat.MP4,
      VideoFormatDetector.detect(encoded),
      "MP4: encoded bytes should be detected as MP4",
    )
    println(">>> mp4EncodedBytesDetectedAsMp4: PASS")
  }

  @Test
  fun mp4RoundTripWithAudioPreservesAudioTrack() = runTest {
    println(">>> mp4RoundTripWithAudioPreservesAudioTrack: START")
    val original = VideoTestHelpers.syntheticVideo(
      width = 32, height = 32, durationMs = 300, includeAudio = true,
    )
    val ctx = VideoTestHelpers.testContext()
    val codec = AndroidMp4Codec()

    println(">>> mp4RoundTripWithAudioPreservesAudioTrack: encoding video+audio...")
    val encoded = codec.encode(original, ctx)
    println(">>> mp4RoundTripWithAudioPreservesAudioTrack: encoded ${encoded.size} bytes, decoding...")
    val decoded = codec.decode(encoded, ctx)

    assertNotNull(decoded.audioTrack, "MP4: audio track should be preserved")
    assertTrue(
      decoded.audioTrack!!.samples.data.isNotEmpty(),
      "MP4: decoded audio samples should not be empty",
    )
    println(">>> mp4RoundTripWithAudioPreservesAudioTrack: PASS")
  }

  // -----------------------------------------------------------------------
  // MOV roundtrip
  // -----------------------------------------------------------------------

  @Test
  fun movRoundTripPreservesDimensions() = runTest {
    println(">>> movRoundTripPreservesDimensions: START")
    val original = VideoTestHelpers.syntheticVideo(
      width = 32, height = 32, frameRate = 10.0, durationMs = 300,
    )
    val ctx = VideoTestHelpers.testContext()
    val codec = AndroidMovCodec()

    println(">>> movRoundTripPreservesDimensions: encoding...")
    val encoded = codec.encode(original, ctx)
    assertTrue(encoded.isNotEmpty(), "Encoded MOV should not be empty")
    println(">>> movRoundTripPreservesDimensions: encoded ${encoded.size} bytes, decoding...")

    val decoded = codec.decode(encoded, ctx)
    assertEquals(32, decoded.videoTrack.width, "MOV: width mismatch")
    assertEquals(32, decoded.videoTrack.height, "MOV: height mismatch")
    assertTrue(decoded.videoTrack.frames.frameCount > 0, "MOV: must have frames")
    println(">>> movRoundTripPreservesDimensions: PASS")
  }

  // -----------------------------------------------------------------------
  // WebM decode-only
  // -----------------------------------------------------------------------

  @Test
  fun webmDecoderReportsCorrectFormat() {
    println(">>> webmDecoderReportsCorrectFormat: START")
    val decoder = AndroidWebmDecoder()
    assertTrue(VideoFormat.WEBM in decoder.supportedFormats)
    println(">>> webmDecoderReportsCorrectFormat: PASS")
  }

  // -----------------------------------------------------------------------
  // Format declarations
  // -----------------------------------------------------------------------

  @Test
  fun allCodecsReportCorrectFormats() {
    println(">>> allCodecsReportCorrectFormats: START")
    assertTrue(VideoFormat.MP4 in AndroidMp4Codec().decodableFormats)
    assertTrue(VideoFormat.MP4 in AndroidMp4Codec().encodableFormats)
    assertTrue(VideoFormat.MOV in AndroidMovCodec().decodableFormats)
    assertTrue(VideoFormat.MOV in AndroidMovCodec().encodableFormats)
    assertTrue(VideoFormat.WEBM in AndroidWebmDecoder().supportedFormats)
    println(">>> allCodecsReportCorrectFormats: PASS")
  }
}
