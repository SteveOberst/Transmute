package dev.transmute.audio

import dev.transmute.common.PipelineContext
import dev.transmute.common.PrintLogger
import kotlin.math.PI
import kotlin.math.sin

/**
 * Test utilities for audio tests.
 */
object AudioTestHelpers {

  /**
   * Creates a test [PipelineContext].
   */
  fun testContext(): PipelineContext = PipelineContext(logger = PrintLogger)

  /**
   * Generates a sine wave AudioIR for testing.
   *
   * @param frequency Frequency of the sine wave in Hz.
   * @param durationMs Duration in milliseconds.
   * @param sampleRate Sample rate in Hz.
   * @param amplitude Amplitude (0.0 to 1.0).
   */
  fun sineWave(
    frequency: Float = 440f,
    durationMs: Long = 1000,
    sampleRate: Int = 44100,
    amplitude: Float = 0.5f,
    channelCount: Int = 1,
  ): AudioIR {
    val totalSamples = ((durationMs * sampleRate * channelCount) / 1000).toInt()
    val data = FloatArray(totalSamples)

    for (i in 0 until totalSamples step channelCount) {
      val t = i.toFloat() / (sampleRate * channelCount)
      val sample = amplitude * sin(2 * PI.toFloat() * frequency * t)
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

  /**
   * Generates silence AudioIR for testing.
   */
  fun silence(
    durationMs: Long = 1000,
    sampleRate: Int = 44100,
    channelCount: Int = 1,
  ): AudioIR {
    val totalSamples = ((durationMs * sampleRate * channelCount) / 1000).toInt()
    return AudioIR(
      samples = AudioSamples(FloatArray(totalSamples), sampleRate, channelCount),
      sampleRate = sampleRate,
      channelCount = channelCount,
      durationMs = durationMs,
    )
  }

  /**
   * Calculates the peak amplitude of an AudioIR.
   */
  fun peakAmplitude(ir: AudioIR): Float {
    return ir.samples.data.maxOfOrNull { kotlin.math.abs(it) } ?: 0f
  }

  /**
   * Calculates RMS (root mean square) of an AudioIR.
   */
  fun rms(ir: AudioIR): Float {
    val data = ir.samples.data
    if (data.isEmpty()) return 0f
    val sumSquares = data.sumOf { (it * it).toDouble() }
    return kotlin.math.sqrt(sumSquares / data.size).toFloat()
  }
}
