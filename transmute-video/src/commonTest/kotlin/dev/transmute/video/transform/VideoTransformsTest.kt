package dev.transmute.video.transform

import dev.transmute.video.VideoIR
import dev.transmute.video.VideoTestHelpers.syntheticVideo
import dev.transmute.video.VideoTestHelpers.testContext
import dev.transmute.image.ByteArrayPixelBuffer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoTrimTransformTest {

  private val context = testContext()

  @Test
  fun trimStartOnly() = runTest {
    val video = syntheticVideo(durationMs = 1000)
    val transform = VideoTrimTransform(startMs = 500)

    val result = transform.apply(video, context)

    assertEquals(500, result.durationMs)
  }

  @Test
  fun trimEndOnly() = runTest {
    val video = syntheticVideo(durationMs = 1000)
    val transform = VideoTrimTransform(startMs = 0, endMs = 500)

    val result = transform.apply(video, context)

    assertEquals(500, result.durationMs)
  }

  @Test
  fun trimBothEnds() = runTest {
    val video = syntheticVideo(durationMs = 1000)
    val transform = VideoTrimTransform(startMs = 250, endMs = 750)

    val result = transform.apply(video, context)

    assertEquals(500, result.durationMs)
  }

  @Test
  fun trimUpdatesMetadata() = runTest {
    val video = syntheticVideo(durationMs = 1000)
    val transform = VideoTrimTransform(startMs = 0, endMs = 300)

    val result = transform.apply(video, context)

    assertEquals(300, result.metadata.durationMs)
  }
}

class VideoResizeTransformTest {

  private val context = testContext()

  @Test
  fun scalesDownToFit() = runTest {
    val video = syntheticVideo(width = 1920, height = 1080)
    val transform = VideoResizeTransform(maxWidth = 640, maxHeight = 480)

    val result = transform.apply(video, context)

    assertTrue(result.videoTrack.width <= 640)
    assertTrue(result.videoTrack.height <= 480)
    // Aspect ratio should be preserved (16:9)
    val aspectRatio = result.videoTrack.width.toDouble() / result.videoTrack.height
    assertTrue(aspectRatio in 1.7..1.8)
  }

  @Test
  fun noChangeIfAlreadySmaller() = runTest {
    val video = syntheticVideo(width = 320, height = 240)
    val transform = VideoResizeTransform(maxWidth = 640, maxHeight = 480)

    val result = transform.apply(video, context)

    assertEquals(320, result.videoTrack.width)
    assertEquals(240, result.videoTrack.height)
  }

  @Test
  fun preservesAspectRatio() = runTest {
    val video = syntheticVideo(width = 800, height = 600) // 4:3
    val transform = VideoResizeTransform(maxWidth = 400, maxHeight = 400)

    val result = transform.apply(video, context)

    val aspectRatio = result.videoTrack.width.toDouble() / result.videoTrack.height
    assertTrue(aspectRatio in 1.3..1.4) // ~4:3
  }
}

class VideoFrameRateTransformTest {

  private val context = testContext()

  @Test
  fun changesFrameRate() = runTest {
    val video = syntheticVideo(frameRate = 30.0)
    val transform = VideoFrameRateTransform(targetFps = 24.0)

    val result = transform.apply(video, context)

    assertEquals(24.0, result.videoTrack.frameRate)
  }

  @Test
  fun updatesFrameCount() = runTest {
    val video = syntheticVideo(frameRate = 30.0, durationMs = 1000)
    val transform = VideoFrameRateTransform(targetFps = 15.0)

    val result = transform.apply(video, context)

    // Half the frame rate should mean roughly half the frames
    assertTrue(result.videoTrack.frames.frameCount < video.videoTrack.frames.frameCount)
  }
}

class VideoRemoveAudioTransformTest {

  private val context = testContext()

