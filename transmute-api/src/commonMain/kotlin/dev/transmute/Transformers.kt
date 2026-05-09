package dev.transmute

import dev.transmute.audio.transform.*
import dev.transmute.image.transform.*
import dev.transmute.image.transform.kernel.ResampleFilter
import dev.transmute.video.transform.*

/**
 * Factory for creating transform instances.
 *
 * Provides domain-specific sub-factories for image, audio, and video
 * transforms. Returned transforms can be used directly with
 * [TransformPipeline][dev.transmute.codec.pipeline.TransformPipeline]:
 *
 * ```kotlin
 * Transmute.video {
 *   transform {
 *     add(Transformers.video().resize(640, 480))
 *     before<VideoResizeTransform>(Transformers.video().trim(0, 5000))
 *   }
 * }.transmute(buf)
 * ```
 *
 * Functions in the sub-factories are annotated with [TransformDescriptor]
 * and [Param] for runtime catalog discovery - no hardcoding in the server.
 */
object Transformers {

  /** Factory for image transforms. */
  fun image(): ImageTransforms = ImageTransforms

  /** Factory for audio transforms. */
  fun audio(): AudioTransforms = AudioTransforms

  /** Factory for video transforms. */
  fun video(): VideoTransforms = VideoTransforms
}

// -- Image transform factory ---

/** Creates image [Transform][dev.transmute.codec.pipeline.Transform] instances. */
object ImageTransforms {

  /** Scale to fit within [maxWidth]x[maxHeight], preserving aspect ratio. No upscaling. */
  @TransformDescriptor("scale", "Proportionally scale to fit within bounds, preserving aspect ratio")
  fun scale(
    @Param("Maximum width in pixels", required = true) maxWidth: Int,
    @Param("Maximum height in pixels", required = true) maxHeight: Int,
  ) = ImageScaleTransform(maxWidth, maxHeight)

  /**
   * Resize to exact [targetWidth]x[targetHeight] using a configurable resample [filter].
   *
   * Unlike [scale], this does **not** preserve aspect ratio - it resizes to the
   * exact dimensions specified. Set [allowUpscale] to `false` to skip images
   * that are already smaller than the target.
   */
  @TransformDescriptor("resize", "Resize to exact dimensions with optional resampling filter")
  fun resize(
    @Param("Target width in pixels", required = true) targetWidth: Int,
    @Param("Target height in pixels", required = true) targetHeight: Int,
    @Param("Resample filter", default = "BICUBIC_MITCHELL") filter: ResampleFilter = ResampleFilter.BICUBIC_MITCHELL,
    @Param("Allow upscaling if source is smaller than target", default = "true") allowUpscale: Boolean = true,
  ) = ImageResizeTransform(targetWidth, targetHeight, filter, allowUpscale)

  /** Crop to sub-region. Coordinates are clamped to image bounds. */
  @TransformDescriptor("crop", "Crop to a rectangular sub-region")
  fun crop(
    @Param("X offset in pixels from the left", required = true) x: Int,
    @Param("Y offset in pixels from the top", required = true) y: Int,
    @Param("Crop width in pixels", required = true) width: Int,
    @Param("Crop height in pixels", required = true) height: Int,
  ) = ImageCropTransform(x, y, width, height)

  /** Rotate clockwise by [degrees] (90, 180, or 270). Defaults to 90 deg. */
  @TransformDescriptor("rotate", "Rotate by an explicit number of degrees clockwise (90, 180, or 270)")
  fun rotate(@Param("Clockwise rotation angle; must be 90, 180, or 270", default = "90", enumValues = "90,180,270") degrees: Int = 90) =
    ImageRotateTransform(degrees)

  /** Convert to grayscale using BT.709 luma coefficients. */
  @TransformDescriptor("grayscale", "Convert to grayscale using BT.709 luma coefficients")
  fun grayscale() = ImageGrayscaleTransform()

  /** Flip horizontally and/or vertically. */
  @TransformDescriptor("flip", "Flip the image horizontally, vertically, or both")
  fun flip(
    @Param("Flip horizontally (mirror left-right)", default = "false") horizontal: Boolean = false,
    @Param("Flip vertically (mirror top-bottom)", default = "false") vertical: Boolean = false,
  ) = ImageFlipTransform(horizontal, vertical)

