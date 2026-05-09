@file:Suppress("MagicNumber", "TooManyFunctions")

package dev.transmute.testing.video

import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.video.FrameStream
import dev.transmute.video.VideoFrame
import dev.transmute.video.VideoIR
import kotlin.math.abs

/**
 * Measurement and assertion utilities for [VideoIR] instances.
 *
 * Provides frame-access helpers, simple measurements, and ready-to-use
 * assertion functions that throw [AssertionError] with descriptive messages.
 *
 * ### Quick start
 * ```kotlin
 * val original = SyntheticVideo.solidColor(320, 240, frameCount = 30)
 * val decoded  = roundTrip(original) // encode -> decode
 *
 * VideoAssertions.assertResolution(decoded, 320, 240)
 * VideoAssertions.assertFrameCount(decoded, 30)
 * VideoAssertions.assertDurationNear(decoded, 1000L, toleranceMs = 100)
 * ```
 */
object VideoAssertions {

  // ---
  // Frame extraction
  // ---

  /**
   * Collect all frames from the [VideoIR]'s frame stream into a list.
   *
   * **Consumes** the frame stream - call only once per VideoIR or on a
   * freshly-constructed synthetic video whose [FrameStream] is rewindable.
   */
  suspend fun collectFrames(video: VideoIR): List<VideoFrame> {
    val result = mutableListOf<VideoFrame>()
    val stream = video.videoTrack.frames
    while (true) {
      val frame = stream.nextFrame() ?: break
      result.add(frame)
    }
    return result
  }

  // ---
  // Measurements
  // ---

  /**
   * Compute the average brightness (luma) of a single frame using Rec.601:
   * Y = 0.299.R + 0.587.G + 0.114.B
   *
   * Only supports [ByteArrayPixelBuffer] with 8-bit channels.
   */
  fun frameBrightness(frame: VideoFrame): Double {
    val data = requireFrameBytes(frame)
    val bpp = frame.pixelFormat.bytesPerPixel
    var sum = 0.0
    var count = 0L
    for (y in 0 until frame.height) {
      val rowStart = y * frame.width * bpp
      for (x in 0 until frame.width) {
        val off = rowStart + x * bpp
        val r = data[off].toInt() and 0xFF
        val g = data[off + 1].toInt() and 0xFF
        val b = data[off + 2].toInt() and 0xFF
        sum += 0.299 * r + 0.587 * g + 0.114 * b
        count++
      }
    }
    return if (count == 0L) 0.0 else sum / count
  }

  /**
   * Peak absolute pixel difference between two frames (across all channels).
   */
  fun framePeakDifference(a: VideoFrame, b: VideoFrame): Int {
    require(a.width == b.width && a.height == b.height) {
      "Frame dimensions mismatch: ${a.width}×${a.height} vs ${b.width}×${b.height}"
    }
    val da = requireFrameBytes(a)
    val db = requireFrameBytes(b)
    val bpp = a.pixelFormat.bytesPerPixel
    var peak = 0
    for (y in 0 until a.height) {
      for (x in 0 until a.width) {
        val offA = y * a.width * bpp + x * bpp
        val offB = y * b.width * bpp + x * bpp
        for (ch in 0 until bpp) {
          val diff = abs((da[offA + ch].toInt() and 0xFF) - (db[offB + ch].toInt() and 0xFF))
          if (diff > peak) peak = diff
        }
      }
    }
    return peak
  }

  // ---
  // Assertions
  // ---

  /**
   * Assert that the video has the expected resolution (width x height).
   */
  fun assertResolution(video: VideoIR, width: Int, height: Int) {
    val vt = video.videoTrack
    check(vt.width == width && vt.height == height) {
      "Expected resolution ${width}×${height}, got ${vt.width}×${vt.height}"
    }
  }

  /**
   * Assert that the declared frame rate matches.
   *
   * @param tolerance Acceptable delta (default 0.1 fps).
   */
  fun assertFrameRate(video: VideoIR, expectedFps: Double, tolerance: Double = 0.1) {
    val actual = video.videoTrack.frameRate
    check(abs(actual - expectedFps) <= tolerance) {
      "Expected frame rate $expectedFps±$tolerance fps, got $actual fps"
    }
  }

