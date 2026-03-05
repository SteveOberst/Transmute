@file:Suppress("MagicNumber")

package dev.transmute.testing.dsl

import dev.transmute.audio.AudioSamples
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.PixelFormat
import dev.transmute.testing.Color
import dev.transmute.video.AudioTrack
import dev.transmute.video.ListFrameStream
import dev.transmute.video.VideoFrame
import dev.transmute.video.VideoIR
import dev.transmute.video.VideoTrack
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════════════════════════
//  Entry point
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Build a synthetic [VideoIR] using the video DSL.
 *
 * ```kotlin
 * // Static solid-colour video
 * val clip = syntheticVideo {
 *     size(320, 240)
 *     frameRate = 30.0
 *     duration = 1.seconds
 *     staticFrame { solid(Color.RED) }
 * }
 *
 * // Animated fade
 * val fade = syntheticVideo {
 *     size(640, 480)
 *     frameRate = 24.0
 *     frameCount = 48
 *     animate { frame ->
 *         solid(Color.lerp(Color.BLACK, Color.WHITE, frame.progress))
 *     }
 * }
 *
 * // Per-frame generator with audio
 * val demo = syntheticVideo {
 *     size(320, 240)
 *     frameRate = 30.0
 *     duration = 2.seconds
 *     animate { frame ->
 *         checkerboard {
 *             blockSize = 8 + (frame.progress * 24).toInt()
 *         }
 *     }
 *     audio {
 *         sine(440.hz)
 *         fadeOut(200.ms)
 *     }
 * }
 *
 * // Keyframed transitions
 * val kf = syntheticVideo {
 *     size(320, 240)
 *     frameRate = 30.0
 *     frameCount = 90
 *     keyframes {
 *         at(0f)   { solid(Color.RED) }
 *         at(0.5f) { solid(Color.GREEN) }
 *         at(1f)   { solid(Color.BLUE) }
 *     }
 * }
 * ```
 */
fun syntheticVideo(block: VideoScope.() -> Unit): VideoIR =
  VideoScope().apply(block).build()

// ═══════════════════════════════════════════════════════════════════════════════
//  Root scope
// ═══════════════════════════════════════════════════════════════════════════════

@SyntheticMediaDsl
class VideoScope {
  /** Frame width in pixels. */
  var width: Int = 320

  /** Frame height in pixels. */
  var height: Int = 240

  /** Frame rate (frames per second). */
  var frameRate: Double = 30.0

  /**
   * Number of frames to generate.
   *
   * If set explicitly, [duration] is derived from `frameCount / frameRate`.
   * If [duration] is set instead, `frameCount` is derived from it.
   */
  var frameCount: Int = -1 // sentinel; derived later

  /**
   * Duration in milliseconds.
   *
   * If set explicitly, [frameCount] is derived as `(duration * frameRate / 1000)`.
   * If [frameCount] is also set, [frameCount] takes priority.
   */
  var duration: Long = -1L // sentinel; derived later

  /** Pixel format for generated frames. */
  var pixelFormat: PixelFormat = PixelFormat.RGBA_8888

  // ---- internal ----
  internal var frameGenerator: FrameGenerator? = null
  internal var audioBuilder: AudioScope? = null

  /** Set width and height in one call. */
  fun size(w: Int, h: Int) {
    width = w
    height = h
  }

  // ─────────────────────────── Frame generation ──────────────────────

  /**
   * Every frame is identical — described once by an [ImageScope] block.
   */
  fun staticFrame(block: ImageScope.() -> Unit) {
    frameGenerator = StaticFrameGen(block)
  }

  /**
   * Animate frames: the lambda receives a [FrameContext] and configures
   * an [ImageScope] per frame.
   *
   * Inside the lambda you have access to all [ImageScope] drawing commands
   * **and** [FrameContext] with `index`, `count`, `progress`, `timestampMs`.
   */
  fun animate(block: ImageScope.(frame: FrameContext) -> Unit) {
    frameGenerator = AnimateFrameGen(block)
  }

  /**
   * Define keyframed transitions.
   *
   * Each keyframe specifies a progress value (0.0–1.0) and the image at that point.
   * Frames between keyframes are interpolated by linearly blending the two nearest
   * keyframes' pixel data.
   */
  fun keyframes(block: KeyframeScope.() -> Unit) {
    val scope = KeyframeScope().apply(block)
    frameGenerator = KeyframeFrameGen(scope.entries.sortedBy { it.progress })
  }

  // ─────────────────────────── Audio ─────────────────────────────────

  /** Attach an audio track described by the audio DSL. Duration auto-matches video. */
  fun audio(block: AudioScope.() -> Unit) {
    audioBuilder = AudioScope().apply(block)
  }

  // ─────────────────────────── Build ─────────────────────────────────

