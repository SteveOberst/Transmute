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

  /** Run the pipeline and return the encoded bytes. */
  suspend fun transmute(): ByteArray

  /**
   * Run the pipeline and write the result into [buffer] at [offset].
   *
   * Useful for avoiding an extra copy when the caller already owns a buffer
   * (e.g. writing into a memory-mapped file or a pre-allocated NIO ByteBuffer).
   *
   * @return The number of bytes written.
   */
  suspend fun transmuteInto(buffer: ByteArray, offset: Int = 0): Int

}

// ── Transmute facade ──

/**
 * Public API facade for Transmute.
 *
 * Three ways to use it:
 *
 * ```kotlin
 * // 1. Builder-chain
 * val bytes = Transmute.image(myBytes)
 *   .scale(800, 600)
 *   .outputFormat(ImageFormat.WEBP)
 *   .transmute()
 *
 * // 2. DSL block (returns ByteArray directly)
 * val bytes = Transmute.image(myBytes) {
 *   scale(800, 600)
 *   outputFormat(ImageFormat.WEBP)
 * }
 *
 * // 3. Typed dispatch
 * val bytes = Transmute.transmute(TransmuteType.Image, myBytes) {
 *   scale(800, 600)
 * }
 * ```
 */
object Transmute {

  fun image(source: ByteArray): ImageTransmuter = ImageTransmuter(source)
  suspend fun image(source: ByteArray, block: ImageTransmuter.() -> Unit): ByteArray =
    ImageTransmuter(source).apply(block).transmute()

  fun audio(source: ByteArray): AudioTransmuter = AudioTransmuter(source)
  suspend fun audio(source: ByteArray, block: AudioTransmuter.() -> Unit): ByteArray =
    AudioTransmuter(source).apply(block).transmute()

  fun video(source: ByteArray): VideoTransmuter = VideoTransmuter(source)
  suspend fun video(source: ByteArray, block: VideoTransmuter.() -> Unit): ByteArray =
    VideoTransmuter(source).apply(block).transmute()

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
      TransmuteType.Image -> ImageTransmuter(source) as T
      TransmuteType.Audio -> AudioTransmuter(source) as T
      TransmuteType.Video -> VideoTransmuter(source) as T
    }
    transmuter.block()
    return transmuter.transmute()
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

class ImageTransmuter(private val source: ByteArray) : Transmuter<ImageTransmuter> {
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

  override suspend fun transmute(): ByteArray {
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

  override suspend fun transmuteInto(buffer: ByteArray, offset: Int): Int {
    val bytes = transmute()
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

class AudioTransmuter(private val source: ByteArray) : Transmuter<AudioTransmuter> {
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

  override suspend fun transmute(): ByteArray {
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

  override suspend fun transmuteInto(buffer: ByteArray, offset: Int): Int {
    val bytes = transmute()
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

class VideoTransmuter(private val source: ByteArray) : Transmuter<VideoTransmuter> {
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

  override suspend fun transmute(): ByteArray {
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

  override suspend fun transmuteInto(buffer: ByteArray, offset: Int): Int {
    val bytes = transmute()
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
