package dev.transmute.testing.video

import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.testing.video.VideoAssertions.assertDurationNear
import dev.transmute.testing.video.VideoAssertions.assertFrameCount
import dev.transmute.testing.video.VideoAssertions.assertFrameRate
import dev.transmute.testing.video.VideoAssertions.assertHasAudio
import dev.transmute.testing.video.VideoAssertions.assertNoAudio
import dev.transmute.testing.video.VideoAssertions.assertResolution
import dev.transmute.testing.video.VideoAssertions.collectFrames
import dev.transmute.testing.video.VideoAssertions.frameBrightness
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyntheticVideoTest {

  // ---
  // solidColor
  // ---

  @Test
  fun solidColorDefaults() {
    val v = SyntheticVideo.solidColor(320, 240)
    assertResolution(v, 320, 240)
    assertFrameRate(v, 30.0)
    assertFrameCount(v, 30L)
  }

  @Test
  fun solidColorCustom() {
    val v = SyntheticVideo.solidColor(160, 120, frameCount = 10, frameRate = 25.0, r = 0, g = 255, b = 0)
    assertResolution(v, 160, 120)
    assertFrameCount(v, 10L)
    assertFrameRate(v, 25.0)
  }

  @Test
  fun solidColorWithAudio() {
    val v = SyntheticVideo.solidColor(64, 48, frameCount = 15, withAudio = true)
    assertHasAudio(v)
  }

  @Test
  fun solidColorNoAudioByDefault() {
    val v = SyntheticVideo.solidColor(64, 48, frameCount = 5)
    assertNoAudio(v)
  }

  @Test
  fun solidColorAllFramesSamePixel() = runTest {
    val v = SyntheticVideo.solidColor(32, 32, frameCount = 5, r = 128, g = 64, b = 32)
    val frames = collectFrames(v)
    assertEquals(5, frames.size)
    for (frame in frames) {
      val data = (frame.buffer as ByteArrayPixelBuffer).data
      assertEquals(128, data[0].toInt() and 0xFF, "R")
      assertEquals(64, data[1].toInt() and 0xFF, "G")
      assertEquals(32, data[2].toInt() and 0xFF, "B")
    }
  }

  // ---
  // scrollingGradient
  // ---

  @Test
  fun scrollingGradientBasic() {
    val v = SyntheticVideo.scrollingGradient(64, 48, frameCount = 10)
    assertResolution(v, 64, 48)
    assertFrameCount(v, 10L)
  }

  // ---
  // fadeToBlack / fadeFromBlack
  // ---

  @Test
  fun fadeToBlackEndsBlack() = runTest {
    val v = SyntheticVideo.fadeToBlack(32, 32, frameCount = 30)
    val frames = collectFrames(v)
    assertTrue(frames.isNotEmpty())
    val lastBrightness = frameBrightness(frames.last())
    assertTrue(lastBrightness < 5.0, "Last frame should be near black, got $lastBrightness")
  }

  @Test
  fun fadeToBlackStartsBright() = runTest {
    val v = SyntheticVideo.fadeToBlack(32, 32, frameCount = 30)
    val frames = collectFrames(v)
    val firstBrightness = frameBrightness(frames.first())
    assertTrue(firstBrightness > 200.0, "First frame should be bright, got $firstBrightness")
  }

  @Test
  fun fadeFromBlackStartsBlack() = runTest {
    val v = SyntheticVideo.fadeFromBlack(32, 32, frameCount = 30)
    val frames = collectFrames(v)
    val firstBrightness = frameBrightness(frames.first())
    assertTrue(firstBrightness < 5.0, "First frame should be near black, got $firstBrightness")
  }

  @Test
  fun fadeFromBlackEndsBright() = runTest {
    val v = SyntheticVideo.fadeFromBlack(32, 32, frameCount = 30)
    val frames = collectFrames(v)
    val lastBrightness = frameBrightness(frames.last())
    assertTrue(lastBrightness > 200.0, "Last frame should be bright, got $lastBrightness")
  }

  // ---
  // flashing
  // ---

  @Test
  fun flashingAlternatesColours() = runTest {
    val v = SyntheticVideo.flashing(32, 32, frameCount = 6)
    val frames = collectFrames(v)
    assertEquals(6, frames.size)
    val brightness0 = frameBrightness(frames[0])
    val brightness2 = frameBrightness(frames[2])
    assertEquals(brightness0, brightness2, "Same phase frames should match")
  }

  // ---
  // animatedColorBars
  // ---

  @Test
  fun animatedColorBarsCreatesFrames() {
    val v = SyntheticVideo.animatedColorBars(64, 48, frameCount = 10)
    assertFrameCount(v, 10L)
    assertResolution(v, 64, 48)
  }

  // ---
  // pulsing
  // ---

  @Test
  fun pulsingFramesDiffer() = runTest {
    val v = SyntheticVideo.pulsing(32, 32, frameCount = 10)
    val frames = collectFrames(v)
    assertTrue(frames.size == 10)
    // The spatial wave shifts per frame - pixel data should differ between frames
    val data0 = (frames[0].buffer as ByteArrayPixelBuffer).data
    val data5 = (frames[5].buffer as ByteArrayPixelBuffer).data
    val differs = data0.indices.count { data0[it] != data5[it] }
    assertTrue(differs > 0, "Pulsing frames should have different pixel data")
  }

  // ---
  // animatedCheckerboard
  // ---

  @Test
  fun animatedCheckerboardBasic() {
    val v = SyntheticVideo.animatedCheckerboard(64, 64, frameCount = 10)
    assertResolution(v, 64, 64)
    assertFrameCount(v, 10L)
  }

  // ---
  // hueRotation
  // ---

  @Test
  fun hueRotationFramesVary() = runTest {
    val v = SyntheticVideo.hueRotation(32, 32, frameCount = 12)
    val frames = collectFrames(v)
    assertEquals(12, frames.size)
    // Different frames should have different colours
    val firstData = (frames[0].buffer as ByteArrayPixelBuffer).data
    val midData = (frames[6].buffer as ByteArrayPixelBuffer).data
    val samePixel = firstData[0] == midData[0] && firstData[1] == midData[1] && firstData[2] == midData[2]
    assertTrue(!samePixel, "Hue rotation should produce different colours per frame")
  }

  // ---
  // singleFrame
  // ---

  @Test
  fun singleFrameHasOneFrame() {
    val v = SyntheticVideo.singleFrame(32, 32)
    assertFrameCount(v, 1L)
  }

  // ---
  // Timestamp monotonicity
  // ---

  @Test
  fun timestampsIncreaseMonotonically() = runTest {
    val v = SyntheticVideo.solidColor(32, 32, frameCount = 15, frameRate = 24.0)
    val frames = collectFrames(v)
    for (i in 1 until frames.size) {
      assertTrue(
        frames[i].timestampMs > frames[i - 1].timestampMs,
        "Timestamp at frame $i should exceed frame ${i - 1}",
      )
    }
  }
}