  @Test
  fun removesAudioTrack() = runTest {
    val video = syntheticVideo(includeAudio = true)
    assertTrue(video.audioTrack != null)

    val transform = VideoRemoveAudioTransform()
    val result = transform.apply(video, context)

    assertNull(result.audioTrack)
  }

  @Test
  fun noOpIfNoAudio() = runTest {
    val video = syntheticVideo(includeAudio = false)
    assertNull(video.audioTrack)

    val transform = VideoRemoveAudioTransform()
    val result = transform.apply(video, context)

    assertNull(result.audioTrack)
  }

  @Test
  fun preservesVideoTrack() = runTest {
    val video = syntheticVideo(width = 640, height = 480, includeAudio = true)
    val transform = VideoRemoveAudioTransform()

    val result = transform.apply(video, context)

    assertEquals(640, result.videoTrack.width)
    assertEquals(480, result.videoTrack.height)
  }
}

// -- Crop --

class VideoCropTransformTest {

  private val context = testContext()

  @Test
  fun cropChangesDimensions() = runTest {
    val video = syntheticVideo(width = 320, height = 240, durationMs = 100)

    val result = VideoCropTransform(x = 10, y = 20, cropWidth = 100, cropHeight = 80)
      .apply(video, context)

    assertEquals(100, result.videoTrack.width)
    assertEquals(80, result.videoTrack.height)
  }

  @Test
  fun cropClampsToFrame() = runTest {
    val video = syntheticVideo(width = 100, height = 100, durationMs = 100)

    // Request crop that exceeds the frame.
    val result = VideoCropTransform(x = 80, y = 80, cropWidth = 200, cropHeight = 200)
      .apply(video, context)

    // Should be clamped to 20x20 (100-80=20).
    assertEquals(20, result.videoTrack.width)
    assertEquals(20, result.videoTrack.height)
  }

  @Test
  fun fullFrameCropIsNoOp() = runTest {
    val video = syntheticVideo(width = 320, height = 240, durationMs = 100)

    val result = VideoCropTransform(x = 0, y = 0, cropWidth = 320, cropHeight = 240)
      .apply(video, context)

    // Same dimensions - transform should skip.
    assertEquals(320, result.videoTrack.width)
    assertEquals(240, result.videoTrack.height)
  }

  @Test
  fun croppedFrameStreamProducesCorrectPixels() = runTest {
    val video = syntheticVideo(width = 320, height = 240, durationMs = 100, frameRate = 10.0)

    val result = VideoCropTransform(x = 10, y = 20, cropWidth = 50, cropHeight = 30)
      .apply(video, context)

    val frame = result.videoTrack.frames.nextFrame()!!
    assertEquals(50, frame.width)
    assertEquals(30, frame.height)
    val data = (frame.buffer as ByteArrayPixelBuffer).data
    assertEquals(50 * 30 * 4, data.size) // RGBA
  }

  @Test
  fun preservesDuration() = runTest {
    val video = syntheticVideo(width = 320, height = 240, durationMs = 500)

    val result = VideoCropTransform(x = 0, y = 0, cropWidth = 100, cropHeight = 100)
      .apply(video, context)

    assertEquals(500L, result.durationMs)
  }
}

// -- Speed --

class VideoSpeedTransformTest {

  private val context = testContext()

  @Test
  fun speed2xHalvesDuration() = runTest {
    val video = syntheticVideo(durationMs = 1000, includeAudio = true)

    val result = VideoSpeedTransform(speed = 2f).apply(video, context)

    assertEquals(500L, result.durationMs)
  }

  @Test
  fun speed0_5xDoublesDuration() = runTest {
    val video = syntheticVideo(durationMs = 1000, includeAudio = false)

    val result = VideoSpeedTransform(speed = 0.5f).apply(video, context)

    assertEquals(2000L, result.durationMs)
  }

