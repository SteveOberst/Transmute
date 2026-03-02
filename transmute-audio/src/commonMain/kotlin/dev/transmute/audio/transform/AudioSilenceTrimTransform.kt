package dev.transmute.audio.transform

import dev.transmute.audio.AudioHint
import dev.transmute.audio.AudioIR
import dev.transmute.audio.AudioSamples
import dev.transmute.audio.AudioTransform
import dev.transmute.common.PipelineContext
import dev.transmute.codec.pipeline.TransformId
import kotlin.math.abs
import kotlin.math.pow

/**
 * Trims silence from the beginning and/or end of an audio track.
 *
 * Silence is defined as consecutive frames where all channels are
 * below [thresholdDb]. The threshold is in dBFS (decibels relative
 * to full scale), so 40 dB means roughly 1% of max amplitude.
 *
 * A minimum hold period ([minSilenceMs]) prevents the trimmer from
 * cutting brief pauses in speech or between musical phrases.
 *
 * @param thresholdDb Silence floor in dBFS. Default 40 dB.
 * @param minSilenceMs Minimum consecutive silence before trimming. Default 100 ms.
 * @param trimStart Whether to trim leading silence.
 * @param trimEnd Whether to trim trailing silence.
 */
class AudioSilenceTrimTransform(
  val thresholdDb: Float = -40f,
  val minSilenceMs: Long = 100,
  val trimStart: Boolean = true,
  val trimEnd: Boolean = true,
) : AudioTransform {

  override fun wouldTransform(hint: AudioHint): Boolean = trimStart || trimEnd

  override val id = TransformId("audio.silence-trim")

  override suspend fun apply(ir: AudioIR, context: PipelineContext): AudioIR {
    if (!trimStart && !trimEnd) return ir

    val samples = ir.samples.data
    val channelCount = ir.channelCount
    val sampleRate = ir.sampleRate
    val frameCount = samples.size / channelCount

    // Convert dBFS threshold to linear amplitude.
    val linearThreshold = 10.0.pow(thresholdDb / 20.0).toFloat()
    val minSilenceFrames = ((minSilenceMs * sampleRate) / 1000).toInt()

    var startFrame = 0
    var endFrame = frameCount

    if (trimStart) {
      startFrame = findFirstLoudFrame(samples, channelCount, frameCount, linearThreshold, minSilenceFrames)
    }

    if (trimEnd) {
      endFrame = findLastLoudFrame(samples, channelCount, frameCount, linearThreshold, minSilenceFrames) + 1
    }

    if (startFrame >= endFrame) {
      context.logger.warn("AudioSilenceTrimTransform: entire track is below threshold - returning empty")
      return ir.copy(
        samples = AudioSamples(FloatArray(0), sampleRate, channelCount),
        durationMs = 0,
      )
    }

    if (startFrame == 0 && endFrame == frameCount) {
      context.logger.debug("AudioSilenceTrimTransform: no silence to trim")
      return ir
    }

    val startSample = startFrame * channelCount
    val endSample = endFrame * channelCount
    val trimmed = samples.copyOfRange(startSample, endSample)
    val newFrames = endFrame - startFrame
    val newDurationMs = (newFrames.toLong() * 1000L) / sampleRate

    context.logger.info(
      "AudioSilenceTrimTransform: trimmed ${startFrame} leading + ${frameCount - endFrame} trailing frames"
    )

    return ir.copy(
      samples = AudioSamples(trimmed, sampleRate, channelCount),
      durationMs = newDurationMs,
    )
  }

  companion object {

    /** Scans forward to find the first frame where amplitude exceeds threshold. */
    private fun findFirstLoudFrame(
      samples: FloatArray, channels: Int, frames: Int,
      threshold: Float, minSilence: Int,
    ): Int {
      var consecutiveLoud = 0
      for (frame in 0 until frames) {
        val loud = isFrameLoud(samples, frame, channels, threshold)
        if (loud) {
          consecutiveLoud++
          if (consecutiveLoud >= 1) {
            // Back up to include the first loud frame.
            return (frame - consecutiveLoud + 1).coerceAtLeast(0)
          }
        } else {
          consecutiveLoud = 0
        }
      }
      return frames
    }

    /** Scans backward to find the last frame where amplitude exceeds threshold. */
    private fun findLastLoudFrame(
      samples: FloatArray, channels: Int, frames: Int,
      threshold: Float, minSilence: Int,
    ): Int {
      var consecutiveLoud = 0
      for (frame in frames - 1 downTo 0) {
        val loud = isFrameLoud(samples, frame, channels, threshold)
        if (loud) {
          consecutiveLoud++
          if (consecutiveLoud >= 1) {
            return (frame + consecutiveLoud - 1).coerceAtMost(frames - 1)
          }
        } else {
          consecutiveLoud = 0
        }
      }
      return 0
    }

    /** Returns true if any channel in the given frame exceeds [threshold]. */
    private fun isFrameLoud(samples: FloatArray, frame: Int, channels: Int, threshold: Float): Boolean {
      val base = frame * channels
      for (ch in 0 until channels) {
        if (abs(samples[base + ch]) > threshold) return true
      }
      return false
    }
  }
}
