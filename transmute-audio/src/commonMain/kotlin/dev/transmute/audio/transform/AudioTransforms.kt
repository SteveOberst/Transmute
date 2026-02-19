package dev.transmute.audio.transform

import dev.transmute.audio.AudioIR
import dev.transmute.audio.AudioSamples
import dev.transmute.core.ConversionContext
import dev.transmute.core.pipeline.Transform
import dev.transmute.core.pipeline.TransformId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.PI

/**
 * Normalizes audio to a target peak level.
 *
 * @param targetPeak Peak amplitude to normalize to (0.0 to 1.0). Default is 0.95 to avoid clipping.
 */
class AudioNormalizeTransform(
  val targetPeak: Float = 0.95f,
) : Transform<AudioIR> {
  override val id = TransformId("audio.normalize")

  override suspend fun apply(ir: AudioIR, context: ConversionContext): AudioIR {

    val samples = ir.samples.data
    val currentPeak = samples.maxOfOrNull { abs(it) } ?: 0f

    if (currentPeak == 0f || currentPeak >= targetPeak) {
      return ir
    }

    val gain = targetPeak / currentPeak
    val normalized = FloatArray(samples.size) { (samples[it] * gain).coerceIn(-1f, 1f) }

    return ir.copy(
      samples = AudioSamples(
        data = normalized,
        sampleRate = ir.sampleRate,
        channelCount = ir.channelCount,
      )
    )
  }
}

/**
 * Resamples audio to a different sample rate using linear interpolation.
 *
 * @param targetSampleRate The target sample rate in Hz.
 */
class AudioResampleTransform(
  val targetSampleRate: Int,
) : Transform<AudioIR> {
  override val id = TransformId("audio.resample")

  override suspend fun apply(ir: AudioIR, context: ConversionContext): AudioIR {

    if (ir.sampleRate == targetSampleRate) {
      return ir
    }

    val samples = ir.samples.data
    val channelCount = ir.channelCount
    val ratio = targetSampleRate.toDouble() / ir.sampleRate

    val inputFrames = samples.size / channelCount
    val outputFrames = (inputFrames * ratio).roundToInt()
    val outputSamples = FloatArray(outputFrames * channelCount)

    for (outFrame in 0 until outputFrames) {
      val inPos = outFrame / ratio
      val inFrame = inPos.toInt()
      val frac = (inPos - inFrame).toFloat()

      for (ch in 0 until channelCount) {
        val idx0 = inFrame * channelCount + ch
        val idx1 = minOf(idx0 + channelCount, samples.size - 1)

        val s0 = if (idx0 < samples.size) samples[idx0] else 0f
        val s1 = if (idx1 < samples.size) samples[idx1] else s0

        outputSamples[outFrame * channelCount + ch] = s0 + (s1 - s0) * frac
      }
    }

    val newDurationMs = (outputFrames.toLong() * 1000L) / targetSampleRate

    return ir.copy(
      samples = AudioSamples(
        data = outputSamples,
        sampleRate = targetSampleRate,
        channelCount = channelCount,
      ),
      sampleRate = targetSampleRate,
      durationMs = newDurationMs,
    )
  }
}

/**
 * Applies fade in/out effects to audio.
 *
 * @param fadeInMs Duration of fade in from silence (0 to disable).
 * @param fadeOutMs Duration of fade out to silence (0 to disable).
 */
class AudioFadeTransform(
  val fadeInMs: Long = 0,
  val fadeOutMs: Long = 0,
) : Transform<AudioIR> {
  override val id = TransformId("audio.fade")

  override suspend fun apply(ir: AudioIR, context: ConversionContext): AudioIR {

    if (fadeInMs <= 0 && fadeOutMs <= 0) {
      return ir
    }

    val samples = ir.samples.data.copyOf()
    val channelCount = ir.channelCount
    val sampleRate = ir.sampleRate

    val fadeInSamples = ((fadeInMs * sampleRate) / 1000).toInt() * channelCount
    val fadeOutSamples = ((fadeOutMs * sampleRate) / 1000).toInt() * channelCount

    // Apply fade in
    if (fadeInMs > 0 && fadeInSamples > 0) {
      for (i in 0 until minOf(fadeInSamples, samples.size)) {
        val gain = i.toFloat() / fadeInSamples
        samples[i] *= gain
      }
    }

    // Apply fade out
    if (fadeOutMs > 0 && fadeOutSamples > 0) {
      val startIdx = max(0, samples.size - fadeOutSamples)
      for (i in startIdx until samples.size) {
        val gain = (samples.size - i - 1).toFloat() / fadeOutSamples
        samples[i] *= gain
      }
    }

    return ir.copy(
      samples = AudioSamples(
        data = samples,
        sampleRate = sampleRate,
        channelCount = channelCount,
      )
    )
  }
}

