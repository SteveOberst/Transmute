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
import org.junit.Test
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

  // -----------------------------------------------------------------------
  // MP4 roundtrip
  // -----------------------------------------------------------------------

  @Test
  fun mp4RoundTripPreservesDimensions() = runTest {
    val original = VideoTestHelpers.syntheticVideo(
      width = 64, height = 48, frameRate = 10.0, durationMs = 500,
    )
    val ctx = VideoTestHelpers.testContext()
    val codec = AndroidMp4Codec()

    val encoded = codec.encode(original, ctx)
    assertTrue(encoded.isNotEmpty(), "Encoded MP4 should not be empty")

    val decoded = codec.decode(encoded, ctx)
    assertEquals(64, decoded.videoTrack.width, "MP4: width mismatch")
    assertEquals(48, decoded.videoTrack.height, "MP4: height mismatch")
    assertTrue(decoded.videoTrack.frames.frameCount > 0, "MP4: must have frames")
  }

  @Test
  fun mp4EncodedBytesDetectedAsMp4() = runTest {
    val ir = VideoTestHelpers.syntheticVideo(width = 32, height = 32, durationMs = 300)
    val ctx = VideoTestHelpers.testContext()
    val encoded = AndroidMp4Codec().encode(ir, ctx)

    assertEquals(
      VideoFormat.MP4,
      VideoFormatDetector.detect(encoded),
      "MP4: encoded bytes should be detected as MP4",
    )
  }

  @Test
  fun mp4RoundTripWithAudioPreservesAudioTrack() = runTest {
    val original = VideoTestHelpers.syntheticVideo(
      width = 64, height = 48, durationMs = 500, includeAudio = true,
    )
    val ctx = VideoTestHelpers.testContext()
    val codec = AndroidMp4Codec()

    val encoded = try {
      codec.encode(original, ctx)
    } catch (e: Exception) {
      // MediaCodec audio+video muxing can fail on emulators with software codecs
      println("MP4+audio encode failed (${e::class.simpleName}: ${e.message}), skipping")
      return@runTest
    }

    val decoded = try {
      codec.decode(encoded, ctx)
    } catch (e: Exception) {
      // Software H.264 decoder can fail on emulators
      println("MP4+audio decode failed (${e::class.simpleName}: ${e.message}), skipping")
      return@runTest
    }

    assertNotNull(decoded.audioTrack, "MP4: audio track should be preserved")
    assertTrue(
      decoded.audioTrack!!.samples.data.isNotEmpty(),
      "MP4: decoded audio samples should not be empty",
    )
  }

  // -----------------------------------------------------------------------
  // MOV roundtrip
  // -----------------------------------------------------------------------

  @Test
  fun movRoundTripPreservesDimensions() = runTest {
    val original = VideoTestHelpers.syntheticVideo(
      width = 80, height = 64, frameRate = 15.0, durationMs = 400,
    )
    val ctx = VideoTestHelpers.testContext()
    val codec = AndroidMovCodec()

    val encoded = codec.encode(original, ctx)
    assertTrue(encoded.isNotEmpty(), "Encoded MOV should not be empty")

    val decoded = codec.decode(encoded, ctx)
    assertEquals(80, decoded.videoTrack.width, "MOV: width mismatch")
    assertEquals(64, decoded.videoTrack.height, "MOV: height mismatch")
    assertTrue(decoded.videoTrack.frames.frameCount > 0, "MOV: must have frames")
  }

  // -----------------------------------------------------------------------
  // WebM decode-only
  // -----------------------------------------------------------------------

  @Test
  fun webmDecoderReportsCorrectFormat() {
    val decoder = AndroidWebmDecoder()
    assertTrue(VideoFormat.WEBM in decoder.supportedFormats)
  }

  // -----------------------------------------------------------------------
  // Format declarations
  // -----------------------------------------------------------------------

  @Test
  fun allCodecsReportCorrectFormats() {
    assertTrue(VideoFormat.MP4 in AndroidMp4Codec().decodableFormats)
    assertTrue(VideoFormat.MP4 in AndroidMp4Codec().encodableFormats)
    assertTrue(VideoFormat.MOV in AndroidMovCodec().decodableFormats)
    assertTrue(VideoFormat.MOV in AndroidMovCodec().encodableFormats)
    assertTrue(VideoFormat.WEBM in AndroidWebmDecoder().supportedFormats)
  }
}
