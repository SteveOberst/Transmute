package dev.transmute.audio

import dev.transmute.common.PipelineContext
import dev.transmute.common.PrintLogger
import kotlin.math.PI
import kotlin.math.sin

object AudioTestHelpers {

  fun testContext(): PipelineContext = PipelineContext(logger = PrintLogger)

  fun sineWave(
    frequency: Float = 440f,
    durationMs: Long = 1000,
    sampleRate: Int = 44100,
    amplitude: Float = 0.5f,
    channelCount: Int = 1,
  ): AudioIR {
    val totalSamples = ((durationMs * sampleRate * channelCount) / 1000).toInt()
    val data = FloatArray(totalSamples)

    for (index in 0 until totalSamples step channelCount) {
      val time = index.toFloat() / (sampleRate * channelCount)
      val sample = amplitude * sin(2 * PI.toFloat() * frequency * time)
      for (channel in 0 until channelCount) {
        data[index + channel] = sample
      }
    }

    return AudioIR(
      samples = AudioSamples(data, sampleRate, channelCount),
      sampleRate = sampleRate,
      channelCount = channelCount,
      durationMs = durationMs,
    )
  }
}