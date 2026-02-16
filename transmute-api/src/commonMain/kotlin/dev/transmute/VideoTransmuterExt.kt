package dev.transmute

import dev.transmute.video.transform.VideoCropTransform
import dev.transmute.video.transform.VideoFrameRateTransform
import dev.transmute.video.transform.VideoRemoveAudioTransform
import dev.transmute.video.transform.VideoResizeTransform
import dev.transmute.video.transform.VideoRotateTransform
import dev.transmute.video.transform.VideoSpeedTransform
import dev.transmute.video.transform.VideoTrimTransform

/** Trim to time range (milliseconds). [endMs] = null → end of video. */
fun VideoTransmuter.trim(startMs: Long, endMs: Long? = null): VideoTransmuter = apply {
  pipeline.add(VideoTrimTransform(startMs, endMs))
}

/** Resize frames to fit within [maxWidth]×[maxHeight], preserving aspect ratio. No upscaling. */
fun VideoTransmuter.resize(maxWidth: Int, maxHeight: Int): VideoTransmuter = apply {
  pipeline.add(VideoResizeTransform(maxWidth, maxHeight))
}

/** Change frame rate. */
fun VideoTransmuter.frameRate(fps: Double): VideoTransmuter = apply {
  pipeline.add(VideoFrameRateTransform(fps))
}

/** Strip the audio track. */
fun VideoTransmuter.removeAudio(): VideoTransmuter = apply {
  pipeline.add(VideoRemoveAudioTransform())
}

/** Crop frames to a sub-region. Coordinates clamped to frame bounds. */
fun VideoTransmuter.crop(x: Int, y: Int, width: Int, height: Int): VideoTransmuter = apply {
  pipeline.add(VideoCropTransform(x, y, width, height))
}

/** Change playback speed. Adjusts both frames and audio. */
fun VideoTransmuter.speed(speed: Float): VideoTransmuter = apply {
  pipeline.add(VideoSpeedTransform(speed))
}

/** Rotate frames by 90°, 180°, or 270° clockwise. */
fun VideoTransmuter.rotate(degrees: Int): VideoTransmuter = apply {
  pipeline.add(VideoRotateTransform(degrees))
}
