package dev.transmute.video

import dev.transmute.audio.AudioSamples
import dev.transmute.audio.SampleStream
import dev.transmute.common.Closeable
import dev.transmute.image.PixelBuffer
import dev.transmute.image.PixelFormat

/**
 * Platform-agnostic intermediate representation for a decoded video.
 *
 * Every video decoder produces a [VideoIR]; every video encoder consumes one.
 * Video frames reuse [PixelBuffer] / [PixelFormat] from the image module,
 * and audio tracks reuse [AudioSamples] / [SampleStream] from the audio module.
 */
data class VideoIR(
  val videoTrack: VideoTrack,
  val audioTrack: AudioTrack?,
  val durationMs: Long,
  val metadata: VideoMetadata = VideoMetadata(),
)

// --- Video track ---

data class VideoTrack(val width: Int, val height: Int, val frameRate: Double, val frames: FrameStream)

/** Pull-based streaming access to decoded video frames. */
interface FrameStream : Closeable {
  val frameCount: Long
  suspend fun nextFrame(): VideoFrame?
}

/**
 * Simple [FrameStream] backed by a pre-decoded list of frames.
 * Used by platform decoders that extract all frames up-front.
 */
class ListFrameStream(private val frames: List<VideoFrame>) : FrameStream {
  private var index = 0
  override val frameCount: Long = frames.size.toLong()
  override suspend fun nextFrame(): VideoFrame? {
    if (index >= frames.size) return null
    return frames[index++]
  }

  override fun close() {}
}

data class VideoFrame(val buffer: PixelBuffer, val width: Int, val height: Int, val pixelFormat: PixelFormat, val timestampMs: Long)

// --- Audio track (within a video container) ---

data class AudioTrack(val samples: AudioSamples, val sampleStream: SampleStream?)

// --- Metadata ---

data class VideoMetadata(
  val title: String? = null,
  val artist: String? = null,
  val durationMs: Long? = null,
  val bitrateKbps: Int? = null,
  val appMetadata: Map<String, String> = emptyMap(),
)