  /** Adjust brightness and/or contrast. Brightness: 255..+255, contrast: 0..3. */
  @TransformDescriptor("brightnessContrast", "Adjust brightness and contrast")
  fun brightnessContrast(
    @Param("Brightness adjustment (-255 to +255)", default = "0.0", min = "-255", max = "255") brightness: Float = 0f,
    @Param("Contrast multiplier (0 = flat grey, 1 = no change, 3 = high contrast)", default = "1.0", min = "0", max = "3") contrast:
    Float = 1f,
  ) = ImageBrightnessContrastTransform(brightness, contrast)

  /** Apply box blur. Radius: 1 = 3x3, 2 = 5x5, etc. */
  @TransformDescriptor("blur", "Apply box blur")
  fun blur(@Param("Blur radius (1 = 3x3 kernel, 2 = 5x5, ...)", default = "1", min = "1", max = "20") radius: Int = 1) =
    ImageBlurTransform(radius)

  /** Adjust alpha channel opacity (0.0 = transparent, 1.0 = unchanged). */
  @TransformDescriptor("opacity", "Adjust alpha channel opacity")
  fun opacity(@Param("Opacity (0.0 = fully transparent, 1.0 = unchanged)", required = true, min = "0.0", max = "1.0") opacity: Float) =
    ImageOpacityTransform(opacity)
}

// -- Audio transform factory ---

/** Creates audio [Transform][dev.transmute.codec.pipeline.Transform] instances. */
object AudioTransforms {

  /** Normalize peak amplitude. Default target 0.95. */
  @TransformDescriptor("normalize", "Normalize peak amplitude to a target level")
  fun normalize(@Param("Target peak level (0.0 to 1.0)", default = "0.95", min = "0.0", max = "1.0") targetPeak: Float = 0.95f) =
    AudioNormalizeTransform(targetPeak)

  /** Resample to [targetSampleRate] Hz using linear interpolation. */
  @TransformDescriptor("resample", "Resample to a different sample rate")
  fun resample(@Param("Target sample rate in Hz (e.g. 44100, 48000)", required = true) targetSampleRate: Int) =
    AudioResampleTransform(targetSampleRate)

  /** Apply fade-in / fade-out envelopes (milliseconds). */
  @TransformDescriptor("fade", "Apply fade-in and/or fade-out envelopes")
  fun fade(
    @Param("Fade-in duration in milliseconds", default = "0", min = "0") fadeInMs: Long = 0,
    @Param("Fade-out duration in milliseconds", default = "0", min = "0") fadeOutMs: Long = 0,
  ) = AudioFadeTransform(fadeInMs, fadeOutMs)

  /** Trim to time range (milliseconds). [endMs] = null -> end of audio. */
  @TransformDescriptor("trim", "Trim to a specific time range")
  fun trim(
    @Param("Start time in milliseconds", required = true, min = "0") startMs: Long,
    @Param("End time in milliseconds (omit to trim to end)") endMs: Long? = null,
  ) = AudioTrimTransform(startMs, endMs)

  /** Apply volume gain in decibels (+dB louder, dB quieter). */
  @TransformDescriptor("gain", "Apply volume gain or attenuation in decibels")
  fun gain(@Param("Gain in decibels (+dB = louder, -dB = quieter)", required = true, min = "-60", max = "60") db: Float) =
    AudioGainTransform(db)

  /** Convert stereo -> mono by averaging channels. */
  @TransformDescriptor("mono", "Mix down to mono by averaging channels")
  fun mono() = AudioMonoTransform()

  /** Reverse playback. */
  @TransformDescriptor("reverse", "Reverse the audio playback direction")
  fun reverse() = AudioReverseTransform()

  /** Change playback speed without altering pitch (SOLA time-stretch). */
  @TransformDescriptor("speed", "Change playback speed without pitch shift")
  fun speed(@Param("Speed multiplier (0.5 = half speed, 2.0 = double speed)", required = true, min = "0.25", max = "4.0") speed: Float) =
    AudioSpeedTransform(speed)

