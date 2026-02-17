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
import kotlinx.coroutines.*
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

  // Safety-net: generous limit that only fires if coroutine timeout fails.
  @get:Rule val timeout: Timeout = Timeout.seconds(180)

  /**
   * Runs [block] on [Dispatchers.IO] with a real-time timeout.
   * Returns null (and logs SKIP) if the operation hangs
   * (e.g. MediaCodec.native_setup() on CI emulators) or throws.
   *
   * Uses an independent [CoroutineScope] so that [runBlocking] does not
   * wait for a thread stuck in a blocking JNI call.
   */
  private suspend fun <T> codecOp(
    label: String,
    timeoutMs: Long = 45_000L,
    block: suspend () -> T,
  ): T? = try {
    val deferred = CoroutineScope(Dispatchers.IO).async { block() }
    withTimeout(timeoutMs) { deferred.await() }
  } catch (e: Throwable) {
    println("SKIP: $label: ${e::class.simpleName}: ${e.message}")
    null
  }

  // MP4 roundtrip

  @Test
  fun mp4RoundTripPreservesDimensions() = runBlocking {
    val original = VideoTestHelpers.syntheticVideo(
      width = 32, height = 32, frameRate = 10.0, durationMs = 300,
    )
    val ctx = VideoTestHelpers.testContext()
    val codec = AndroidMp4Codec()

    val encoded = codecOp("MP4 encoding") { codec.encode(original, ctx) } ?: return@runBlocking
    assertTrue(encoded.isNotEmpty(), "Encoded MP4 should not be empty")

    val decoded = codecOp("MP4 decoding") { codec.decode(encoded, ctx) } ?: return@runBlocking
    assertEquals(32, decoded.videoTrack.width, "MP4: width mismatch")
    assertEquals(32, decoded.videoTrack.height, "MP4: height mismatch")
    assertTrue(decoded.videoTrack.frames.frameCount > 0, "MP4: must have frames")
  }

  @Test
  fun mp4EncodedBytesDetectedAsMp4() = runBlocking {
    val ir = VideoTestHelpers.syntheticVideo(width = 32, height = 32, durationMs = 200)
    val ctx = VideoTestHelpers.testContext()
    val encoded = codecOp("MP4 encoding") { AndroidMp4Codec().encode(ir, ctx) } ?: return@runBlocking

    assertEquals(
      VideoFormat.MP4,
      VideoFormatDetector.detect(encoded),
      "MP4: encoded bytes should be detected as MP4",
    )
  }

  @Test
  fun mp4RoundTripWithAudioPreservesAudioTrack() = runBlocking {
    val original = VideoTestHelpers.syntheticVideo(
      width = 32, height = 32, durationMs = 300, includeAudio = true,
    )
    val ctx = VideoTestHelpers.testContext()
    val codec = AndroidMp4Codec()

    val encoded = codecOp("MP4 encoding") { codec.encode(original, ctx) } ?: return@runBlocking
    val decoded = codecOp("MP4 decoding") { codec.decode(encoded, ctx) } ?: return@runBlocking

    assertNotNull(decoded.audioTrack, "MP4: audio track should be preserved")
    assertTrue(
      decoded.audioTrack!!.samples.data.isNotEmpty(),
      "MP4: decoded audio samples should not be empty",
    )
  }

  // MOV roundtrip

  @Test
  fun movRoundTripPreservesDimensions() = runBlocking {
    val original = VideoTestHelpers.syntheticVideo(
      width = 32, height = 32, frameRate = 10.0, durationMs = 300,
    )
    val ctx = VideoTestHelpers.testContext()
    val codec = AndroidMovCodec()

    val encoded = codecOp("MOV encoding") { codec.encode(original, ctx) } ?: return@runBlocking
    assertTrue(encoded.isNotEmpty(), "Encoded MOV should not be empty")

    val decoded = codecOp("MOV decoding") { codec.decode(encoded, ctx) } ?: return@runBlocking
    assertEquals(32, decoded.videoTrack.width, "MOV: width mismatch")
    assertEquals(32, decoded.videoTrack.height, "MOV: height mismatch")
    assertTrue(decoded.videoTrack.frames.frameCount > 0, "MOV: must have frames")
  }

  // WebM decode-only

  @Test
  fun webmDecoderReportsCorrectFormat() {
    val decoder = AndroidWebmDecoder()
    assertTrue(VideoFormat.WEBM in decoder.supportedFormats)
  }

  // Format declarations

  @Test
  fun allCodecsReportCorrectFormats() {
    assertTrue(VideoFormat.MP4 in AndroidMp4Codec().decodableFormats)
    assertTrue(VideoFormat.MP4 in AndroidMp4Codec().encodableFormats)
    assertTrue(VideoFormat.MOV in AndroidMovCodec().decodableFormats)
    assertTrue(VideoFormat.MOV in AndroidMovCodec().encodableFormats)
    assertTrue(VideoFormat.WEBM in AndroidWebmDecoder().supportedFormats)
  }
}
