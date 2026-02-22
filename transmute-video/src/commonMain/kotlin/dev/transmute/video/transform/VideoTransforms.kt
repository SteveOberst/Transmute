package dev.transmute.video.transform

import dev.transmute.core.TransmuteContext
import dev.transmute.core.pipeline.TransformId
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.PixelBuffer
import dev.transmute.video.FrameStream
import dev.transmute.video.VideoFrame
import dev.transmute.video.VideoHint
import dev.transmute.video.VideoIR
import dev.transmute.video.VideoMetadata
import dev.transmute.video.VideoTrack
import dev.transmute.video.VideoTransform

/**
 * Trims video to a specified time range.
 *
 * Note: This is metadata-only trimming that marks trim points.
 * Actual frame extraction requires platform-specific decoders.
 *
 * @param startMs Start time in milliseconds.
 * @param endMs End time in milliseconds (null = end of video).
 */
class VideoTrimTransform(
  val startMs: Long,
  val endMs: Long? = null,
) : VideoTransform {
  override fun wouldTransform(hint: VideoHint): Boolean = true // always trims

  override val id = TransformId("video.trim")

  override suspend fun apply(ir: VideoIR, context: TransmuteContext): VideoIR {

    val actualEnd = endMs ?: ir.durationMs
    val newDuration = actualEnd - startMs

    return ir.copy(
      durationMs = newDuration.coerceAtLeast(0),
      videoTrack = ir.videoTrack.copy(
        frames = TrimmedFrameStream(ir.videoTrack.frames, startMs, actualEnd)
      ),
      metadata = ir.metadata.copy(
        durationMs = newDuration.coerceAtLeast(0),
      ),
    )
  }
}

/**
 * Frame stream wrapper that filters frames within a time range.
 */
private class TrimmedFrameStream(
  private val source: FrameStream,
  private val startMs: Long,
  private val endMs: Long,
) : FrameStream {
  override val frameCount: Long = source.frameCount // Approximation

  private var started = false

  override suspend fun nextFrame(): VideoFrame? {
    while (true) {
      val frame = source.nextFrame() ?: return null
      
      if (frame.timestampMs < startMs) continue
      if (frame.timestampMs > endMs) return null
      
      // Adjust timestamp relative to new start
      return frame.copy(timestampMs = frame.timestampMs - startMs)
    }
  }
}

/**
 * Resizes video frames to fit within maximum dimensions.
 *
 * This transform marks target dimensions; actual scaling requires
 * platform-specific frame processing.
 *
 * @param maxWidth Maximum width in pixels.
 * @param maxHeight Maximum height in pixels.
 */
class VideoResizeTransform(
  val maxWidth: Int,
  val maxHeight: Int,
) : VideoTransform {
  override fun wouldTransform(hint: VideoHint): Boolean =
    hint.width == null || hint.height == null ||
      hint.width > maxWidth || hint.height > maxHeight

  override val id = TransformId("video.resize")

  override suspend fun apply(ir: VideoIR, context: TransmuteContext): VideoIR {

    val track = ir.videoTrack
    val aspectRatio = track.width.toDouble() / track.height

    val (newWidth, newHeight) = if (track.width <= maxWidth && track.height <= maxHeight) {
      track.width to track.height
    } else {
      val widthRatio = maxWidth.toDouble() / track.width
      val heightRatio = maxHeight.toDouble() / track.height
      val scale = minOf(widthRatio, heightRatio)
      
      (track.width * scale).toInt() to (track.height * scale).toInt()
    }

    return ir.copy(
      videoTrack = track.copy(
        width = newWidth,
        height = newHeight,
        frames = ResizedFrameStream(track.frames, newWidth, newHeight)
      )
    )
  }
}

/**
 * Frame stream wrapper that marks frames for resizing.
 */
