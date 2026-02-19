package dev.transmute

import dev.transmute.audio.AudioFormatDetector
import dev.transmute.audio.AudioIR
import dev.transmute.audio.AudioMetadataTransform
import dev.transmute.audio.AudioRegistries
import dev.transmute.core.AudioFormat
import dev.transmute.core.ConversionContext
import dev.transmute.core.ConversionLogger
import dev.transmute.core.ImageFormat
import dev.transmute.core.MediaFormat
import dev.transmute.core.MetadataPolicy
import dev.transmute.core.TransmuteLogging
import dev.transmute.core.VideoFormat
import dev.transmute.image.ImageFormatDetector
import dev.transmute.image.ImageIR
import dev.transmute.image.ImageMetadataTransform
import dev.transmute.image.ImageRegistries
import dev.transmute.video.VideoFormatDetector
import dev.transmute.video.VideoIR
import dev.transmute.video.VideoMetadataTransform
import dev.transmute.video.VideoRegistries
import dev.transmute.core.pipeline.TransformPipeline
import dev.transmute.image.ImageHint
import dev.transmute.audio.AudioHint
import dev.transmute.video.VideoHint
import dev.transmute.image.transform.ImageScaleTransform
import dev.transmute.image.transform.ImageResizeTransform
import dev.transmute.image.transform.ImageBrightnessContrastTransform
import dev.transmute.image.transform.ImageBlurTransform
import dev.transmute.image.transform.ImageOpacityTransform
import dev.transmute.image.transform.ImageFlipTransform
import dev.transmute.audio.transform.AudioResampleTransform
import dev.transmute.audio.transform.AudioSpeedTransform
import dev.transmute.audio.transform.AudioGainTransform
import dev.transmute.audio.transform.AudioFadeTransform
import dev.transmute.audio.transform.AudioCompressorTransform
import dev.transmute.audio.transform.AudioChannelMapTransform
import dev.transmute.video.transform.VideoResizeTransform
import dev.transmute.video.transform.VideoFrameRateTransform
import dev.transmute.video.transform.VideoSpeedTransform
import dev.transmute.video.transform.VideoRemoveAudioTransform
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// ── TransmuteType: typed key for each media domain ──

/**
 * Discriminates the three media domains at the type level.
 *
 * Use as a key with [Transmute.transmute] for a fully-typed entry point:
 * ```kotlin
 * val result = Transmute.transmute(TransmuteType.Image, bytes) {
 *   scale(800, 600)
 *   outputFormat(ImageFormat.WEBP)
 * }
 * ```
 */
sealed class TransmuteType<T : Transmuter<*>> {
  data object Image : TransmuteType<ImageTransmuter>()
  data object Audio : TransmuteType<AudioTransmuter>()
  data object Video : TransmuteType<VideoTransmuter>()
}

// ── Transmuter: base interface for all media transmuters ──

/**
 * A configurable, suspending media-conversion pipeline.
 *
 * All three transmuters ([ImageTransmuter], [AudioTransmuter], [VideoTransmuter])
 * share the same output API surface.
 */
interface Transmuter<Self : Transmuter<Self>> {

  fun metadata(policy: MetadataPolicy): Self

  fun onProgress(callback: (Float) -> Unit): Self

  /**
   * Run the pipeline on [source] and return the encoded bytes.
   *
   * The transmuter is stateless with respect to [source] and can be called
   * repeatedly with different inputs after a single configuration pass.
   */
  suspend fun transmute(source: ByteArray): ByteArray

  /**
   * Run the pipeline on [source] and write the result into [buffer] at [offset].
   *
   * Useful for avoiding an extra copy when the caller already owns a buffer
   * (e.g. writing into a memory-mapped file or a pre-allocated NIO ByteBuffer).
   *
   * @return The number of bytes written.
   */
  suspend fun transmuteInto(source: ByteArray, buffer: ByteArray, offset: Int = 0): Int

}