/**
 * Trims audio to a specified time range.
 *
 * @param startMs Start time in milliseconds.
 * @param endMs End time in milliseconds (null = end of audio).
 */
class AudioTrimTransform(
  val startMs: Long,
  val endMs: Long? = null,
) : Transform<AudioIR> {
  override val id = TransformId("audio.trim")

  override suspend fun apply(ir: AudioIR, context: ConversionContext): AudioIR {

    val samples = ir.samples.data
    val channelCount = ir.channelCount
    val sampleRate = ir.sampleRate

    val startSample = ((startMs * sampleRate * channelCount) / 1000).toInt().coerceIn(0, samples.size)
    val endSample = if (endMs != null) {
      ((endMs * sampleRate * channelCount) / 1000).toInt().coerceIn(startSample, samples.size)
    } else {
      samples.size
    }

    // Align to channel boundaries
    val alignedStart = (startSample / channelCount) * channelCount
    val alignedEnd = (endSample / channelCount) * channelCount

    val trimmed = samples.copyOfRange(alignedStart, alignedEnd)
    val newDurationMs = (trimmed.size.toLong() * 1000L) / (sampleRate * channelCount)

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

/**
 * Adjusts audio volume by applying gain.
 *
 * @param gainDb Gain in decibels (positive = louder, negative = quieter).
 */
class AudioGainTransform(
  val gainDb: Float,
) : Transform<AudioIR> {
  override val id = TransformId("audio.gain")

  override suspend fun apply(ir: AudioIR, context: ConversionContext): AudioIR {

    if (gainDb == 0f) {
      return ir
    }

    val linearGain = 10.0.pow(gainDb / 20.0).toFloat()
    val samples = ir.samples.data
    val adjusted = FloatArray(samples.size) { (samples[it] * linearGain).coerceIn(-1f, 1f) }

    return ir.copy(
      samples = AudioSamples(
        data = adjusted,
        sampleRate = ir.sampleRate,
        channelCount = ir.channelCount,
      )
    )
  }
}

/**
 * Converts stereo audio to mono by averaging channels.
 */
class AudioMonoTransform : Transform<AudioIR> {
  override val id = TransformId("audio.mono")

  override suspend fun apply(ir: AudioIR, context: ConversionContext): AudioIR {

    if (ir.channelCount == 1) {
      return ir
    }

    val samples = ir.samples.data
    val channelCount = ir.channelCount
    val frameCount = samples.size / channelCount
    val mono = FloatArray(frameCount)

    for (frame in 0 until frameCount) {
      var sum = 0f
      for (ch in 0 until channelCount) {
        sum += samples[frame * channelCount + ch]
      }
      mono[frame] = sum / channelCount
    }

    return ir.copy(
      samples = AudioSamples(
        data = mono,
        sampleRate = ir.sampleRate,
        channelCount = 1,
      ),
      channelCount = 1,
    )
  }
}

/**
 * Reverses audio playback.
 */
class AudioReverseTransform : Transform<AudioIR> {
  override val id = TransformId("audio.reverse")

  override suspend fun apply(ir: AudioIR, context: ConversionContext): AudioIR {

    val samples = ir.samples.data
    val channelCount = ir.channelCount
    val frameCount = samples.size / channelCount
    val reversed = FloatArray(samples.size)

    for (frame in 0 until frameCount) {
      val srcFrame = frameCount - frame - 1
      for (ch in 0 until channelCount) {
        reversed[frame * channelCount + ch] = samples[srcFrame * channelCount + ch]
      }
    }

    return ir.copy(
      samples = AudioSamples(
        data = reversed,
        sampleRate = ir.sampleRate,
        channelCount = channelCount,
      )
    )
  }
}
