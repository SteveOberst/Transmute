@file:Suppress("MagicNumber")

package dev.transmute.testing.video

import dev.transmute.audio.AudioSamples
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.PixelFormat
import dev.transmute.testing.audio.SyntheticAudio
import dev.transmute.video.AudioTrack
import dev.transmute.video.ListFrameStream
import dev.transmute.video.VideoFrame
import dev.transmute.video.VideoIR
import dev.transmute.video.VideoTrack
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Convenience shortcuts for generating synthetic [VideoIR] instances.
 *
 * Each function creates a fully-formed [VideoIR] with a single call.
 * For more flexible video generation — animated per-frame rendering,
 * keyframe transitions, and audio DSL attachment — use the **video DSL**:
 *
 * ```kotlin
 * import dev.transmute.testing.dsl.*
 *
 * val video = syntheticVideo {
 *     size(640, 480)
 *     frameRate = 24.0
 *     duration = 2.seconds
 *     animate { frame ->
 *         solid(Color.lerp(Color.RED, Color.BLUE, frame.progress))
 *     }
 *     audio { sine(440.hz); fadeOut(300.ms) }
 * }
 * ```
 *
 * ### Quick start (static helpers)
 * ```kotlin
 * val video = SyntheticVideo.solidColor(320, 240, frameCount = 30)
 * val fade  = SyntheticVideo.fadeToBlack(320, 240, durationMs = 2000)
 * val bars  = SyntheticVideo.animatedColorBars(640, 480, frameCount = 60)
 * ```
 *
 * ### Design notes
 * - Frame timestamps are evenly spaced: `index * (1000 / frameRate)`.
 * - Audio tracks are optional; pass `withAudio = true` to attach a
 *   [SyntheticAudio] sine-wave track that matches the video duration.
 *
 * @see dev.transmute.testing.dsl.syntheticVideo
 */
object SyntheticVideo {

  /**
   * A video where every frame is the same solid color.
   *
   * Useful for verifying that encoders handle static content and that
   * decoders reproduce the correct number of frames.
   */
  fun solidColor(
    width: Int,
    height: Int,
    frameCount: Int = 30,
    frameRate: Double = 30.0,
    r: Int = 255,
    g: Int = 0,
    b: Int = 0,
    a: Int = 255,
    withAudio: Boolean = false,
  ): VideoIR {
    val frames = (0 until frameCount).map { i ->
      solidFrame(width, height, r, g, b, a, timestampMs(i, frameRate))
    }
    return videoIR(width, height, frameRate, frames, frameCount, withAudio)
  }

  /**
   * Animated horizontal gradient that scrolls across the frame.
   *
   * Each frame shifts the gradient start position, creating a smooth
   * lateral motion — useful for motion estimation / temporal compression testing.
   */
  fun scrollingGradient(
    width: Int,
    height: Int,
    frameCount: Int = 60,
    frameRate: Double = 30.0,
    withAudio: Boolean = false,
  ): VideoIR {
    val bpp = 4
    val stride = width * bpp
    val frames = (0 until frameCount).map { i ->
      val shift = (i.toFloat() / frameCount * 256).roundToInt()
      val data = ByteArray(height * stride)
      for (y in 0 until height) {
        for (x in 0 until width) {
          val v = ((x * 256 / width.coerceAtLeast(1)) + shift) % 256
          val off = y * stride + x * bpp
          data[off] = v.toByte()
          data[off + 1] = v.toByte()
          data[off + 2] = v.toByte()
          data[off + 3] = 0xFF.toByte()
        }
      }
      VideoFrame(ByteArrayPixelBuffer(data), width, height, PixelFormat.RGBA_8888, timestampMs(i, frameRate))
    }
    return videoIR(width, height, frameRate, frames, frameCount, withAudio)
  }

  /**
   * Fade from a solid color to black over the video duration.
   *
   * Useful for testing temporal changes and brightness preservation.
   */
  fun fadeToBlack(
    width: Int,
    height: Int,
    frameCount: Int = 30,
    frameRate: Double = 30.0,
    r: Int = 255,
    g: Int = 255,
    b: Int = 255,
    withAudio: Boolean = false,
  ): VideoIR {
    val frames = (0 until frameCount).map { i ->
      val t = if (frameCount > 1) 1f - i.toFloat() / (frameCount - 1) else 1f
      val cr = (r * t).roundToInt().coerceIn(0, 255)
      val cg = (g * t).roundToInt().coerceIn(0, 255)
      val cb = (b * t).roundToInt().coerceIn(0, 255)
      solidFrame(width, height, cr, cg, cb, 255, timestampMs(i, frameRate))
    }
    return videoIR(width, height, frameRate, frames, frameCount, withAudio)
  }