// ── Transmute facade ──

/**
 * Public API facade for Transmute.
 *
 * Transmuters are reusable builders — configure once, apply many times:
 *
 * ```kotlin
 * // 1. Reusable builder (configure once, apply to many inputs)
 * val transmuter = Transmute.image()
 *   .apply {
 *     scale(800, 600)
 *     outputFormat(ImageFormat.WEBP)
 *   }
 * val resultA = transmuter.transmute(bytesA)
 * val resultB = transmuter.transmute(bytesB)
 *
 * // 2. Filter: check whether the transmuter would change a given item
 * val hint = ImageHint(width = 400, height = 300, format = ImageFormat.JPEG)
 * if (transmuter.wouldAffect(hint)) {
 *   val result = transmuter.transmute(bytes)
 * }
 *
 * // 3. One-shot DSL block (still supported for convenience)
 * val bytes = Transmute.image(myBytes) {
 *   scale(800, 600)
 *   outputFormat(ImageFormat.WEBP)
 * }
 *
 * // 4. Apply a pre-configured transmuter to a single source
 * val bytes = Transmute.image(myBytes, transmuter)
 *
 * // 5. Typed dispatch
 * val bytes = Transmute.transmute(TransmuteType.Image, myBytes) {
 *   scale(800, 600)
 * }
 * ```
 */
object Transmute {

  /** Create a reusable [ImageTransmuter] with no source bytes. Configure once, apply many times. */
  fun image(): ImageTransmuter = ImageTransmuter()

  /**
   * One-shot convenience: configure via [block] and transmute [source] immediately.
   * Equivalent to `Transmute.image().apply(block).transmute(source)`.
   */
  suspend fun image(source: ByteArray, block: ImageTransmuter.() -> Unit): ByteArray =
    ImageTransmuter().apply(block).transmute(source)

  /** Apply a pre-configured [transmuter] to [source]. */
  suspend fun image(source: ByteArray, transmuter: ImageTransmuter): ByteArray =
    transmuter.transmute(source)

  /** Create a reusable [AudioTransmuter] with no source bytes. Configure once, apply many times. */
  fun audio(): AudioTransmuter = AudioTransmuter()

  /**
   * One-shot convenience: configure via [block] and transmute [source] immediately.
   * Equivalent to `Transmute.audio().apply(block).transmute(source)`.
   */
  suspend fun audio(source: ByteArray, block: AudioTransmuter.() -> Unit): ByteArray =
    AudioTransmuter().apply(block).transmute(source)

  /** Apply a pre-configured [transmuter] to [source]. */
  suspend fun audio(source: ByteArray, transmuter: AudioTransmuter): ByteArray =
    transmuter.transmute(source)

  /** Create a reusable [VideoTransmuter] with no source bytes. Configure once, apply many times. */
  fun video(): VideoTransmuter = VideoTransmuter()

  /**
   * One-shot convenience: configure via [block] and transmute [source] immediately.
   * Equivalent to `Transmute.video().apply(block).transmute(source)`.
   */
  suspend fun video(source: ByteArray, block: VideoTransmuter.() -> Unit): ByteArray =
    VideoTransmuter().apply(block).transmute(source)

  /** Apply a pre-configured [transmuter] to [source]. */
  suspend fun video(source: ByteArray, transmuter: VideoTransmuter): ByteArray =
    transmuter.transmute(source)

  /**
   * Fully-typed transmutation entry point - lets callers parameterise the
   * media domain without committing to a concrete transmuter class.
   *
   * ```
   * Transmute.transmute(TransmuteType.Image, bytes) { scale(800, 600) }
   * ```
   */
  @Suppress("UNCHECKED_CAST")
  suspend fun <T : Transmuter<*>> transmute(
    type: TransmuteType<T>,
    source: ByteArray,
    block: T.() -> Unit = {},
  ): ByteArray {
    val transmuter: T = when (type) {
      TransmuteType.Image -> ImageTransmuter() as T
      TransmuteType.Audio -> AudioTransmuter() as T
      TransmuteType.Video -> VideoTransmuter() as T
    }
    transmuter.block()
    return transmuter.transmute(source)
  }

