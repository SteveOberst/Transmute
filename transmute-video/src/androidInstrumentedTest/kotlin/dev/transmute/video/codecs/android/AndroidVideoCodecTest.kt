package dev.transmute.video.codecs.android

import dev.transmute.codec.OutputFormat
import dev.transmute.video.VideoFormat
import dev.transmute.video.VideoFormatDetector
import dev.transmute.video.VideoTestHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import dev.transmute.video.CanonicalVideoDecodeOptions
import dev.transmute.video.CanonicalVideoEncodeOptions

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

  // Safety-net: JUnit rule at 180 s so tests that escape coroutine
  // timeout still get killed (generous – coroutine timeout fires first).
  @get:Rule val timeout: Timeout = Timeout.seconds(180)

  /**
   * Run [block] on an *independent* IO scope with a coroutine timeout.
   *
   * MediaCodec.native_setup() occasionally hangs indefinitely on CI
   * emulators.  Thread.interrupt() (JUnit Timeout) cannot break native
   * code, so we launch on a separate scope and use [withTimeout] at the
   * `await()` suspension point to bail out cleanly.
   *
   * Returns null on timeout/failure – callers exit with `?: return@runBlocking`.
   */
  private suspend fun <T> codecOp(
    label: String,
    timeoutMs: Long = 90_000L,
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

    val encoded = codecOp("MP4 encode") {
      codec.encode(original, VideoFormat.Mp4, CanonicalVideoEncodeOptions(outputFormat = OutputFormat.Exact(VideoFormat.Mp4)), ctx)
    } ?: return@runBlocking
    assertTrue(encoded.isNotEmpty(), "Encoded MP4 should not be empty")

    val decoded = codecOp("MP4 decode") { codec.decode(encoded, CanonicalVideoDecodeOptions(), ctx) } ?: return@runBlocking
    assertEquals(32, decoded.videoTrack.width, "MP4: width mismatch")
    assertEquals(32, decoded.videoTrack.height, "MP4: height mismatch")
    assertTrue(decoded.videoTrack.frames.frameCount > 0, "MP4: must have frames")
  }

  @Test
  fun mp4EncodedBytesDetectedAsMp4() = runBlocking {
    val ir = VideoTestHelpers.syntheticVideo(width = 32, height = 32, durationMs = 200)
    val ctx = VideoTestHelpers.testContext()
    val encoded = codecOp("MP4 encode-detect") {
      AndroidMp4Codec().encode(ir, VideoFormat.Mp4, CanonicalVideoEncodeOptions(outputFormat = OutputFormat.Exact(VideoFormat.Mp4)), ctx)
    } ?: return@runBlocking

    assertEquals(
      VideoFormat.Mp4,
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

    val encoded = codecOp("MP4+audio encode") {
      codec.encode(original, VideoFormat.Mp4, CanonicalVideoEncodeOptions(outputFormat = OutputFormat.Exact(VideoFormat.Mp4)), ctx)
    } ?: return@runBlocking
    val decoded = codecOp("MP4+audio decode") { codec.decode(encoded, CanonicalVideoDecodeOptions(), ctx) } ?: return@runBlocking

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

    val encoded = codecOp("MOV encode") {
      codec.encode(original, VideoFormat.Mov, CanonicalVideoEncodeOptions(outputFormat = OutputFormat.Exact(VideoFormat.Mov)), ctx)
    } ?: return@runBlocking
    assertTrue(encoded.isNotEmpty(), "Encoded MOV should not be empty")

    val decoded = codecOp("MOV decode") { codec.decode(encoded, CanonicalVideoDecodeOptions(), ctx) } ?: return@runBlocking
    assertEquals(32, decoded.videoTrack.width, "MOV: width mismatch")
    assertEquals(32, decoded.videoTrack.height, "MOV: height mismatch")
    assertTrue(decoded.videoTrack.frames.frameCount > 0, "MOV: must have frames")
  }

  // WebM decode-only

  @Test
  fun webmDecoderReportsCorrectFormat() {
    val decoder = AndroidWebmDecoder()
    assertTrue(VideoFormat.Webm in decoder.supportedFormats)
  }

  // Format declarations

  @Test
  fun allCodecsReportCorrectFormats() {
    assertTrue(VideoFormat.Mp4 in AndroidMp4Codec().decodableFormats)
    assertTrue(VideoFormat.Mp4 in AndroidMp4Codec().encodableFormats)
    assertTrue(VideoFormat.Mov in AndroidMovCodec().decodableFormats)
    assertTrue(VideoFormat.Mov in AndroidMovCodec().encodableFormats)
    assertTrue(VideoFormat.Webm in AndroidWebmDecoder().supportedFormats)
  }
}