  /** Trim silence from start and/or end. */
  @TransformDescriptor("silenceTrim", "Remove silence from the start and/or end")
  fun silenceTrim(
    @Param(
      "Silence threshold in dB (below this is considered silence)",
      default = "-40.0",
      min = "-80",
      max = "0",
    ) thresholdDb: Float = -40f,
    @Param("Minimum continuous silence duration to trim in milliseconds", default = "100", min = "0") minSilenceMs: Long = 100,
    @Param("Trim silence from the beginning of the audio", default = "true") trimStart: Boolean = true,
    @Param("Trim silence from the end of the audio", default = "true") trimEnd: Boolean = true,
  ) = AudioSilenceTrimTransform(thresholdDb, minSilenceMs, trimStart, trimEnd)

  /** Dynamic range compressor. */
  @TransformDescriptor("compressor", "Apply dynamic range compression")
  fun compressor(
    @Param("Compression threshold in dB (signal above this is compressed)", default = "-20.0", min = "-80", max = "0") thresholdDb:
    Float = -20f,
    @Param("Compression ratio (higher = more compression)", default = "4.0", min = "1.0", max = "20.0") ratio: Float = 4f,
    @Param("Attack time in milliseconds", default = "10.0", min = "0.1", max = "500") attackMs: Float = 10f,
    @Param("Release time in milliseconds", default = "100.0", min = "1", max = "2000") releaseMs: Float = 100f,
    @Param("Makeup gain in dB applied after compression", default = "0.0", min = "-20", max = "20") makeupGainDb: Float = 0f,
  ) = AudioCompressorTransform(thresholdDb, ratio, attackMs, releaseMs, makeupGainDb)

  /** Remap audio channels. [mapping] defines output->source channel indices. */
  @TransformDescriptor("channelMap", "Remap audio channels to a different layout")
  fun channelMap(@Param("Output-to-source channel index mapping (e.g. [0, 0] for left->mono)", required = true) mapping: IntArray) =
    AudioChannelMapTransform(mapping)
}

// -- Video transform factory ---

/** Creates video [Transform][dev.transmute.codec.pipeline.Transform] instances. */
object VideoTransforms {

  /** Trim to time range (milliseconds). [endMs] = null -> end of video. */
  @TransformDescriptor("trim", "Trim to a specific time range")
  fun trim(
    @Param("Start time in milliseconds", required = true, min = "0") startMs: Long,
    @Param("End time in milliseconds (omit to keep to end)") endMs: Long? = null,
  ) = VideoTrimTransform(startMs, endMs)

  /** Resize frames to fit within [maxWidth]x[maxHeight], preserving aspect ratio. */
  @TransformDescriptor("resize", "Resize frames to fit within bounds, preserving aspect ratio")
  fun resize(
    @Param("Maximum frame width in pixels", required = true) maxWidth: Int,
    @Param("Maximum frame height in pixels", required = true) maxHeight: Int,
  ) = VideoResizeTransform(maxWidth, maxHeight)

  /** Change frame rate. */
  @TransformDescriptor("frameRate", "Change the frame rate of the video")
  fun frameRate(@Param("Target frames per second", required = true, min = "1", max = "240") targetFps: Double) =
    VideoFrameRateTransform(targetFps)

  /** Strip the audio track. */
  @TransformDescriptor("removeAudio", "Strip the audio track from the video")
  fun removeAudio() = VideoRemoveAudioTransform()

  /** Crop frames to a sub-region. Coordinates clamped to frame bounds. */
  @TransformDescriptor("crop", "Crop video frames to a rectangular sub-region")
  fun crop(
    @Param("X offset in pixels from the left", required = true) x: Int,
    @Param("Y offset in pixels from the top", required = true) y: Int,
    @Param("Crop width in pixels", required = true) width: Int,
    @Param("Crop height in pixels", required = true) height: Int,
  ) = VideoCropTransform(x, y, width, height)

  /** Change playback speed. Adjusts both frame timing and audio. */
  @TransformDescriptor("speed", "Change video playback speed (adjusts frames and audio)")
  fun speed(@Param("Speed multiplier (0.5 = half speed, 2.0 = double speed)", required = true, min = "0.25", max = "4.0") speed: Float) =
    VideoSpeedTransform(speed)

  /** Rotate frames by 90 deg, 180 deg, or 270 deg clockwise. */
  @TransformDescriptor("rotate", "Rotate video frames (90 deg, 180 deg, or 270 deg clockwise)")
  fun rotate(@Param("Rotation in degrees -- must be 90, 180, or 270", required = true, enumValues = "90,180,270") degrees: Int) =
    VideoRotateTransform(degrees)
}