  fun detectImageFormat(bytes: ByteArray): ImageFormat = ImageFormatDetector.detect(bytes)
  fun detectAudioFormat(bytes: ByteArray): AudioFormat = AudioFormatDetector.detect(bytes)
  fun detectVideoFormat(bytes: ByteArray): VideoFormat = VideoFormatDetector.detect(bytes)

  /** Auto-detect the media domain and format from raw bytes. */
  fun detectFormat(bytes: ByteArray): MediaFormat {
    val img = ImageFormatDetector.detect(bytes)
    if (img != ImageFormat.UNKNOWN) return img
    val aud = AudioFormatDetector.detect(bytes)
    if (aud != AudioFormat.UNKNOWN) return aud
    val vid = VideoFormatDetector.detect(bytes)
    if (vid != VideoFormat.UNKNOWN) return vid
    return ImageFormat.UNKNOWN
  }
}

// ── ImageTransmuter ──

class ImageTransmuter : Transmuter<ImageTransmuter> {
  val pipeline = TransformPipeline<ImageIR>()
  private var outputFormat: ImageFormat? = null
  // Default to STRIP_ALL to avoid leaking GPS/camera data in shared images.
  private var metadataPolicy: MetadataPolicy = MetadataPolicy.STRIP_ALL
  private var progressCallback: ((Float) -> Unit)? = null
  // 0.85 balances visual quality against file size for most lossy formats.
  private var quality: Float = 0.85f
  private var loggerOverride: ConversionLogger? = null

  /** Override the global logger for this operation only. */
  fun logger(logger: ConversionLogger): ImageTransmuter = apply { loggerOverride = logger }

  /**
   * Direct pipeline access for fine-grained ordering control.
   *
   * ```kotlin
   * Transmute.image(bytes) {
   *   transform {
   *     add(Transformers.image().scale(800, 600))
   *     add(Transformers.image().crop(0, 0, 400, 400))
   *     before<ImageCropTransform>(Transformers.image().rotate())
   *   }
   * }
   * ```
   */
  fun transform(block: TransformPipeline<ImageIR>.() -> Unit): ImageTransmuter = apply {
    pipeline.block()
  }

  fun outputFormat(format: ImageFormat): ImageTransmuter = apply { outputFormat = format }

  /** Quality for lossy formats (e.g. JPEG/WebP). */
  fun quality(value: Float): ImageTransmuter = apply { quality = value.coerceIn(0f, 1f) }

  override fun metadata(policy: MetadataPolicy): ImageTransmuter = apply { metadataPolicy = policy }

  override fun onProgress(callback: (Float) -> Unit): ImageTransmuter = apply { progressCallback = callback }

  /**
   * Returns `true` if this transmuter would produce any change on an image described by [hint].
   *
   * All decisions are conservative: if a hint property is `null` (unknown), the corresponding
   * transform is assumed to apply. Only returns `false` when it can be proven from the hint
   * that every configured transform is a no-op.
   */
  fun wouldAffect(hint: ImageHint): Boolean {
    if (metadataPolicy == MetadataPolicy.STRIP_ALL) return true
    if (outputFormat != null && outputFormat != hint.format) return true
    return pipeline.transforms.any { t ->
      when (t) {
        is ImageScaleTransform -> {
          val w = hint.width; val h = hint.height
          w == null || h == null || w > t.maxWidth || h > t.maxHeight
        }
        is ImageResizeTransform -> {
          val w = hint.width; val h = hint.height
          w == null || h == null || w != t.targetWidth || h != t.targetHeight
        }
        is ImageBrightnessContrastTransform -> t.brightness != 0f || t.contrast != 1f
        is ImageBlurTransform -> t.radius > 0
        is ImageOpacityTransform -> t.opacity != 1f
        is ImageFlipTransform -> t.horizontal || t.vertical
        else -> true // conservative: unknown transforms are assumed to apply
      }
    }
  }

