package dev.transmute

import dev.transmute.video.VideoFormat
import dev.transmute.video.transform.VideoCropTransform
import dev.transmute.video.transform.VideoFrameRateTransform
import dev.transmute.video.transform.VideoRemoveAudioTransform
import dev.transmute.video.transform.VideoResizeTransform
import dev.transmute.video.transform.VideoRotateTransform
import dev.transmute.video.transform.VideoSpeedTransform
import dev.transmute.video.transform.VideoTrimTransform

/** Trim to time range (milliseconds). [endMs] = null → end of video. */
fun <IN, OUT> DynamicVideoTransmuterBuilder<IN, OUT>.trim(startMs: Long, endMs: Long? = null): DynamicVideoTransmuterBuilder<IN, OUT> = apply {
  transform { add(VideoTrimTransform(startMs, endMs)) }
}

fun <IN, OUT : VideoFormat> VideoTransmuterBuilder<IN, OUT>.trim(
  startMs: Long,
  endMs: Long? = null,
): VideoTransmuterBuilder<IN, OUT> = apply {
  transform { add(VideoTrimTransform(startMs, endMs)) }
}

/** Resize frames to fit within [maxWidth]×[maxHeight], preserving aspect ratio. No upscaling. */
fun <IN, OUT> DynamicVideoTransmuterBuilder<IN, OUT>.resize(maxWidth: Int, maxHeight: Int): DynamicVideoTransmuterBuilder<IN, OUT> = apply {
  transform { add(VideoResizeTransform(maxWidth, maxHeight)) }
}

fun <IN, OUT : VideoFormat> VideoTransmuterBuilder<IN, OUT>.resize(
  maxWidth: Int,
  maxHeight: Int,
): VideoTransmuterBuilder<IN, OUT> = apply {
  transform { add(VideoResizeTransform(maxWidth, maxHeight)) }
}

/** Change frame rate. */
fun <IN, OUT> DynamicVideoTransmuterBuilder<IN, OUT>.frameRate(fps: Double): DynamicVideoTransmuterBuilder<IN, OUT> = apply {
  transform { add(VideoFrameRateTransform(fps)) }
}

fun <IN, OUT : VideoFormat> VideoTransmuterBuilder<IN, OUT>.frameRate(fps: Double): VideoTransmuterBuilder<IN, OUT> = apply {
  transform { add(VideoFrameRateTransform(fps)) }
}

/** Strip the audio track. */
fun <IN, OUT> DynamicVideoTransmuterBuilder<IN, OUT>.removeAudio(): DynamicVideoTransmuterBuilder<IN, OUT> = apply {
  transform { add(VideoRemoveAudioTransform()) }
}

fun <IN, OUT : VideoFormat> VideoTransmuterBuilder<IN, OUT>.removeAudio(): VideoTransmuterBuilder<IN, OUT> = apply {
  transform { add(VideoRemoveAudioTransform()) }
}

/** Crop frames to a sub-region. Coordinates clamped to frame bounds. */
fun <IN, OUT> DynamicVideoTransmuterBuilder<IN, OUT>.crop(x: Int, y: Int, width: Int, height: Int): DynamicVideoTransmuterBuilder<IN, OUT> = apply {
  transform { add(VideoCropTransform(x, y, width, height)) }
}

fun <IN, OUT : VideoFormat> VideoTransmuterBuilder<IN, OUT>.crop(
  x: Int,
  y: Int,
  width: Int,
  height: Int,
): VideoTransmuterBuilder<IN, OUT> = apply {
  transform { add(VideoCropTransform(x, y, width, height)) }
}

/** Change playback speed. Adjusts both frames and audio. */
fun <IN, OUT> DynamicVideoTransmuterBuilder<IN, OUT>.speed(speed: Float): DynamicVideoTransmuterBuilder<IN, OUT> = apply {
  transform { add(VideoSpeedTransform(speed)) }
}

fun <IN, OUT : VideoFormat> VideoTransmuterBuilder<IN, OUT>.speed(speed: Float): VideoTransmuterBuilder<IN, OUT> = apply {
  transform { add(VideoSpeedTransform(speed)) }
}

/** Rotate frames by 90°, 180°, or 270° clockwise. */
fun <IN, OUT> DynamicVideoTransmuterBuilder<IN, OUT>.rotate(degrees: Int): DynamicVideoTransmuterBuilder<IN, OUT> = apply {
  transform { add(VideoRotateTransform(degrees)) }
}

fun <IN, OUT : VideoFormat> VideoTransmuterBuilder<IN, OUT>.rotate(degrees: Int): VideoTransmuterBuilder<IN, OUT> = apply {
  transform { add(VideoRotateTransform(degrees)) }
}