  @Test
  fun speed1xIsIdentity() = runTest {
    val video = syntheticVideo(durationMs = 500, includeAudio = true)

    val result = VideoSpeedTransform(speed = 1f).apply(video, context)

    assertEquals(500L, result.durationMs)
    // Same instance returned.
    assertTrue(video === result)
  }

  @Test
  fun adjustsAudioLength() = runTest {
    val video = syntheticVideo(durationMs = 1000, includeAudio = true)
    val inputAudioLen = video.audioTrack!!.samples.data.size

    val result = VideoSpeedTransform(speed = 2f).apply(video, context)

    val outputAudioLen = result.audioTrack!!.samples.data.size
    assertTrue(
      outputAudioLen < inputAudioLen,
      "Audio should be shortened: input=$inputAudioLen, output=$outputAudioLen"
    )
  }

  @Test
  fun preservesDimensions() = runTest {
    val video = syntheticVideo(width = 640, height = 480, durationMs = 500)

    val result = VideoSpeedTransform(speed = 1.5f).apply(video, context)

    assertEquals(640, result.videoTrack.width)
    assertEquals(480, result.videoTrack.height)
  }
}

// -- Rotate --

class VideoRotateTransformTest {

  private val context = testContext()

  @Test
  fun rotate90SwapsDimensions() = runTest {
    val video = syntheticVideo(width = 320, height = 240, durationMs = 100)

    val result = VideoRotateTransform(degrees = 90).apply(video, context)

    assertEquals(240, result.videoTrack.width)
    assertEquals(320, result.videoTrack.height)
  }

  @Test
  fun rotate180PreservesDimensions() = runTest {
    val video = syntheticVideo(width = 320, height = 240, durationMs = 100)

    val result = VideoRotateTransform(degrees = 180).apply(video, context)

    assertEquals(320, result.videoTrack.width)
    assertEquals(240, result.videoTrack.height)
  }

  @Test
  fun rotate270SwapsDimensions() = runTest {
    val video = syntheticVideo(width = 320, height = 240, durationMs = 100)

    val result = VideoRotateTransform(degrees = 270).apply(video, context)

    assertEquals(240, result.videoTrack.width)
    assertEquals(320, result.videoTrack.height)
  }

  @Test
  fun rotatedFrameHasCorrectSize() = runTest {
    val video = syntheticVideo(width = 320, height = 240, durationMs = 100, frameRate = 10.0)

    val result = VideoRotateTransform(degrees = 90).apply(video, context)
    val frame = result.videoTrack.frames.nextFrame()!!

    assertEquals(240, frame.width)
    assertEquals(320, frame.height)
    val data = (frame.buffer as ByteArrayPixelBuffer).data
    assertEquals(240 * 320 * 4, data.size)
  }

  @Test
  fun rotate180PixelsAreReversed() = runTest {
    val video = syntheticVideo(width = 4, height = 2, durationMs = 100, frameRate = 10.0)

    val original = video.videoTrack.frames.nextFrame()!!
    val originalData = (original.buffer as ByteArrayPixelBuffer).data

    // Reset frame stream by creating a new video.
    val video2 = syntheticVideo(width = 4, height = 2, durationMs = 100, frameRate = 10.0)
    val result = VideoRotateTransform(degrees = 180).apply(video2, context)
    val rotated = result.videoTrack.frames.nextFrame()!!
    val rotatedData = (rotated.buffer as ByteArrayPixelBuffer).data

    // Bottom-right of original should be top-left of rotated.
    val lastPixelOrig = originalData.copyOfRange(originalData.size - 4, originalData.size)
    val firstPixelRot = rotatedData.copyOfRange(0, 4)
    assertTrue(lastPixelOrig.contentEquals(firstPixelRot), "Rotation should reverse pixel order")
  }

  @Test
  fun preservesDuration() = runTest {
    val video = syntheticVideo(durationMs = 750)

    val result = VideoRotateTransform(degrees = 90).apply(video, context)

    assertEquals(750L, result.durationMs)
  }
}