  override suspend fun transmute(source: ByteArray): ByteArray {
    val context = createContext().also {
      it.scratchpad["image.quality"] = quality
    }

    ImageRegistries.installDefaultsIfEmpty()

    val inputFormat = ImageFormatDetector.detect(source)
    val outFormat = outputFormat ?: inputFormat
    context.scratchpad["image.output.format"] = outFormat

    context.onProgress(0.1f)
    val decoder = ImageRegistries.decoders.decoderFor(inputFormat)
      ?: error("No image decoder for $inputFormat")
    var ir = decoder.decode(source, context)

    val steps = pipeline.transforms
    val transformStep = 0.7f / steps.size.coerceAtLeast(1)
    steps.forEachIndexed { index, transform ->
      ir = transform.apply(ir, context)
      context.onProgress(0.1f + (index + 1) * transformStep)
    }

    ir = ImageMetadataTransform(metadataPolicy).apply(ir, context)

    context.onProgress(0.9f)
    val encoder = ImageRegistries.encoders.encoderFor(outFormat)
      ?: error("No image encoder for $outFormat")
    val result = encoder.encode(ir, context)
    context.onProgress(1f)
    return result
  }

  override suspend fun transmuteInto(source: ByteArray, buffer: ByteArray, offset: Int): Int {
    val bytes = transmute(source)
    require(offset >= 0 && offset <= buffer.size) { "offset out of bounds" }
    require(bytes.size <= buffer.size - offset) {
      "buffer too small: need ${bytes.size}, have ${buffer.size - offset}"
    }
    bytes.copyInto(buffer, destinationOffset = offset)
    return bytes.size
  }

  @OptIn(ExperimentalUuidApi::class)
  private fun createContext() = ConversionContext(
    jobId = Uuid.random().toString(),
    coroutineJob = null,
    metadataPolicy = metadataPolicy,
    onProgress = progressCallback ?: {},
    logger = loggerOverride ?: TransmuteLogging.logger,
    scratchpad = mutableMapOf(),
    timeBudgetMs = Long.MAX_VALUE,
    memoryBudgetBytes = Long.MAX_VALUE,
  )
}

// ── AudioTransmuter ──

class AudioTransmuter : Transmuter<AudioTransmuter> {
  val pipeline = TransformPipeline<AudioIR>()
  private var outputFormat: AudioFormat? = null
  private var metadataPolicy: MetadataPolicy = MetadataPolicy.STRIP_ALL
  private var progressCallback: ((Float) -> Unit)? = null
  private var loggerOverride: ConversionLogger? = null

  /** Override the global logger for this operation only. */
  fun logger(logger: ConversionLogger): AudioTransmuter = apply { loggerOverride = logger }

  /**
   * Direct pipeline access for fine-grained ordering control.
   *
   * ```kotlin
   * Transmute.audio(bytes) {
   *   transform {
   *     add(Transformers.audio().normalize())
   *     add(Transformers.audio().trim(1000, 5000))
   *     before<AudioTrimTransform>(Transformers.audio().fade(fadeInMs = 200))
   *   }
   * }
   * ```
   */
  fun transform(block: TransformPipeline<AudioIR>.() -> Unit): AudioTransmuter = apply {
    pipeline.block()
  }

  fun outputFormat(format: AudioFormat): AudioTransmuter = apply { outputFormat = format }

  override fun metadata(policy: MetadataPolicy): AudioTransmuter = apply { metadataPolicy = policy }

  override fun onProgress(callback: (Float) -> Unit): AudioTransmuter = apply { progressCallback = callback }

