package dev.transmute.gstreamer

import dev.transmute.audio.AudioIR
import dev.transmute.audio.AudioSamples
import dev.transmute.common.PipelineContext
import dev.transmute.common.PrintLogger
import dev.transmute.image.AlphaSemantics
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.ColorInfo
import dev.transmute.image.ImageIR
import dev.transmute.image.PixelFormat
import dev.transmute.video.AudioTrack
import dev.transmute.video.FrameStream
import dev.transmute.video.VideoFrame
import dev.transmute.video.VideoIR
import dev.transmute.video.VideoTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * Test utilities for GStreamer integration tests.
 *
 * Provides synthetic media generators (audio, image, video) used by
 * the integration test suite. Self-contained -- does not depend on
 * test helpers from other modules (which aren't on the test classpath).
 *
 * GStreamer availability is checked at test runtime via [GStreamerTestBase]:
 * each test class that requires GStreamer extends that base, which calls
 * [org.junit.jupiter.api.Assumptions.assumeTrue] before every test so that
 * tests are skipped (not failed) when GStreamer is not installed.
 */
object GStreamerTestHelpers {

  /**
   * Creates a test [PipelineContext] with print logging.
   */
  fun testContext(): PipelineContext = PipelineContext(logger = PrintLogger)

  // -- Synthetic audio ---

  /**
   * Generate a 440 Hz sine-wave [AudioIR].
   */
  fun sineWave(durationMs: Long = 500, sampleRate: Int = 44100, channelCount: Int = 1): AudioIR {
    val totalSamples = ((durationMs * sampleRate * channelCount) / 1000).toInt()
    val data = FloatArray(totalSamples)
    val amplitude = 0.5f
    for (i in 0 until totalSamples step channelCount) {
      val t = i.toFloat() / (sampleRate * channelCount)
      val sample = amplitude * sin(2 * PI.toFloat() * 440f * t)
      for (ch in 0 until channelCount) {
        data[i + ch] = sample
      }
    }
    return AudioIR(
      samples = AudioSamples(data, sampleRate, channelCount),
      sampleRate = sampleRate,
      channelCount = channelCount,
      durationMs = durationMs,
    )
  }

  // -- Synthetic image ---

  /**
   * Create a solid-colour RGBA_8888 [ImageIR].
   */
  fun solidColor(width: Int, height: Int, r: Int, g: Int, b: Int, a: Int = 255): ImageIR {
    val bpp = 4
    val stride = width * bpp
    val pixels = ByteArray(height * stride)
    for (y in 0 until height) {
      for (x in 0 until width) {
        val off = y * stride + x * bpp
        pixels[off] = r.toByte()
        pixels[off + 1] = g.toByte()
        pixels[off + 2] = b.toByte()
        pixels[off + 3] = a.toByte()
      }
    }
    return ImageIR(
      buffer = ByteArrayPixelBuffer(pixels),
      width = width,
      height = height,
      stride = stride,
      pixelFormat = PixelFormat.RGBA_8888,
      alphaSemantics = if (a < 255) AlphaSemantics.STRAIGHT else AlphaSemantics.OPAQUE,
      colorInfo = ColorInfo(),
    )
  }

  // -- Synthetic video ---

  /**
   * Create a synthetic [VideoIR] with flat-colour frames.
   */
  fun syntheticVideo(
    width: Int = 160,
    height: Int = 120,
    frameRate: Double = 10.0,
    durationMs: Long = 500,
    includeAudio: Boolean = false,
  ): VideoIR {
    val frameCount = ((durationMs * frameRate) / 1000).toLong()
    val videoTrack = VideoTrack(
      width = width,
      height = height,
      frameRate = frameRate,
      frames = SyntheticFrameStreamHelper(width, height, frameCount, frameRate),
    )
    val audioTrack = if (includeAudio) {
      val sr = 44100
      val ch = 2
      val samples = (durationMs * sr * ch / 1000).toInt()
      AudioTrack(
        samples = AudioSamples(FloatArray(samples), sr, ch),
        sampleStream = null,
      )
    } else {
      null
    }
    return VideoIR(
      videoTrack = videoTrack,
      audioTrack = audioTrack,
      durationMs = durationMs,
    )
  }

  /**
   * Trivial [FrameStream] that yields flat-colour RGBA frames.
   */
  private class SyntheticFrameStreamHelper(
    private val w: Int,
    private val h: Int,
    override val frameCount: Long,
    private val fps: Double,
  ) : FrameStream {
    private var index = 0

    override suspend fun nextFrame(): VideoFrame? {
      if (index >= frameCount) return null
      val bpp = 4
      val pixels = ByteArray(h * w * bpp) // black frames
      val frame = VideoFrame(
        buffer = ByteArrayPixelBuffer(pixels),
        width = w,
        height = h,
        pixelFormat = PixelFormat.RGBA_8888,
        timestampMs = (index * 1000L / fps).toLong(),
      )
      index++
      return frame
    }

    override fun close() {}
  }
}
