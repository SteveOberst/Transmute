package dev.transmute.video

import dev.transmute.audio.AudioSamples
import dev.transmute.common.PipelineContext
import dev.transmute.common.PrintLogger
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.PixelFormat

object VideoTestHelpers {

  fun testContext(): PipelineContext = PipelineContext(logger = PrintLogger)

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
    } else {
      null
    }

    return VideoIR(
      videoTrack = videoTrack,
      audioTrack = audioTrack,
      durationMs = durationMs,
    )
  }
}

internal class SyntheticFrameStream(
  private val width: Int,
  private val height: Int,
  override val frameCount: Long,
  private val frameRate: Double,
) : FrameStream {
  private var currentFrame = 0L

  override fun close() {}

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

  private fun createGradientFrame(frameWidth: Int, frameHeight: Int, offset: Int): ByteArray {
    val data = ByteArray(frameWidth * frameHeight * 4)
    for (y in 0 until frameHeight) {
      for (x in 0 until frameWidth) {
        val index = (y * frameWidth + x) * 4
        data[index] = ((x + offset) % 256).toByte()
        data[index + 1] = ((y + offset) % 256).toByte()
        data[index + 2] = ((x + y + offset) % 256).toByte()
        data[index + 3] = 0xFF.toByte()
      }
    }
    return data
  }
}