private class ResizedFrameStream(
  private val source: FrameStream,
  private val targetWidth: Int,
  private val targetHeight: Int,
) : FrameStream {
  override val frameCount: Long = source.frameCount

  override suspend fun nextFrame(): VideoFrame? {
    val frame = source.nextFrame() ?: return null
    
    // Simple nearest-neighbor resize for now
    // Real implementation would use bilinear/bicubic interpolation
    val scaled = scaleFrame(frame, targetWidth, targetHeight)
    return scaled
  }

  private fun scaleFrame(frame: VideoFrame, width: Int, height: Int): VideoFrame {
    if (frame.width == width && frame.height == height) return frame

    val srcData = (frame.buffer as? ByteArrayPixelBuffer)?.data ?: return frame
    val srcWidth = frame.width
    val srcHeight = frame.height
    val bytesPerPixel = 4 // Assume RGBA

    val dstData = ByteArray(width * height * bytesPerPixel)
    val xRatio = srcWidth.toFloat() / width
    val yRatio = srcHeight.toFloat() / height

    for (y in 0 until height) {
      for (x in 0 until width) {
        val srcX = (x * xRatio).toInt().coerceIn(0, srcWidth - 1)
        val srcY = (y * yRatio).toInt().coerceIn(0, srcHeight - 1)
        
        val srcIdx = (srcY * srcWidth + srcX) * bytesPerPixel
        val dstIdx = (y * width + x) * bytesPerPixel

        for (c in 0 until bytesPerPixel) {
          if (srcIdx + c < srcData.size && dstIdx + c < dstData.size) {
            dstData[dstIdx + c] = srcData[srcIdx + c]
          }
        }
      }
    }

    return VideoFrame(
      buffer = ByteArrayPixelBuffer(dstData),
      width = width,
      height = height,
      pixelFormat = frame.pixelFormat,
      timestampMs = frame.timestampMs,
    )
  }
}

/**
 * Changes video frame rate.
 *
 * @param targetFps Target frames per second.
 */
class VideoFrameRateTransform(
  val targetFps: Double,
) : VideoTransform {
  override fun wouldTransform(hint: VideoHint): Boolean =
    hint.fps == null || hint.fps > targetFps

  override val id = TransformId("video.framerate")

  override suspend fun apply(ir: VideoIR, context: TransmuteContext): VideoIR {

    return ir.copy(
      videoTrack = ir.videoTrack.copy(
        frameRate = targetFps,
        frames = FrameRateAdjustedStream(ir.videoTrack.frames, ir.videoTrack.frameRate, targetFps)
      )
    )
  }
}

/**
 * Frame stream that adjusts frame timing for different frame rates.
 */
private class FrameRateAdjustedStream(
  private val source: FrameStream,
  private val sourceFps: Double,
  private val targetFps: Double,
) : FrameStream {
  override val frameCount: Long = ((source.frameCount * targetFps) / sourceFps).toLong()

  private var frameIndex = 0L
  private var lastFrame: VideoFrame? = null

  override suspend fun nextFrame(): VideoFrame? {
    val targetTimestamp = (frameIndex * 1000.0 / targetFps).toLong()
    
    // Advance source until we have a frame at or past target timestamp
    while (lastFrame == null || (lastFrame?.timestampMs ?: 0) < targetTimestamp) {
      val next = source.nextFrame()
      if (next == null) {
        // Return last frame if we've hit the end
        val result = lastFrame ?: return null
        lastFrame = null
        return result.copy(timestampMs = targetTimestamp)
      }
      lastFrame = next
    }

    frameIndex++
    return lastFrame?.copy(timestampMs = targetTimestamp)
  }
}

/**
 * Removes the audio track from a video.
 */
class VideoRemoveAudioTransform : VideoTransform {
  override fun wouldTransform(hint: VideoHint): Boolean = true // always removes audio track

  override val id = TransformId("video.removeAudio")

  override suspend fun apply(ir: VideoIR, context: TransmuteContext): VideoIR {
    return ir.copy(audioTrack = null)
  }
}