  /**
   * Returns `true` if this transmuter would produce any change on an audio track described by [hint].
   *
   * All decisions are conservative: if a hint property is `null` (unknown), the corresponding
   * transform is assumed to apply. Only returns `false` when it can be proven from the hint
   * that every configured transform is a no-op.
   */
  fun wouldAffect(hint: AudioHint): Boolean {
    if (metadataPolicy == MetadataPolicy.STRIP_ALL) return true
    if (outputFormat != null && outputFormat != hint.format) return true
    return pipeline.transforms.any { t ->
      when (t) {
        is AudioResampleTransform ->
          hint.sampleRate == null || hint.sampleRate != t.targetSampleRate
        is AudioSpeedTransform -> t.speed != 1f
        is AudioGainTransform -> t.gainDb != 0f
        is AudioFadeTransform -> t.fadeInMs > 0 || t.fadeOutMs > 0
        is AudioCompressorTransform -> t.ratio > 1f
        is AudioChannelMapTransform ->
          hint.channelCount == null ||
            t.mapping.size != hint.channelCount ||
            t.mapping.indices.any { t.mapping[it] != it }
        else -> true // conservative
      }
    }
  }

  override suspend fun transmute(source: ByteArray): ByteArray {
    val context = createContext()

    AudioRegistries.installDefaultsIfEmpty()
    val decoderRegistry = AudioRegistries.decoders
    val encoderRegistry = AudioRegistries.encoders

    val inputFormat = AudioFormatDetector.detect(source)
    // Fall back to WAV when no encoder exists for the input format,
    // since WAV is the only format with a cross-platform pure-Kotlin encoder.
    val outFormat = outputFormat ?: run {
      if (encoderRegistry.encoderFor(inputFormat) != null) inputFormat else AudioFormat.WAV
    }

    context.onProgress(0.1f)
    val decoder = decoderRegistry.decoderFor(inputFormat)
      ?: error("No audio decoder for $inputFormat")
    var ir = decoder.decode(source, context)

    val steps = pipeline.transforms
    val transformStep = 0.7f / steps.size.coerceAtLeast(1)
    steps.forEachIndexed { index, transform ->
      ir = transform.apply(ir, context)
      context.onProgress(0.1f + (index + 1) * transformStep)
    }

    ir = AudioMetadataTransform(metadataPolicy).apply(ir, context)

    context.onProgress(0.9f)
    val encoder = encoderRegistry.encoderFor(outFormat)
      ?: error("No audio encoder for $outFormat")
    val result = encoder.encode(ir, context)
    context.onProgress(1f)
    return result
  }

  override suspend fun transmuteInto(source: ByteArray, buffer: ByteArray, offset: Int): Int {
    val bytes = transmute(source)
    require(offset >= 0 && offset <= buffer.size) { "offset out of bounds" }
    require(bytes.size <= buffer.size - offset) {
      "buffer too small: need ${bytes.size}, have ${buffer.size - offset}"
    }
    bytes.copyInto(buffer, destinationOffset = offset)
    return bytes.size
  }

  @OptIn(ExperimentalUuidApi::class)
  private fun createContext() = ConversionContext(
    jobId = Uuid.random().toString(),
    coroutineJob = null,
    metadataPolicy = metadataPolicy,
    onProgress = progressCallback ?: {},
    logger = loggerOverride ?: TransmuteLogging.logger,
    scratchpad = mutableMapOf(),
    timeBudgetMs = Long.MAX_VALUE,
    memoryBudgetBytes = Long.MAX_VALUE,
  )
}

// ── VideoTransmuter ──

class VideoTransmuter : Transmuter<VideoTransmuter> {
  val pipeline = TransformPipeline<VideoIR>()
  private var outputFormat: VideoFormat? = null
  private var metadataPolicy: MetadataPolicy = MetadataPolicy.STRIP_ALL
  private var progressCallback: ((Float) -> Unit)? = null
  private var loggerOverride: ConversionLogger? = null

  /** Override the global logger for this operation only. */
  fun logger(logger: ConversionLogger): VideoTransmuter = apply { loggerOverride = logger }

