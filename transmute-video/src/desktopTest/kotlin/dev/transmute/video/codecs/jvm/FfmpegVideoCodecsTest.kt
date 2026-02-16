package dev.transmute.video.codecs.jvm

import dev.transmute.core.PrintLogger
import dev.transmute.core.VideoFormat
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.PixelFormat
import dev.transmute.video.VideoFormatDetector
import dev.transmute.video.VideoRegistries
import dev.transmute.video.VideoTestHelpers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Integration tests for FFmpeg-backed video codecs on desktop/JVM.
 *
 * All tests are gated on FFmpeg availability — they skip gracefully when
 * FFmpeg is not on PATH (same pattern as [FfmpegAudioCodecsTest] in the
 * audio module).
 */
class FfmpegVideoCodecsTest {

  private val log = PrintLogger
  private val hasFfmpeg: Boolean = FfmpegVideoEngine.available

  /**
   * Guards a test that requires FFmpeg.
   * If FFmpeg is unavailable the test body is skipped with a log warning
   * (mirrors the audio module convention).
   */
  private inline fun requireFfmpeg(block: () -> Unit) {
    if (!hasFfmpeg) {
      log.warn("SKIPPED — FFmpeg not found on PATH")
      return
    }
    VideoRegistries.installDefaultsIfEmpty()
    block()
  }

  // --- MP4 ---

  @Test
  fun mp4RoundTripPreservesDimensions() = runTest {
    requireFfmpeg {
      val original = VideoTestHelpers.syntheticVideo(
        width = 64, height = 48, frameRate = 10.0, durationMs = 300,
      )
      val ctx = VideoTestHelpers.testContext()
      val codec = JvmMp4Codec()

      val encoded = codec.encode(original, ctx)
      assertTrue(encoded.isNotEmpty(), "MP4: encoded output must not be empty")

      val decoded = codec.decode(encoded, ctx)
      assertEquals(64, decoded.videoTrack.width, "MP4: width mismatch after round-trip")
      assertEquals(48, decoded.videoTrack.height, "MP4: height mismatch after round-trip")
      assertTrue(decoded.videoTrack.frames.frameCount > 0, "MP4: must have at least 1 frame")
    }
  }

  @Test
  fun mp4EncodedBytesDetectedAsMP4() = runTest {
    requireFfmpeg {
      val ir = VideoTestHelpers.syntheticVideo(width = 32, height = 32, durationMs = 200)
      val ctx = VideoTestHelpers.testContext()
      val encoded = JvmMp4Codec().encode(ir, ctx)

      assertEquals(
        VideoFormat.MP4,
        VideoFormatDetector.detect(encoded),
        "MP4: encoded bytes should be detected as MP4",
      )
    }
  }

  @Test
  fun mp4RoundTripWithAudioPreservesAudioTrack() = runTest {
    requireFfmpeg {
      val original = VideoTestHelpers.syntheticVideo(
        width = 64, height = 48, durationMs = 500, includeAudio = true,
      )
      val ctx = VideoTestHelpers.testContext()
      val codec = JvmMp4Codec()

      val encoded = codec.encode(original, ctx)
      val decoded = codec.decode(encoded, ctx)

      assertTrue(decoded.audioTrack != null, "MP4: audio track should be preserved")
      assertTrue(
        decoded.audioTrack!!.samples.data.isNotEmpty(),
        "MP4: decoded audio samples should not be empty"
      )
    }
  }

  // --- MOV ---

  @Test
  fun movRoundTripPreservesDimensions() = runTest {
    requireFfmpeg {
      val original = VideoTestHelpers.syntheticVideo(
        width = 80, height = 60, frameRate = 15.0, durationMs = 300,
      )
      val ctx = VideoTestHelpers.testContext()
      val codec = JvmMovCodec()

      val encoded = codec.encode(original, ctx)
      assertTrue(encoded.isNotEmpty(), "MOV: encoded output must not be empty")

      val decoded = codec.decode(encoded, ctx)
      assertEquals(80, decoded.videoTrack.width, "MOV: width mismatch")
      assertEquals(60, decoded.videoTrack.height, "MOV: height mismatch")
    }
  }

  // --- WebM ---

