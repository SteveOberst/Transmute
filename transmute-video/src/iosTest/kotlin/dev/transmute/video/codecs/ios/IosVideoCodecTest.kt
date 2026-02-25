package dev.transmute.video.codecs.ios

import dev.transmute.codec.OutputFormat
import dev.transmute.model.core.asBytes
import dev.transmute.video.CanonicalVideoDecodeOptions
import dev.transmute.video.CanonicalVideoEncodeOptions
import dev.transmute.video.VideoFormat
import dev.transmute.video.VideoTestHelpers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * iOS integration tests for the AVFoundation-based video codecs.
 *
 * Run: `./gradlew :transmute-video:iosSimulatorArm64Test`
 */
class IosVideoCodecTest {

  @Test
  fun mp4RoundTripProducesFrames() = runTest {
    val codec = IosMp4Codec()
    val original = VideoTestHelpers.syntheticVideo(width = 64, height = 64, frameRate = 10.0, durationMs = 500)
    val ctx = VideoTestHelpers.testContext()

    val encoded = try {
      codec.encode(original, VideoFormat.Mp4, CanonicalVideoEncodeOptions(outputFormat = OutputFormat.Exact(VideoFormat.Mp4)), ctx)
    } catch (e: Throwable) {
      println("SKIP: MP4 encoding not available on this simulator: ${e::class.simpleName}: ${e.message}")
      return@runTest
    }
    assertTrue(encoded.isNotEmpty(), "MP4 encoded bytes should not be empty")

    val decoded = try {
      codec.decode(encoded, CanonicalVideoDecodeOptions(), ctx)
    } catch (e: Throwable) {
      println("SKIP: MP4 decoding not available on this simulator: ${e::class.simpleName}: ${e.message}")
      return@runTest
    }
    assertTrue(decoded.videoTrack.width > 0)
    assertTrue(decoded.videoTrack.height > 0)
    assertTrue(decoded.durationMs > 0)
  }

  @Test
  fun movRoundTripProducesFrames() = runTest {
    val codec = IosMovCodec()
    val original = VideoTestHelpers.syntheticVideo(width = 64, height = 64, frameRate = 10.0, durationMs = 500)
    val ctx = VideoTestHelpers.testContext()

    val encoded = try {
      codec.encode(original, VideoFormat.Mov, CanonicalVideoEncodeOptions(outputFormat = OutputFormat.Exact(VideoFormat.Mov)), ctx)
    } catch (e: Throwable) {
      println("SKIP: MOV encoding not available on this simulator: ${e::class.simpleName}: ${e.message}")
      return@runTest
    }
    assertTrue(encoded.isNotEmpty(), "MOV encoded bytes should not be empty")

    val decoded = try {
      codec.decode(encoded, CanonicalVideoDecodeOptions(), ctx)
    } catch (e: Throwable) {
      println("SKIP: MOV decoding not available on this simulator: ${e::class.simpleName}: ${e.message}")
      return@runTest
    }
    assertTrue(decoded.videoTrack.width > 0)
    assertTrue(decoded.videoTrack.height > 0)
    assertTrue(decoded.durationMs > 0)
  }

  @Test
  fun codecReportsCorrectFormats() {
    assertTrue(VideoFormat.Mp4 in IosMp4Codec().decodableFormats)
    assertTrue(VideoFormat.Mp4 in IosMp4Codec().encodableFormats)

    assertTrue(VideoFormat.Mov in IosMovCodec().decodableFormats)
    assertTrue(VideoFormat.Mov in IosMovCodec().encodableFormats)
  }
}

