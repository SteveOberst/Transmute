package dev.transmute

import dev.transmute.audio.transform.*
import dev.transmute.image.transform.*
import dev.transmute.image.transform.kernel.ResampleFilter
import dev.transmute.video.transform.*

/**
 * Factory for creating transform instances.
 *
 * Provides domain-specific sub-factories for image, audio, and video
 * transforms.  Returned transforms can be used directly with
 * [TransformPipeline][dev.transmute.core.pipeline.TransformPipeline]:
 *
 * ```kotlin
 * Transmute.video(buf) {
 *   transform {
 *     add(Transformers.video().resize(640, 480))
 *     before<VideoResizeTransform>(Transformers.video().trim(0, 5000))
 *   }
 * }
 * ```
 *
 * Custom transforms that implement [Transform][dev.transmute.core.pipeline.Transform]
 * can be added to any pipeline without registration - just pass instances
 * directly to `add()`, `before<T>()`, etc.
 */
object Transformers {

  /** Factory for image transforms. */
  fun image(): ImageTransforms = ImageTransforms

  /** Factory for audio transforms. */
  fun audio(): AudioTransforms = AudioTransforms

  /** Factory for video transforms. */
  fun video(): VideoTransforms = VideoTransforms
}

// ── Image transform factory ──

/** Creates image [Transform][dev.transmute.core.pipeline.Transform] instances. */
object ImageTransforms {

  /** Scale to fit within [maxWidth]×[maxHeight], preserving aspect ratio. No upscaling. */
  fun scale(maxWidth: Int, maxHeight: Int) = ImageScaleTransform(maxWidth, maxHeight)

  /**
   * Resize to exact [targetWidth]×[targetHeight] using a configurable resample [filter].
   *
   * Unlike [scale], this does **not** preserve aspect ratio - it resizes to the
   * exact dimensions specified. Set [allowUpscale] to `false` to skip images
   * that are already smaller than the target.
   */
  fun resize(
    targetWidth: Int,
    targetHeight: Int,
    filter: ResampleFilter = ResampleFilter.BICUBIC_MITCHELL,
    allowUpscale: Boolean = true,
  ) = ImageResizeTransform(targetWidth, targetHeight, filter, allowUpscale)

  /** Crop to sub-region. Coordinates are clamped to image bounds. */
  fun crop(x: Int, y: Int, width: Int, height: Int) = ImageCropTransform(x, y, width, height)

  /** Auto-rotate based on EXIF orientation, then set orientation to NORMAL. */
  fun rotate() = ImageRotateTransform()

  /** Convert to grayscale using BT.709 luma coefficients. */
  fun grayscale() = ImageGrayscaleTransform()

  /** Flip horizontally and/or vertically. */
  fun flip(horizontal: Boolean = false, vertical: Boolean = false) =
    ImageFlipTransform(horizontal, vertical)

  /** Adjust brightness and/or contrast. Brightness: −255..+255, contrast: 0..3. */
  fun brightnessContrast(brightness: Float = 0f, contrast: Float = 1f) =
    ImageBrightnessContrastTransform(brightness, contrast)

  /** Apply box blur. Radius: 1 = 3×3, 2 = 5×5, etc. */
  fun blur(radius: Int = 1) = ImageBlurTransform(radius)

  /** Adjust alpha channel opacity (0.0 = transparent, 1.0 = unchanged). */
  fun opacity(opacity: Float) = ImageOpacityTransform(opacity)
}

// ── Audio transform factory ──

/** Creates audio [Transform][dev.transmute.core.pipeline.Transform] instances. */
object AudioTransforms {

  /** Normalize peak amplitude. Default target 0.95. */
  fun normalize(targetPeak: Float = 0.95f) = AudioNormalizeTransform(targetPeak)

  /** Resample to [targetSampleRate] Hz using linear interpolation. */
  fun resample(targetSampleRate: Int) = AudioResampleTransform(targetSampleRate)

  /** Apply fade-in / fade-out envelopes (milliseconds). */
  fun fade(fadeInMs: Long = 0, fadeOutMs: Long = 0) = AudioFadeTransform(fadeInMs, fadeOutMs)

  /** Trim to time range (milliseconds). [endMs] = null → end of audio. */
  fun trim(startMs: Long, endMs: Long? = null) = AudioTrimTransform(startMs, endMs)

  /** Apply volume gain in decibels (+dB louder, −dB quieter). */
  fun gain(db: Float) = AudioGainTransform(db)

  /** Convert stereo → mono by averaging channels. */
  fun mono() = AudioMonoTransform()

  /** Reverse playback. */
  fun reverse() = AudioReverseTransform()

  /** Change playback speed without altering pitch (SOLA time-stretch). */
  fun speed(speed: Float) = AudioSpeedTransform(speed)

  /** Trim silence from start and/or end. */
  fun silenceTrim(
    thresholdDb: Float = -40f,
    minSilenceMs: Long = 100,
    trimStart: Boolean = true,
    trimEnd: Boolean = true,
  ) = AudioSilenceTrimTransform(thresholdDb, minSilenceMs, trimStart, trimEnd)

  /** Dynamic range compressor. */
  fun compressor(
    thresholdDb: Float = -20f,
    ratio: Float = 4f,
    attackMs: Float = 10f,
    releaseMs: Float = 100f,
    makeupGainDb: Float = 0f,
  ) = AudioCompressorTransform(thresholdDb, ratio, attackMs, releaseMs, makeupGainDb)

  /** Remap audio channels. [mapping] defines output→source channel indices. */
  fun channelMap(mapping: IntArray) = AudioChannelMapTransform(mapping)
}

// ── Video transform factory ──

/** Creates video [Transform][dev.transmute.core.pipeline.Transform] instances. */
object VideoTransforms {

  /** Trim to time range (milliseconds). [endMs] = null → end of video. */
  fun trim(startMs: Long, endMs: Long? = null) = VideoTrimTransform(startMs, endMs)

  /** Resize frames to fit within [maxWidth]×[maxHeight], preserving aspect ratio. */
  fun resize(maxWidth: Int, maxHeight: Int) = VideoResizeTransform(maxWidth, maxHeight)

  /** Change frame rate. */
  fun frameRate(targetFps: Double) = VideoFrameRateTransform(targetFps)

  /** Strip the audio track. */
  fun removeAudio() = VideoRemoveAudioTransform()

  /** Crop frames to a sub-region. Coordinates clamped to frame bounds. */
  fun crop(x: Int, y: Int, width: Int, height: Int) = VideoCropTransform(x, y, width, height)

  /** Change playback speed. Adjusts both frame timing and audio. */
  fun speed(speed: Float) = VideoSpeedTransform(speed)

  /** Rotate frames by 90°, 180°, or 270° clockwise. */
  fun rotate(degrees: Int) = VideoRotateTransform(degrees)
}
