package dev.transmute.video.transform

import dev.transmute.audio.AudioSamples
import dev.transmute.common.PipelineContext
import dev.transmute.codec.pipeline.TransformId
import dev.transmute.video.AudioTrack
import dev.transmute.video.FrameStream
import dev.transmute.video.VideoFrame
import dev.transmute.video.VideoHint
import dev.transmute.video.VideoIR
import dev.transmute.video.VideoTransform
import kotlin.math.roundToInt

/**
 * Changes video playback speed.
 *
 * Adjusts both frame timestamps and audio speed to keep them in sync.
 * Audio samples are resampled proportionally - at 2x speed, each second
 * of output contains 2 seconds of source audio played twice as fast.
 *
 * - [speed] > 1.0 -> faster playback (shorter duration)
 * - [speed] < 1.0 -> slow-motion (longer duration)
 *
 * Unlike [AudioSpeedTransform][dev.transmute.audio.transform.AudioSpeedTransform],
 * this does *not* preserve pitch - the audio is simply resampled.
 * Pitch preservation for video audio would require integrating SOLA
 * with frame timing, which is better handled at the platform layer.
 *
 * @param speed Playback speed multiplier. Must be > 0.
 */
class VideoSpeedTransform(
  val speed: Float,
) : VideoTransform {

  override fun wouldTransform(hint: VideoHint): Boolean = speed != 1f

  override val id = TransformId("video.speed")

  override suspend fun apply(ir: VideoIR, context: PipelineContext): VideoIR {
    require(speed > 0f) { "Speed must be > 0, got $speed" }
    if (speed == 1f) return ir

    context.logger.info("VideoSpeedTransform: ${speed}x speed")

    val newDuration = (ir.durationMs / speed).toLong()

    val adjustedAudio = ir.audioTrack?.let { track ->
      val samples = track.samples
      val srcFrameCount = samples.data.size / samples.channelCount
      val dstFrameCount = (srcFrameCount / speed).roundToInt()
      val dstData = FloatArray(dstFrameCount * samples.channelCount)

      // Linear interpolation resampling to match the new duration.
      for (outFrame in 0 until dstFrameCount) {
        val srcPos = outFrame * speed
        val srcFrame = srcPos.toInt()
        val frac = srcPos - srcFrame

        for (ch in 0 until samples.channelCount) {
          val idx0 = srcFrame * samples.channelCount + ch
          val idx1 = ((srcFrame + 1) * samples.channelCount + ch).coerceAtMost(samples.data.size - 1)
          val s0 = if (idx0 < samples.data.size) samples.data[idx0] else 0f
          val s1 = if (idx1 < samples.data.size) samples.data[idx1] else s0
          dstData[outFrame * samples.channelCount + ch] = (s0 + (s1 - s0) * frac.toFloat())
        }
      }

      AudioTrack(
        samples = AudioSamples(dstData, samples.sampleRate, samples.channelCount),
        sampleStream = null,
      )
    }

    return ir.copy(
      videoTrack = ir.videoTrack.copy(
        frames = SpeedAdjustedFrameStream(ir.videoTrack.frames, speed),
      ),
      audioTrack = adjustedAudio ?: ir.audioTrack,
      durationMs = newDuration,
      metadata = ir.metadata.copy(durationMs = newDuration),
    )
  }
}

/**
 * Adjusts frame timestamps by the speed factor.
 */
private class SpeedAdjustedFrameStream(
  private val source: FrameStream,
  private val speed: Float,
) : FrameStream {
  override val frameCount: Long = (source.frameCount / speed).toLong()

  override fun close() = source.close()

  override suspend fun nextFrame(): VideoFrame? {
    val frame = source.nextFrame() ?: return null
    return frame.copy(timestampMs = (frame.timestampMs / speed).toLong())
  }
}