  /**
   * Fade from black to a solid color over the video duration.
   */
  fun fadeFromBlack(
    width: Int,
    height: Int,
    frameCount: Int = 30,
    frameRate: Double = 30.0,
    r: Int = 255,
    g: Int = 255,
    b: Int = 255,
    withAudio: Boolean = false,
  ): VideoIR {
    val frames = (0 until frameCount).map { i ->
      val t = if (frameCount > 1) i.toFloat() / (frameCount - 1) else 1f
      val cr = (r * t).roundToInt().coerceIn(0, 255)
      val cg = (g * t).roundToInt().coerceIn(0, 255)
      val cb = (b * t).roundToInt().coerceIn(0, 255)
      solidFrame(width, height, cr, cg, cb, 255, timestampMs(i, frameRate))
    }
    return videoIR(width, height, frameRate, frames, frameCount, withAudio)
  }

  /**
   * Alternating solid-color frames (like a strobe).
   *
   * Useful for testing keyframe placement and temporal compression.
   *
   * @param colorA RGBA of even frames.
   * @param colorB RGBA of odd frames.
   */
  fun flashing(
    width: Int,
    height: Int,
    frameCount: Int = 30,
    frameRate: Double = 30.0,
    colorA: IntArray = intArrayOf(255, 255, 255, 255),
    colorB: IntArray = intArrayOf(0, 0, 0, 255),
    withAudio: Boolean = false,
  ): VideoIR {
    require(colorA.size == 4 && colorB.size == 4) { "Colors must be RGBA (4 elements)" }
    val frames = (0 until frameCount).map { i ->
      val c = if (i % 2 == 0) colorA else colorB
      solidFrame(width, height, c[0], c[1], c[2], c[3], timestampMs(i, frameRate))
    }
    return videoIR(width, height, frameRate, frames, frameCount, withAudio)
  }

  /**
   * Animated color bars — SMPTE-style bars that shift left each frame.
   *
   * Useful for visual inspection and testing color-space handling in
   * motion codecs.
   */
  fun animatedColorBars(
    width: Int,
    height: Int,
    frameCount: Int = 60,
    frameRate: Double = 30.0,
    withAudio: Boolean = false,
  ): VideoIR {
    val bars = listOf(
      intArrayOf(255, 255, 255, 255),
      intArrayOf(255, 255, 0, 255),
      intArrayOf(0, 255, 255, 255),
      intArrayOf(0, 255, 0, 255),
      intArrayOf(255, 0, 255, 255),
      intArrayOf(255, 0, 0, 255),
      intArrayOf(0, 0, 255, 255),
    )
    val bpp = 4
    val stride = width * bpp
    val frames = (0 until frameCount).map { i ->
      val shift = i * 4 // shift 4 pixels per frame
      val data = ByteArray(height * stride)
      for (y in 0 until height) {
        for (x in 0 until width) {
          val barIdx = (((x + shift) % width) * bars.size / width.coerceAtLeast(1))
            .coerceIn(0, bars.size - 1)
          val c = bars[barIdx]
          val off = y * stride + x * bpp
          data[off] = c[0].toByte()
          data[off + 1] = c[1].toByte()
          data[off + 2] = c[2].toByte()
          data[off + 3] = c[3].toByte()
        }
      }
      VideoFrame(ByteArrayPixelBuffer(data), width, height, PixelFormat.RGBA_8888, timestampMs(i, frameRate))
    }
    return videoIR(width, height, frameRate, frames, frameCount, withAudio)
  }

  /**
   * Pulsing sine-wave pattern — a radial brightness pulse that expands
   * outward from center over time.
   *
   * Useful for exercising temporal + spatial compression simultaneously.
   */
  fun pulsing(
    width: Int,
    height: Int,
    frameCount: Int = 60,
    frameRate: Double = 30.0,
    withAudio: Boolean = false,
  ): VideoIR {
    val bpp = 4
    val stride = width * bpp
    val cx = width / 2f
    val cy = height / 2f
    val maxR = maxOf(cx, cy).coerceAtLeast(1f)
    val frames = (0 until frameCount).map { i ->
      val phase = i.toFloat() / frameCount * 2f * PI.toFloat()
      val data = ByteArray(height * stride)
      for (y in 0 until height) {
        for (x in 0 until width) {
          val dx = x - cx
          val dy = y - cy
          val r = kotlin.math.sqrt(dx * dx + dy * dy) / maxR
          val v = ((sin((r * 10f - phase).toDouble()) + 1.0) * 127.5).roundToInt().coerceIn(0, 255)
          val off = y * stride + x * bpp
          data[off] = v.toByte()
          data[off + 1] = v.toByte()
          data[off + 2] = v.toByte()
          data[off + 3] = 0xFF.toByte()
        }
      }
      VideoFrame(ByteArrayPixelBuffer(data), width, height, PixelFormat.RGBA_8888, timestampMs(i, frameRate))
    }
    return videoIR(width, height, frameRate, frames, frameCount, withAudio)
  }