  @Test
  fun webmRoundTripPreservesDimensions() = runTest {
    requireFfmpeg {
      val original = VideoTestHelpers.syntheticVideo(
        width = 64, height = 48, durationMs = 300,
      )
      val ctx = VideoTestHelpers.testContext()
      val codec = JvmWebmCodec()

      val encoded = codec.encode(original, ctx)
      assertTrue(encoded.isNotEmpty(), "WebM: encoded output must not be empty")

      val decoded = codec.decode(encoded, ctx)
      assertEquals(64, decoded.videoTrack.width, "WebM: width mismatch")
      assertEquals(48, decoded.videoTrack.height, "WebM: height mismatch")
    }
  }

  @Test
  fun webmEncodedBytesDetectedAsWebM() = runTest {
    requireFfmpeg {
      val ir = VideoTestHelpers.syntheticVideo(width = 32, height = 32, durationMs = 200)
      val ctx = VideoTestHelpers.testContext()
      val encoded = JvmWebmCodec().encode(ir, ctx)

      assertEquals(
        VideoFormat.WEBM,
        VideoFormatDetector.detect(encoded),
        "WebM: encoded bytes should be detected as WEBM",
      )
    }
  }

  // --- AVI ---

  @Test
  fun aviRoundTripPreservesDimensions() = runTest {
    requireFfmpeg {
      val original = VideoTestHelpers.syntheticVideo(
        width = 64, height = 48, durationMs = 300,
      )
      val ctx = VideoTestHelpers.testContext()
      val codec = JvmAviCodec()

      val encoded = codec.encode(original, ctx)
      assertTrue(encoded.isNotEmpty(), "AVI: encoded output must not be empty")

      val decoded = codec.decode(encoded, ctx)
      assertEquals(64, decoded.videoTrack.width, "AVI: width mismatch")
      assertEquals(48, decoded.videoTrack.height, "AVI: height mismatch")
    }
  }

  @Test
  fun aviEncodedBytesDetectedAsAVI() = runTest {
    requireFfmpeg {
      val ir = VideoTestHelpers.syntheticVideo(width = 32, height = 32, durationMs = 200)
      val ctx = VideoTestHelpers.testContext()
      val encoded = JvmAviCodec().encode(ir, ctx)

      assertEquals(
        VideoFormat.AVI,
        VideoFormatDetector.detect(encoded),
        "AVI: encoded bytes should be detected as AVI",
      )
    }
  }

  // --- MKV ---

  @Test
  fun mkvRoundTripPreservesDimensions() = runTest {
    requireFfmpeg {
      val original = VideoTestHelpers.syntheticVideo(
        width = 64, height = 48, durationMs = 300,
      )
      val ctx = VideoTestHelpers.testContext()
      val codec = JvmMkvCodec()

      val encoded = codec.encode(original, ctx)
      assertTrue(encoded.isNotEmpty(), "MKV: encoded output must not be empty")

      val decoded = codec.decode(encoded, ctx)
      assertEquals(64, decoded.videoTrack.width, "MKV: width mismatch")
      assertEquals(48, decoded.videoTrack.height, "MKV: height mismatch")
    }
  }

  // --- Frame pixel verification ---

  @Test
  fun mp4RoundTripFirstFrameHasReasonablePixels() = runTest {
    requireFfmpeg {
      val original = VideoTestHelpers.syntheticVideo(
        width = 64, height = 64, durationMs = 200,
      )
      val ctx = VideoTestHelpers.testContext()
      val codec = JvmMp4Codec()

      val encoded = codec.encode(original, ctx)
      val decoded = codec.decode(encoded, ctx)

      val firstFrame = decoded.videoTrack.frames.nextFrame()
      assertTrue(firstFrame != null, "MP4: should have at least one frame after round-trip")
      assertEquals(64, firstFrame.width, "MP4: frame width mismatch")
      assertEquals(64, firstFrame.height, "MP4: frame height mismatch")
      assertEquals(PixelFormat.RGBA_8888, firstFrame.pixelFormat, "MP4: pixel format mismatch")

      // Verify pixel data is not all zeros (lossy, so we just check non-trivial)
      val data = (firstFrame.buffer as ByteArrayPixelBuffer).data
      val nonZero = data.count { it != 0.toByte() }
      assertTrue(nonZero > data.size / 10, "MP4: frame data should not be mostly zeros")
    }
  }
}
