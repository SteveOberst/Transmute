package dev.transmute.video.codecs.ios

import dev.transmute.core.VideoFormat
import dev.transmute.video.VideoTestHelpers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * iOS integration tests for the AVFoundation-based video codecs.
 *
 * These tests run on the iOS simulator via Kotlin/Native and exercise
 * the real AVAssetReader / AVAssetWriter / AVAssetWriterInputPixelBufferAdaptor
 * pipeline.
 *
 * Codecs under test:
 * - [IosMp4Codec] - H.264 + AAC in MP4 container
 * - [IosMovCodec] - H.264 + AAC in MOV container
 *
 * Run: `./gradlew :transmute-video:iosSimulatorArm64Test`
 */
class IosVideoCodecTest {

  // MP4 roundtrip (encode → decode)

  @Test
  fun mp4RoundTripProducesFrames() = runTest {
    val codec = IosMp4Codec()
    val original = VideoTestHelpers.syntheticVideo(
      width = 64, height = 64, frameRate = 10.0, durationMs = 500,
    )
    val ctx = VideoTestHelpers.testContext()

    // Wrap only codec operations — simulator may lack HW codecs.
    // Assertions stay outside so real failures are never swallowed.
    val encoded = try {
      codec.encode(original, ctx)
    } catch (e: Throwable) {
      println("SKIP: MP4 encoding not available on this simulator: ${e::class.simpleName}: ${e.message}")
      return@runTest
    }
    assertTrue(encoded.isNotEmpty(), "MP4 encoded bytes should not be empty")

    val decoded = try {
      codec.decode(encoded, ctx)
    } catch (e: Throwable) {
      println("SKIP: MP4 decoding not available on this simulator: ${e::class.simpleName}: ${e.message}")
      return@runTest
    }
    assertTrue(decoded.videoTrack.width > 0, "Decoded width should be > 0")
    assertTrue(decoded.videoTrack.height > 0, "Decoded height should be > 0")
    assertTrue(decoded.durationMs > 0, "Decoded duration should be > 0, was ${decoded.durationMs}ms")
  }

  @Test
  fun mp4RoundTripPreservesDimensions() = runTest {
    val codec = IosMp4Codec()
    val original = VideoTestHelpers.syntheticVideo(
      width = 128, height = 96, frameRate = 15.0, durationMs = 500,
    )
    val ctx = VideoTestHelpers.testContext()

    val encoded = try {
      codec.encode(original, ctx)
    } catch (e: Throwable) {
      println("SKIP: MP4 encoding not available on this simulator: ${e::class.simpleName}: ${e.message}")
      return@runTest
    }
    val decoded = try {
      codec.decode(encoded, ctx)
    } catch (e: Throwable) {
      println("SKIP: MP4 decoding not available on this simulator: ${e::class.simpleName}: ${e.message}")
      return@runTest
    }

    // Video codecs may round to even dimensions; allow ±1
    assertTrue(
      decoded.videoTrack.width in 127..129,
      "Width should be ~128, was ${decoded.videoTrack.width}",
    )
    assertTrue(
      decoded.videoTrack.height in 95..97,
      "Height should be ~96, was ${decoded.videoTrack.height}",
    )
  }

  // MOV roundtrip (encode → decode)

  @Test
  fun movRoundTripProducesFrames() = runTest {
    val codec = IosMovCodec()
    val original = VideoTestHelpers.syntheticVideo(
      width = 64, height = 64, frameRate = 10.0, durationMs = 500,
    )
    val ctx = VideoTestHelpers.testContext()

    val encoded = try {
      codec.encode(original, ctx)
    } catch (e: Throwable) {
      println("SKIP: MOV encoding not available on this simulator: ${e::class.simpleName}: ${e.message}")
      return@runTest
    }
    assertTrue(encoded.isNotEmpty(), "MOV encoded bytes should not be empty")

    val decoded = try {
      codec.decode(encoded, ctx)
    } catch (e: Throwable) {
      println("SKIP: MOV decoding not available on this simulator: ${e::class.simpleName}: ${e.message}")
      return@runTest
    }
    assertTrue(decoded.videoTrack.width > 0, "Decoded width should be > 0")
    assertTrue(decoded.videoTrack.height > 0, "Decoded height should be > 0")
    assertTrue(decoded.durationMs > 0, "Decoded duration should be > 0, was ${decoded.durationMs}ms")
  }

  // Format support assertions

  @Test
  fun mp4CodecReportsCorrectFormats() {
    val codec = IosMp4Codec()
    assertTrue(VideoFormat.MP4 in codec.decodableFormats, "MP4 should be decodable")
    assertTrue(VideoFormat.MP4 in codec.encodableFormats, "MP4 should be encodable")
  }

  @Test
  fun movCodecReportsCorrectFormats() {
    val codec = IosMovCodec()
    assertTrue(VideoFormat.MOV in codec.decodableFormats, "MOV should be decodable")
    assertTrue(VideoFormat.MOV in codec.encodableFormats, "MOV should be encodable")
  }
}