  internal fun build(): VideoIR {
    // Resolve frameCount / duration
    val resolvedFrameCount: Int
    val resolvedDurationMs: Long
    when {
      frameCount > 0 && duration > 0 -> {
        resolvedFrameCount = frameCount
        resolvedDurationMs = duration
      }
      frameCount > 0 -> {
        resolvedFrameCount = frameCount
        resolvedDurationMs = (frameCount * 1000.0 / frameRate).toLong()
      }
      duration > 0 -> {
        resolvedDurationMs = duration
        resolvedFrameCount = (duration * frameRate / 1000.0).roundToInt().coerceAtLeast(1)
      }
      else -> {
        resolvedFrameCount = 30
        resolvedDurationMs = (30 * 1000.0 / frameRate).toLong()
      }
    }

    val gen = frameGenerator ?: StaticFrameGen { solid(Color.BLACK) }
    val frames = gen.generate(this, resolvedFrameCount, resolvedDurationMs)

    val audioTrack = audioBuilder?.let { ab ->
      ab.duration = resolvedDurationMs
      val audioIR = ab.build()
      AudioTrack(
        samples = AudioSamples(audioIR.samples.data, audioIR.sampleRate, audioIR.channelCount),
        sampleStream = null,
      )
    }

    return VideoIR(
      videoTrack = VideoTrack(width, height, frameRate, ListFrameStream(frames)),
      audioTrack = audioTrack,
      durationMs = resolvedDurationMs,
    )
  }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Sub-scopes & data
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Context passed to frame animation lambdas.
 *
 * Provides the current frame's position in the timeline.
 */
data class FrameContext(
  /** Zero-based frame index. */
  val index: Int,
  /** Total number of frames. */
  val count: Int,
  /** Timestamp of this frame in milliseconds. */
  val timestampMs: Long,
  /** Progress through the video: 0.0 (first frame) to 1.0 (last frame). */
  val progress: Float,
)

/** Scope for keyframe definitions. */
@SyntheticMediaDsl
class KeyframeScope {
  internal val entries = mutableListOf<KeyframeEntry>()

  /**
   * Define a keyframe at [progress] (0.0–1.0).
   *
   * The [block] configures the image for that keyframe.
   */
  fun at(progress: Float, block: ImageScope.() -> Unit) {
    entries += KeyframeEntry(progress.coerceIn(0f, 1f), block)
  }
}

internal data class KeyframeEntry(val progress: Float, val block: ImageScope.() -> Unit)

// ═══════════════════════════════════════════════════════════════════════════════
//  Frame generators (internal)
// ═══════════════════════════════════════════════════════════════════════════════

internal sealed interface FrameGenerator {
  fun generate(scope: VideoScope, frameCount: Int, durationMs: Long): List<VideoFrame>
}

internal class StaticFrameGen(private val block: ImageScope.() -> Unit) : FrameGenerator {
  override fun generate(scope: VideoScope, frameCount: Int, durationMs: Long): List<VideoFrame> {
    val imgScope = ImageScope().apply {
      width = scope.width
      height = scope.height
      pixelFormat = scope.pixelFormat
      block()
    }
    val data = imgScope.renderToBytes()
    return (0 until frameCount).map { i ->
      val ts = (i * 1000.0 / scope.frameRate).toLong()
      VideoFrame(ByteArrayPixelBuffer(data.copyOf()), scope.width, scope.height, scope.pixelFormat, ts)
    }
  }
}

internal class AnimateFrameGen(
  private val block: ImageScope.(FrameContext) -> Unit,
) : FrameGenerator {
  override fun generate(scope: VideoScope, frameCount: Int, durationMs: Long): List<VideoFrame> {
    return (0 until frameCount).map { i ->
      val ts = (i * 1000.0 / scope.frameRate).toLong()
      val progress = if (frameCount > 1) i.toFloat() / (frameCount - 1) else 0f
      val ctx = FrameContext(index = i, count = frameCount, timestampMs = ts, progress = progress)
      val imgScope = ImageScope().apply {
        width = scope.width
        height = scope.height
        pixelFormat = scope.pixelFormat
        block(ctx)
      }
      val data = imgScope.renderToBytes()
      VideoFrame(ByteArrayPixelBuffer(data), scope.width, scope.height, scope.pixelFormat, ts)
    }
  }
}

internal class KeyframeFrameGen(private val keyframes: List<KeyframeEntry>) : FrameGenerator {
  override fun generate(scope: VideoScope, frameCount: Int, durationMs: Long): List<VideoFrame> {
    require(keyframes.isNotEmpty()) { "At least one keyframe is required" }

    // Pre-render each keyframe
    val rendered = keyframes.map { kf ->
      kf.progress to ImageScope().apply {
        width = scope.width
        height = scope.height
        pixelFormat = scope.pixelFormat
        kf.block(this)
      }.renderToBytes()
    }

    val bpp = scope.pixelFormat.bytesPerPixel
    val stride = scope.width * bpp
    val bufSize = scope.height * stride

    return (0 until frameCount).map { i ->
      val ts = (i * 1000.0 / scope.frameRate).toLong()
      val progress = if (frameCount > 1) i.toFloat() / (frameCount - 1) else 0f

      // Find surrounding keyframes
      val after = rendered.indexOfFirst { it.first >= progress }
      val data = when {
        after <= 0 -> rendered.first().second.copyOf()
        after >= rendered.size -> rendered.last().second.copyOf()
        else -> {
          val (pA, dA) = rendered[after - 1]
          val (pB, dB) = rendered[after]
          val t = if (pB > pA) (progress - pA) / (pB - pA) else 0f
          ByteArray(bufSize) { idx ->
            val a = dA[idx].toInt() and 0xFF
            val b = dB[idx].toInt() and 0xFF
            (a + (b - a) * t).roundToInt().coerceIn(0, 255).toByte()
          }
        }
      }
      VideoFrame(ByteArrayPixelBuffer(data), scope.width, scope.height, scope.pixelFormat, ts)
    }
  }
}