  /**
   * Direct pipeline access for fine-grained ordering control.
   *
   * ```kotlin
   * Transmute.video(bytes) {
   *   transform {
   *     add(Transformers.video().resize(640, 480))
   *     before<VideoResizeTransform>(Transformers.video().trim(0, 5000))
   *   }
   * }
   * ```
   */
  fun transform(block: TransformPipeline<VideoIR>.() -> Unit): VideoTransmuter = apply {
    pipeline.block()
  }

  fun outputFormat(format: VideoFormat): VideoTransmuter = apply { outputFormat = format }

  override fun metadata(policy: MetadataPolicy): VideoTransmuter = apply { metadataPolicy = policy }

  override fun onProgress(callback: (Float) -> Unit): VideoTransmuter = apply { progressCallback = callback }

  /**
   * Returns `true` if this transmuter would produce any change on a video described by [hint].
   *
   * All decisions are conservative: if a hint property is `null` (unknown), the corresponding
   * transform is assumed to apply. Only returns `false` when it can be proven from the hint
   * that every configured transform is a no-op.
   */
  fun wouldAffect(hint: VideoHint): Boolean {
    if (metadataPolicy == MetadataPolicy.STRIP_ALL) return true
    if (outputFormat != null && outputFormat != hint.format) return true
    return pipeline.transforms.any { t ->
      when (t) {
        is VideoResizeTransform -> {
          val w = hint.width; val h = hint.height
          w == null || h == null || w > t.maxWidth || h > t.maxHeight
        }
        is VideoFrameRateTransform -> {
          val fps = hint.fps
          fps == null || fps > t.targetFps
        }
        is VideoSpeedTransform -> t.speed != 1f
        is VideoRemoveAudioTransform -> true // always strips audio track
        else -> true // conservative
      }
    }
  }

  override suspend fun transmute(source: ByteArray): ByteArray {
    val context = createContext()
    VideoRegistries.installDefaultsIfEmpty()
    val inputFormat = VideoFormatDetector.detect(source)
    val outFormat = outputFormat ?: inputFormat

    context.onProgress(0.1f)
    val decoder = VideoRegistries.decoders.decoderFor(inputFormat)
      ?: error("No video decoder for $inputFormat. Register a platform decoder.")
    var ir = decoder.decode(source, context)

    val steps = pipeline.transforms
    val transformStep = 0.7f / steps.size.coerceAtLeast(1)
    steps.forEachIndexed { index, transform ->
      ir = transform.apply(ir, context)
      context.onProgress(0.1f + (index + 1) * transformStep)
    }

    ir = VideoMetadataTransform(metadataPolicy).apply(ir, context)

    context.onProgress(0.9f)
    val encoder = VideoRegistries.encoders.encoderFor(outFormat)
      ?: error("No video encoder for $outFormat. Register a platform encoder.")
    val result = encoder.encode(ir, context)
    context.onProgress(1f)
    return result
  }

  override suspend fun transmuteInto(source: ByteArray, buffer: ByteArray, offset: Int): Int {
    val bytes = transmute(source)
    require(offset >= 0 && offset <= buffer.size) { "offset out of bounds" }
    require(bytes.size <= buffer.size - offset) {
      "buffer too small: need ${bytes.size}, have ${buffer.size - offset}"
    }
    bytes.copyInto(buffer, destinationOffset = offset)
    return bytes.size
  }

  @OptIn(ExperimentalUuidApi::class)
  private fun createContext() = ConversionContext(
    jobId = Uuid.random().toString(),
    coroutineJob = null,
    metadataPolicy = metadataPolicy,
    onProgress = progressCallback ?: {},
    logger = loggerOverride ?: TransmuteLogging.logger,
    scratchpad = mutableMapOf(),
    timeBudgetMs = Long.MAX_VALUE,
    memoryBudgetBytes = Long.MAX_VALUE,
  )
}