  /**
   * Assert that the declared frame count matches.
   */
  fun assertFrameCount(video: VideoIR, expected: Long) {
    val actual = video.videoTrack.frames.frameCount
    check(actual == expected) {
      "Expected $expected frames, got $actual"
    }
  }

  /**
   * Assert that the video duration is within [toleranceMs] of [expectedMs].
   */
  fun assertDurationNear(video: VideoIR, expectedMs: Long, toleranceMs: Long = 100) {
    check(abs(video.durationMs - expectedMs) <= toleranceMs) {
      "Duration ${video.durationMs} ms not within ±${toleranceMs} ms of $expectedMs ms"
    }
  }

  /**
   * Assert that the video has a non-null audio track.
   */
  fun assertHasAudio(video: VideoIR) {
    check(video.audioTrack != null) { "Expected audio track, but none found" }
  }

  /**
   * Assert that the video has **no** audio track.
   */
  fun assertNoAudio(video: VideoIR) {
    check(video.audioTrack == null) { "Expected no audio track, but one is present" }
  }

  /**
   * Assert that the audio track has the expected sample rate.
   */
  fun assertAudioSampleRate(video: VideoIR, expectedHz: Int) {
    val at = video.audioTrack
    check(at != null) { "No audio track" }
    check(at.samples.sampleRate == expectedHz) {
      "Audio sample rate: expected $expectedHz Hz, got ${at.samples.sampleRate} Hz"
    }
  }

  /**
   * Assert that the audio track has the expected number of channels.
   */
  fun assertAudioChannelCount(video: VideoIR, expected: Int) {
    val at = video.audioTrack
    check(at != null) { "No audio track" }
    check(at.samples.channelCount == expected) {
      "Audio channels: expected $expected, got ${at.samples.channelCount}"
    }
  }

  /**
   * Assert that two frames are within [maxPeakDiff] of each other on all channels.
   */
  fun assertFramesSimilar(a: VideoFrame, b: VideoFrame, maxPeakDiff: Int) {
    val peak = framePeakDifference(a, b)
    check(peak <= maxPeakDiff) {
      "Frame peak difference $peak exceeds tolerance $maxPeakDiff"
    }
  }

  /**
   * Assert that every collected frame in the video has the same resolution as
   * the video track header.
   */
  suspend fun assertFrameDimensionsConsistent(video: VideoIR) {
    val expected = video.videoTrack
    val frames = collectFrames(video)
    for ((i, frame) in frames.withIndex()) {
      check(frame.width == expected.width && frame.height == expected.height) {
        "Frame $i: expected ${expected.width}×${expected.height}, got ${frame.width}×${frame.height}"
      }
    }
  }

  /**
   * Assert that frame timestamps are monotonically increasing.
   */
  suspend fun assertTimestampsMonotonic(video: VideoIR) {
    val frames = collectFrames(video)
    for (i in 1 until frames.size) {
      check(frames[i].timestampMs >= frames[i - 1].timestampMs) {
        "Timestamp not monotonic at frame $i: ${frames[i - 1].timestampMs} → ${frames[i].timestampMs}"
      }
    }
  }

  /**
   * Compound assertion for typical round-trip video codec tests.
   *
   * Checks: resolution preserved, frame count matches, duration within tolerance.
   */
  fun assertRoundTripPlausible(
    original: VideoIR,
    decoded: VideoIR,
    durationToleranceMs: Long = 200,
  ) {
    assertResolution(decoded, original.videoTrack.width, original.videoTrack.height)
    assertFrameCount(decoded, original.videoTrack.frames.frameCount)
    assertDurationNear(decoded, original.durationMs, durationToleranceMs)
    // Preserve audio presence
    if (original.audioTrack != null) {
      assertHasAudio(decoded)
    }
  }

  // ---
  // Internal helpers
  // ---

  private fun requireFrameBytes(frame: VideoFrame): ByteArray {
    val buffer = frame.buffer
    require(buffer is ByteArrayPixelBuffer) {
      "VideoAssertions requires ByteArrayPixelBuffer, got ${buffer::class.simpleName}"
    }
    return buffer.data
  }
}