  /**
   * Animated checkerboard — the phase inverts every N frames.
   *
   * Useful for testing high-frequency spatial content with temporal changes.
   *
   * @param invertEvery Number of frames between phase inversions.
   */
  fun animatedCheckerboard(
    width: Int,
    height: Int,
    blockSize: Int = 16,
    frameCount: Int = 60,
    frameRate: Double = 30.0,
    invertEvery: Int = 5,
    withAudio: Boolean = false,
  ): VideoIR {
    val bpp = 4
    val stride = width * bpp
    val frames = (0 until frameCount).map { i ->
      val inverted = (i / invertEvery) % 2 != 0
      val data = ByteArray(height * stride)
      for (y in 0 until height) {
        for (x in 0 until width) {
          val isWhite = ((x / blockSize) + (y / blockSize)) % 2 == 0
          val bright = if (inverted) !isWhite else isWhite
          val v = if (bright) 255 else 0
          val off = y * stride + x * bpp
          data[off] = v.toByte()
          data[off + 1] = v.toByte()
          data[off + 2] = v.toByte()
          data[off + 3] = 0xFF.toByte()
        }
      }
      VideoFrame(ByteArrayPixelBuffer(data), width, height, PixelFormat.RGBA_8888, timestampMs(i, frameRate))
    }
    return videoIR(width, height, frameRate, frames, frameCount, withAudio)
  }

  /**
   * Each frame is filled with a unique color derived from the frame index.
   *
   * Spreads frames across the hue spectrum so each is visually distinguishable,
   * useful for verifying frame ordering and drop detection.
   */
  fun hueRotation(
    width: Int,
    height: Int,
    frameCount: Int = 30,
    frameRate: Double = 30.0,
    withAudio: Boolean = false,
  ): VideoIR {
    val frames = (0 until frameCount).map { i ->
      val hue = (i * 360.0 / frameCount) % 360.0
      val rgb = hsvToRgb(hue.toFloat(), 1f, 1f)
      solidFrame(width, height, rgb[0], rgb[1], rgb[2], 255, timestampMs(i, frameRate))
    }
    return videoIR(width, height, frameRate, frames, frameCount, withAudio)
  }

  /**
   * Single frame video — useful for still-image-in-container tests (e.g., GIF single frame).
   */
  fun singleFrame(
    width: Int,
    height: Int,
    r: Int = 128,
    g: Int = 128,
    b: Int = 128,
    withAudio: Boolean = false,
  ): VideoIR {
    val frames = listOf(solidFrame(width, height, r, g, b, 255, 0L))
    return videoIR(width, height, 1.0, frames, 1, withAudio)
  }

  // ---------------------------------------------------------------------------
  // Internal helpers
  // ---------------------------------------------------------------------------

  private fun timestampMs(frameIndex: Int, frameRate: Double): Long =
    (frameIndex * 1000.0 / frameRate).toLong()

  private fun solidFrame(
    width: Int,
    height: Int,
    r: Int,
    g: Int,
    b: Int,
    a: Int,
    timestampMs: Long,
  ): VideoFrame {
    val bpp = 4
    val stride = width * bpp
    val data = ByteArray(height * stride)
    for (i in data.indices step bpp) {
      data[i] = r.toByte()
      data[i + 1] = g.toByte()
      data[i + 2] = b.toByte()
      data[i + 3] = a.toByte()
    }
    return VideoFrame(ByteArrayPixelBuffer(data), width, height, PixelFormat.RGBA_8888, timestampMs)
  }

  private fun videoIR(
    width: Int,
    height: Int,
    frameRate: Double,
    frames: List<VideoFrame>,
    frameCount: Int,
    withAudio: Boolean,
  ): VideoIR {
    val durationMs = (frameCount * 1000.0 / frameRate).toLong()
    val audioTrack = if (withAudio) {
      val audio = SyntheticAudio.sineWave(durationMs = durationMs)
      AudioTrack(
        samples = AudioSamples(audio.samples.data, audio.sampleRate, audio.channelCount),
        sampleStream = null,
      )
    } else {
      null
    }
    return VideoIR(
      videoTrack = VideoTrack(width, height, frameRate, ListFrameStream(frames)),
      audioTrack = audioTrack,
      durationMs = durationMs,
    )
  }

  private fun hsvToRgb(h: Float, s: Float, v: Float): IntArray {
    val c = v * s
    val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
    val m = v - c
    val (r1, g1, b1) = when {
      h < 60f -> Triple(c, x, 0f)
      h < 120f -> Triple(x, c, 0f)
      h < 180f -> Triple(0f, c, x)
      h < 240f -> Triple(0f, x, c)
      h < 300f -> Triple(x, 0f, c)
      else -> Triple(c, 0f, x)
    }
    return intArrayOf(
      ((r1 + m) * 255).roundToInt().coerceIn(0, 255),
      ((g1 + m) * 255).roundToInt().coerceIn(0, 255),
      ((b1 + m) * 255).roundToInt().coerceIn(0, 255),
    )
  }
}
