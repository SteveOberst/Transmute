package dev.transmute.audio.transform

import dev.transmute.audio.AudioIR
import dev.transmute.audio.AudioSamples
import dev.transmute.core.ConversionContext
import dev.transmute.core.pipeline.Transform
import dev.transmute.core.pipeline.TransformId
import kotlin.math.roundToInt

/**
 * Changes playback speed without altering pitch.
 *
 * Uses a simple overlap-add (SOLA) algorithm to time-stretch the audio.
 * This preserves the original pitch by keeping sample rate constant
 * while expanding or compressing the waveform in the time domain.
 *
 * - [speed] > 1.0 → faster (shorter duration)
 * - [speed] < 1.0 → slower (longer duration)
 *
 * SOLA uses fixed-size analysis windows and overlaps them at intervals
 * determined by the speed ratio. This avoids the "chipmunk" effect of
 * naive resampling, though quality degrades at extreme ratios (< 0.5 or > 2.0).
 *
 * @param speed Playback speed multiplier. Must be > 0. Default is 1.0 (no change).
 */
class AudioSpeedTransform(
  val speed: Float = 1f,
) : Transform<AudioIR> {
  override val id = TransformId("audio.speed")

  override suspend fun apply(ir: AudioIR, context: ConversionContext): AudioIR {
    require(speed > 0f) { "Speed must be > 0, got $speed" }
    if (speed == 1f) return ir

    val samples = ir.samples.data
    val channelCount = ir.channelCount
    val sampleRate = ir.sampleRate
    val frameCount = samples.size / channelCount

    // Window and hop sizes in frames.
    val windowFrames = (sampleRate * 0.030).toInt() // 30 ms analysis window
    val analysisHop = (windowFrames * speed).toInt().coerceAtLeast(1)
    val synthesisHop = windowFrames // Output hop equals the window size for natural pacing

    val outputFrames = ((frameCount.toDouble() / speed)).toInt()
    val output = FloatArray(outputFrames * channelCount)
    val windowWeights = FloatArray(outputFrames) // For normalisation after overlap-add

    var readPos = 0
    var writePos = 0

    while (readPos + windowFrames <= frameCount && writePos + windowFrames <= outputFrames) {
      for (i in 0 until windowFrames) {
        // Hann window - smooths the overlap boundaries to prevent clicks.
        val w = 0.5f * (1f - kotlin.math.cos(2.0 * kotlin.math.PI * i / windowFrames).toFloat())
        for (ch in 0 until channelCount) {
          val srcIdx = (readPos + i) * channelCount + ch
          val dstIdx = (writePos + i) * channelCount + ch
          if (srcIdx < samples.size && dstIdx < output.size) {
            output[dstIdx] += samples[srcIdx] * w
          }
        }
        if (writePos + i < windowWeights.size) {
          windowWeights[writePos + i] += w
        }
      }
      readPos += analysisHop
      writePos += synthesisHop
    }

    // Normalize overlapped regions.
    for (frame in 0 until outputFrames) {
      val w = windowWeights[frame]
      if (w > 0.001f) {
        for (ch in 0 until channelCount) {
          val idx = frame * channelCount + ch
          if (idx < output.size) output[idx] /= w
        }
      }
    }

    // Trim to actual written frames.
    val actualFrames = writePos.coerceAtMost(outputFrames)
    val trimmed = if (actualFrames * channelCount < output.size) {
      output.copyOfRange(0, actualFrames * channelCount)
    } else {
      output
    }

    val newDurationMs = (actualFrames.toLong() * 1000L) / sampleRate

    return ir.copy(
      samples = AudioSamples(
        data = trimmed,
        sampleRate = sampleRate,
        channelCount = channelCount,
      ),
      durationMs = newDurationMs,
    )
  }
}
