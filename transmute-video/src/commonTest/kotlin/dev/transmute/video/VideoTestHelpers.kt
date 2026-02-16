package dev.transmute.video

import dev.transmute.audio.AudioSamples
import dev.transmute.core.ConversionContext
import dev.transmute.core.ConversionLogger
import dev.transmute.core.MetadataPolicy
import dev.transmute.core.PrintLogger
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.PixelFormat
import kotlin.random.Random

/**
 * Test utilities for video tests.
 */
object VideoTestHelpers {

  /**
   * Creates a test [ConversionContext].
   */
  fun testContext(
    metadataPolicy: MetadataPolicy = MetadataPolicy.PRESERVE,
  ): ConversionContext = ConversionContext(
    jobId = "video-test-${Random.nextLong()}",
    coroutineJob = null,
    metadataPolicy = metadataPolicy,
    onProgress = {},
    logger = PrintLogger,
    scratchpad = mutableMapOf(),
    timeBudgetMs = Long.MAX_VALUE,
    memoryBudgetBytes = Long.MAX_VALUE,
  )

  /**
   * Creates a test VideoIR with synthetic frames.
   *
   * @param width Frame width in pixels.
   * @param height Frame height in pixels.
   * @param frameRate Frames per second.
   * @param durationMs Duration in milliseconds.
   * @param includeAudio Whether to include an audio track.
   */
  fun syntheticVideo(
    width: Int = 320,
    height: Int = 240,
    frameRate: Double = 30.0,
    durationMs: Long = 1000,
    includeAudio: Boolean = false,
  ): VideoIR {
    val frameCount = ((durationMs * frameRate) / 1000).toLong()

    val videoTrack = VideoTrack(
      width = width,
      height = height,
      frameRate = frameRate,
      frames = SyntheticFrameStream(width, height, frameCount, frameRate),
    )

    val audioTrack = if (includeAudio) {
      val sampleRate = 44100
      val channels = 2
      val samples = (durationMs * sampleRate * channels / 1000).toInt()
      AudioTrack(
        samples = AudioSamples(FloatArray(samples), sampleRate, channels),
        sampleStream = null,
      )
    } else null

    return VideoIR(
      videoTrack = videoTrack,
      audioTrack = audioTrack,
      durationMs = durationMs,
    )
  }
}

/**
 * Generates synthetic video frames for testing.
 */
internal class SyntheticFrameStream(
  private val width: Int,
  private val height: Int,
  override val frameCount: Long,
  private val frameRate: Double,
) : FrameStream {
  private var currentFrame = 0L

  override suspend fun nextFrame(): VideoFrame? {
    if (currentFrame >= frameCount) return null

    val timestampMs = (currentFrame * 1000.0 / frameRate).toLong()
    val pixelData = createGradientFrame(width, height, (currentFrame % 256).toInt())

    currentFrame++

    return VideoFrame(
      buffer = ByteArrayPixelBuffer(pixelData),
      width = width,
      height = height,
      pixelFormat = PixelFormat.RGBA_8888,
      timestampMs = timestampMs,
    )
  }

  private fun createGradientFrame(w: Int, h: Int, offset: Int): ByteArray {
    val data = ByteArray(w * h * 4)
    for (y in 0 until h) {
      for (x in 0 until w) {
        val idx = (y * w + x) * 4
        data[idx] = ((x + offset) % 256).toByte()     // R
        data[idx + 1] = ((y + offset) % 256).toByte() // G
        data[idx + 2] = ((x + y + offset) % 256).toByte() // B
        data[idx + 3] = 0xFF.toByte()                 // A
      }
    }
    return data
  }
}
