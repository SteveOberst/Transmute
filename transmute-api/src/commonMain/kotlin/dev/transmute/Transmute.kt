package dev.transmute

import dev.transmute.audio.AudioFormatDetector
import dev.transmute.audio.AudioHint
import dev.transmute.audio.AudioIR
import dev.transmute.audio.AudioDecodeOptions
import dev.transmute.audio.AudioEncodeOptions
import dev.transmute.audio.AudioRegistries
import dev.transmute.audio.AudioTransform
import dev.transmute.audio.CanonicalAudioDecodeOptions
import dev.transmute.audio.CanonicalAudioEncodeOptions
import dev.transmute.audio.AudioDecodeHandler
import dev.transmute.audio.AudioDynamicEncodeHandler
import dev.transmute.audio.AudioFixedEncodeHandler
import dev.transmute.core.AnyFormatTag
import dev.transmute.core.AudioFormat
import dev.transmute.core.AudioFormatTag
import dev.transmute.core.DecodeOptions
import dev.transmute.core.EncodeOptions
import dev.transmute.core.TransmuteContext
import dev.transmute.core.TransmuteLogger
import dev.transmute.core.FormatTag
import dev.transmute.core.ImageFormat
import dev.transmute.core.ImageFormatTag
import dev.transmute.core.MediaFormat
import dev.transmute.core.TransmuteLogging
import dev.transmute.core.VideoFormat
import dev.transmute.core.VideoFormatTag
import dev.transmute.core.pipeline.DecodePipeline
import dev.transmute.core.pipeline.Decoded
import dev.transmute.core.pipeline.EncodePipeline
import dev.transmute.core.pipeline.EncodedBytes
import dev.transmute.core.pipeline.PipelineBuilder
import dev.transmute.core.pipeline.Transform
import dev.transmute.core.pipeline.TransformPipeline
import dev.transmute.image.CanonicalImageDecodeOptions
import dev.transmute.image.CanonicalImageEncodeOptions
import dev.transmute.image.ImageDecodeOptions
import dev.transmute.image.ImageEncodeOptions
import dev.transmute.image.ImageFormatDetector
import dev.transmute.image.ImageHint
import dev.transmute.image.ImageIR
import dev.transmute.image.ImageDecodeHandler
import dev.transmute.image.ImageDynamicEncodeHandler
import dev.transmute.image.ImageFixedEncodeHandler
import dev.transmute.image.ImageRegistries
import dev.transmute.image.ImageTransform
import dev.transmute.image.JpegEncodeOptions
import dev.transmute.video.CanonicalVideoDecodeOptions
import dev.transmute.video.CanonicalVideoEncodeOptions
import dev.transmute.video.VideoDecodeOptions
import dev.transmute.video.VideoEncodeOptions
import dev.transmute.video.VideoFormatDetector
import dev.transmute.video.VideoHint
import dev.transmute.video.VideoIR
import dev.transmute.video.VideoDecodeHandler
import dev.transmute.video.VideoDynamicEncodeHandler
import dev.transmute.video.VideoFixedEncodeHandler
import dev.transmute.video.VideoRegistries
import dev.transmute.video.VideoTransform

typealias DynamicImageTransmuter = ImageTransmuter<ByteArray, AnyFormatTag<ImageFormat>>
typealias DynamicAudioTransmuter = AudioTransmuter<ByteArray, AnyFormatTag<AudioFormat>>
typealias DynamicVideoTransmuter = VideoTransmuter<ByteArray, AnyFormatTag<VideoFormat>>

/**
 * Discriminates the three media domains at the type level.
 *
 * This dispatch type is for the *dynamic-output* transmuters.
 * If you want a type-level output format (e.g. PNG-only post-encode handlers),
 * build via `Transmute.imageTo(ImageFormatTag.Png) { ... }` instead.
 */
sealed class TransmuteType {
  data object Image : TransmuteType()
  data object Audio : TransmuteType()
  data object Video : TransmuteType()
}

/** Immutable, reusable conversion executor built by a builder DSL. */
interface Transmuter<IN, F : MediaFormat, OUT : FormatTag<F>> {
  /** Convert [source] into encoded bytes tagged with the resolved output format. */
  suspend fun transmute(source: IN): EncodedBytes<F, OUT>

  /** Convert [source] and copy the resulting bytes into [buffer]. Returns bytes written. */
  suspend fun transmute(source: IN, buffer: ByteArray, offset: Int = 0): Int {
    val bytes = transmute(source).bytes
    require(offset >= 0 && offset <= buffer.size) { "offset out of bounds" }
    require(bytes.size <= buffer.size - offset) {
      "buffer too small: need ${bytes.size}, have ${buffer.size - offset}"
    }
    bytes.copyInto(buffer, destinationOffset = offset)
    return bytes.size
  }
}

/** Public API facade for Transmute. */
object Transmute {

  // ---- Image ----

  /** Build a dynamic-output image transmuter (output defaults to input format). */
  fun image(block: DynamicImageTransmuterBuilder<ByteArray>.() -> Unit = {}): DynamicImageTransmuter =
    DynamicImageTransmuterBuilder(defaultDecodePipeline = ::defaultImageByteArrayDecodePipeline).apply(block).build()

  /** Build a dynamic-output image transmuter with an explicit input type. */
  fun <IN> imageFrom(block: DynamicImageTransmuterBuilder<IN>.() -> Unit = {}): ImageTransmuter<IN, AnyFormatTag<ImageFormat>> =
    DynamicImageTransmuterBuilder<IN>().apply(block).build()

  /** Build a fixed-output image transmuter with a type-level output tag. */
  fun <OUT : ImageFormatTag> imageTo(output: OUT, block: ImageTransmuterBuilder<ByteArray, OUT>.() -> Unit = {}): ImageTransmuter<ByteArray, OUT> =
    ImageTransmuterBuilder(output, defaultDecodePipeline = ::defaultImageByteArrayDecodePipeline).apply(block).build()

  /** Build a fixed-output image transmuter with an explicit input type. */
  fun <IN, OUT : ImageFormatTag> imageToFrom(
    output: OUT,
    block: ImageTransmuterBuilder<IN, OUT>.() -> Unit = {},
  ): ImageTransmuter<IN, OUT> = ImageTransmuterBuilder<IN, OUT>(output).apply(block).build()

  // ---- Audio ----

  fun audio(block: DynamicAudioTransmuterBuilder<ByteArray>.() -> Unit = {}): DynamicAudioTransmuter =
    DynamicAudioTransmuterBuilder(defaultDecodePipeline = ::defaultAudioByteArrayDecodePipeline).apply(block).build()

  fun <IN> audioFrom(block: DynamicAudioTransmuterBuilder<IN>.() -> Unit = {}): AudioTransmuter<IN, AnyFormatTag<AudioFormat>> =
    DynamicAudioTransmuterBuilder<IN>().apply(block).build()

  fun <OUT : AudioFormatTag> audioTo(output: OUT, block: AudioTransmuterBuilder<ByteArray, OUT>.() -> Unit = {}): AudioTransmuter<ByteArray, OUT> =
    AudioTransmuterBuilder(output, defaultDecodePipeline = ::defaultAudioByteArrayDecodePipeline).apply(block).build()

  fun <IN, OUT : AudioFormatTag> audioToFrom(
    output: OUT,
    block: AudioTransmuterBuilder<IN, OUT>.() -> Unit = {},
  ): AudioTransmuter<IN, OUT> = AudioTransmuterBuilder<IN, OUT>(output).apply(block).build()

  // ---- Video ----

  fun video(block: DynamicVideoTransmuterBuilder<ByteArray>.() -> Unit = {}): DynamicVideoTransmuter =
    DynamicVideoTransmuterBuilder(defaultDecodePipeline = ::defaultVideoByteArrayDecodePipeline).apply(block).build()

  fun <IN> videoFrom(block: DynamicVideoTransmuterBuilder<IN>.() -> Unit = {}): VideoTransmuter<IN, AnyFormatTag<VideoFormat>> =
    DynamicVideoTransmuterBuilder<IN>().apply(block).build()

  fun <OUT : VideoFormatTag> videoTo(output: OUT, block: VideoTransmuterBuilder<ByteArray, OUT>.() -> Unit = {}): VideoTransmuter<ByteArray, OUT> =
    VideoTransmuterBuilder(output, defaultDecodePipeline = ::defaultVideoByteArrayDecodePipeline).apply(block).build()

  fun <IN, OUT : VideoFormatTag> videoToFrom(
    output: OUT,
    block: VideoTransmuterBuilder<IN, OUT>.() -> Unit = {},
  ): VideoTransmuter<IN, OUT> = VideoTransmuterBuilder<IN, OUT>(output).apply(block).build()

  // ---- Typed dispatch ----

  suspend fun transmute(type: TransmuteType, source: ByteArray): ByteArray = when (type) {
    TransmuteType.Image -> image().transmute(source).bytes
    TransmuteType.Audio -> audio().transmute(source).bytes
    TransmuteType.Video -> video().transmute(source).bytes
  }

  fun detectImageFormat(bytes: ByteArray): ImageFormat = ImageFormatDetector.detect(bytes)
  fun detectAudioFormat(bytes: ByteArray): AudioFormat = AudioFormatDetector.detect(bytes)
  fun detectVideoFormat(bytes: ByteArray): VideoFormat = VideoFormatDetector.detect(bytes)

  /** Auto-detect the media domain and format from raw bytes. */
  fun detectFormat(bytes: ByteArray): MediaFormat {
    // ISO BMFF containers (MP4/MOV/M4A/HEIF/HEIC/AVIF) are ambiguous across domains.
    // Handle them explicitly first to avoid cross-domain misclassification.
    if (isBmff(bytes)) {
      val img = ImageFormatDetector.detect(bytes)
      if (img != ImageFormat.UNKNOWN) return img

      val brand = bmffMajorBrand(bytes)
      if (brand == "qt  ") return VideoFormat.MOV

      val hasVideo = bmffHasVideoTrack(bytes)
      val hasAudio = bmffHasAudioTrack(bytes)
      if (hasVideo) return VideoFormat.MP4
      if (hasAudio) return AudioFormat.M4A
      // Fall through to other detectors.
    }

    val img = ImageFormatDetector.detect(bytes)
    if (img != ImageFormat.UNKNOWN) return img

    val vid = VideoFormatDetector.detect(bytes)
    if (vid != VideoFormat.UNKNOWN) return vid

    val aud = AudioFormatDetector.detect(bytes)
    if (aud != AudioFormat.UNKNOWN) return aud

    return dev.transmute.core.UnknownFormat
  }
}

// -- Image ------------------------------------------------------------------

private fun isBmff(bytes: ByteArray): Boolean =
  bytes.size >= 8 &&
    bytes[4] == 0x66.toByte() && bytes[5] == 0x74.toByte() &&
    bytes[6] == 0x79.toByte() && bytes[7] == 0x70.toByte()

private fun bmffMajorBrand(bytes: ByteArray): String? {
  if (!isBmff(bytes) || bytes.size < 12) return null
  return (8 until 12).map { bytes[it].toInt().toChar() }.joinToString("")
}

private fun bmffHasVideoTrack(bytes: ByteArray): Boolean {
  val max = 256 * 1024
  return containsAscii(bytes, "vide", max) || containsAscii(bytes, "avc1", max) || containsAscii(bytes, "hvc1", max)
}

private fun bmffHasAudioTrack(bytes: ByteArray): Boolean {
  val max = 256 * 1024
  return containsAscii(bytes, "soun", max) || containsAscii(bytes, "mp4a", max)
}

private fun containsAscii(bytes: ByteArray, needleAscii: String, maxBytes: Int): Boolean {
  val needle = needleAscii.encodeToByteArray()
  val limit = minOf(bytes.size, maxBytes)
  if (needle.isEmpty() || limit < needle.size) return false

  outer@ for (i in 0..(limit - needle.size)) {
    for (j in needle.indices) {
      if (bytes[i + j] != needle[j]) continue@outer
    }
    return true
  }
  return false
}

class DynamicImageTransmuterBuilder<IN> internal constructor(
  private val defaultDecodePipeline: (() -> DecodePipeline<IN, Decoded<ImageFormat, ImageIR>>)? = null,
) {
  private val transformPipeline = TransformPipeline<ImageIR>()
  private var loggerOverride: TransmuteLogger? = null
  private var decodeOptions: ImageDecodeOptions = CanonicalImageDecodeOptions()
  private var encodeOptions: ImageEncodeOptions = CanonicalImageEncodeOptions()
  private var decodePipeline: DecodePipeline<IN, Decoded<ImageFormat, ImageIR>>? = null
  private var encodePipeline: EncodePipeline<Decoded<ImageFormat, ImageIR>, EncodedBytes<ImageFormat, AnyFormatTag<ImageFormat>>>? = null

  fun logger(logger: TransmuteLogger): DynamicImageTransmuterBuilder<IN> = apply { loggerOverride = logger }

  fun decodeOptions(options: ImageDecodeOptions): DynamicImageTransmuterBuilder<IN> = apply {
    decodeOptions = options
  }

  fun encodeOptions(options: ImageEncodeOptions): DynamicImageTransmuterBuilder<IN> = apply {
    encodeOptions = options
  }

  fun transform(block: TransformPipeline<ImageIR>.() -> Unit): DynamicImageTransmuterBuilder<IN> = apply {
    transformPipeline.block()
  }

  /**
   * Configure the decode pipeline (ByteArray → decode → ImageIR).
   *
   * The decode stage is explicit (`decode()` or `decodeWith { ... }`), and the builder enforces
   * the `ByteArray -> IR` type transition.
   */
  fun decode(
    block: PipelineBuilder<IN, IN>.() -> PipelineBuilder<IN, Decoded<ImageFormat, ImageIR>>,
  ): DynamicImageTransmuterBuilder<IN> = apply {
    decodePipeline = PipelineBuilder.start<IN>().block().build()
  }

  /**
   * Configure the encode pipeline (ImageIR → encode → EncodedBytes).
   *
   * For dynamic output, post-encode handlers receive `AnyFormatTag<ImageFormat>` (runtime-only).
   */
  fun encode(
    block: PipelineBuilder<Decoded<ImageFormat, ImageIR>, Decoded<ImageFormat, ImageIR>>.() ->
      PipelineBuilder<Decoded<ImageFormat, ImageIR>, EncodedBytes<ImageFormat, AnyFormatTag<ImageFormat>>>,
  ): DynamicImageTransmuterBuilder<IN> = apply {
    encodePipeline = PipelineBuilder.start<Decoded<ImageFormat, ImageIR>>().block().build()
  }

  fun build(): ImageTransmuter<IN, AnyFormatTag<ImageFormat>> {
    val decode = decodePipeline
      ?: defaultDecodePipeline?.invoke()
      ?: error("No decode pipeline configured; call decode { ... }")
    val encode = encodePipeline ?: defaultDynamicImageEncodePipeline()

    return ImageTransmuter(
      loggerOverride = loggerOverride,
      transforms = transformPipeline.transforms,
      decodePipeline = decode,
      encodePipeline = encode,
      decodeOptions = decodeOptions,
      encodeOptions = encodeOptions,
    )
  }

  private fun defaultDynamicImageEncodePipeline(): EncodePipeline<Decoded<ImageFormat, ImageIR>, EncodedBytes<ImageFormat, AnyFormatTag<ImageFormat>>> =
    PipelineBuilder.start<Decoded<ImageFormat, ImageIR>>()
      .then(ImageDynamicEncodeHandler())
      .build()
}

class ImageTransmuterBuilder<IN, OUT : ImageFormatTag> internal constructor(
  private val output: OUT,
  private val defaultDecodePipeline: (() -> DecodePipeline<IN, Decoded<ImageFormat, ImageIR>>)? = null,
) {
  private val transformPipeline = TransformPipeline<ImageIR>()
  private var loggerOverride: TransmuteLogger? = null
  private var decodeOptions: ImageDecodeOptions = CanonicalImageDecodeOptions()
  private var encodeOptions: ImageEncodeOptions = CanonicalImageEncodeOptions()
  private var decodePipeline: DecodePipeline<IN, Decoded<ImageFormat, ImageIR>>? = null
  private var encodePipeline: EncodePipeline<Decoded<ImageFormat, ImageIR>, EncodedBytes<ImageFormat, OUT>>? = null

  fun logger(logger: TransmuteLogger): ImageTransmuterBuilder<IN, OUT> = apply { loggerOverride = logger }

  fun decodeOptions(options: ImageDecodeOptions): ImageTransmuterBuilder<IN, OUT> = apply {
    decodeOptions = options
  }

  /** Quality for JPEG encoding only (you probably want ImageFormatTag.Jpeg). */
  fun quality(value: Float): ImageTransmuterBuilder<IN, OUT> = apply {
    encodeOptions = JpegEncodeOptions(quality = value.coerceIn(0f, 1f))
  }

  fun encodeOptions(options: ImageEncodeOptions): ImageTransmuterBuilder<IN, OUT> = apply {
    encodeOptions = options
  }

  fun transform(block: TransformPipeline<ImageIR>.() -> Unit): ImageTransmuterBuilder<IN, OUT> = apply {
    transformPipeline.block()
  }

  fun decode(
    block: PipelineBuilder<IN, IN>.() -> PipelineBuilder<IN, Decoded<ImageFormat, ImageIR>>,
  ): ImageTransmuterBuilder<IN, OUT> = apply {
    decodePipeline = PipelineBuilder.start<IN>().block().build()
  }

  /** Fixed output enables type-safe post-encode handlers via the `OUT` tag type. */
  fun encode(
    block: PipelineBuilder<Decoded<ImageFormat, ImageIR>, Decoded<ImageFormat, ImageIR>>.() ->
      PipelineBuilder<Decoded<ImageFormat, ImageIR>, EncodedBytes<ImageFormat, OUT>>,
  ): ImageTransmuterBuilder<IN, OUT> = apply {
    encodePipeline = PipelineBuilder.start<Decoded<ImageFormat, ImageIR>>().block().build()
  }

  fun build(): ImageTransmuter<IN, OUT> {
    val decode = decodePipeline
      ?: defaultDecodePipeline?.invoke()
      ?: error("No decode pipeline configured; call decode { ... }")
    val encode = encodePipeline ?: defaultFixedImageEncodePipeline()

    return ImageTransmuter(
      loggerOverride = loggerOverride,
      transforms = transformPipeline.transforms,
      decodePipeline = decode,
      encodePipeline = encode,
      decodeOptions = decodeOptions,
      encodeOptions = encodeOptions,
    )
  }

  private fun defaultFixedImageEncodePipeline(): EncodePipeline<Decoded<ImageFormat, ImageIR>, EncodedBytes<ImageFormat, OUT>> =
    PipelineBuilder.start<Decoded<ImageFormat, ImageIR>>()
      .then(ImageFixedEncodeHandler(output))
      .build()
}

class ImageTransmuter<IN, OUT : FormatTag<ImageFormat>> internal constructor(
    private val loggerOverride: TransmuteLogger?,
    private val transforms: List<Transform<ImageIR>>,
    private val decodePipeline: DecodePipeline<IN, Decoded<ImageFormat, ImageIR>>,
    private val encodePipeline: EncodePipeline<Decoded<ImageFormat, ImageIR>, EncodedBytes<ImageFormat, OUT>>,
    private val decodeOptions: ImageDecodeOptions,
    private val encodeOptions: ImageEncodeOptions,
) : Transmuter<IN, ImageFormat, OUT> {

  fun wouldTransmute(hint: ImageHint): Boolean {
    return transforms.any { (it as? ImageTransform)?.wouldTransform(hint) ?: true }
  }

  override suspend fun transmute(source: IN): EncodedBytes<ImageFormat, OUT> {
    val context = createContext(
      loggerOverride = loggerOverride,
      decodeOptions = decodeOptions,
      encodeOptions = encodeOptions,
    )
    ImageRegistries.installDefaultsIfEmpty()

    val decoded = decodePipeline.run(source, context)
    val inputFormat = decoded.format
    var ir = decoded.ir

    val steps = transforms
    steps.forEachIndexed { index, transform ->
      ir = transform.apply(ir, context)
    }

    val encoded = encodePipeline.run(Decoded(inputFormat, ir), context)
    return encoded
  }
}

// -- Audio ------------------------------------------------------------------─

class DynamicAudioTransmuterBuilder<IN> internal constructor(
  private val defaultDecodePipeline: (() -> DecodePipeline<IN, Decoded<AudioFormat, AudioIR>>)? = null,
) {
  private val transformPipeline = TransformPipeline<AudioIR>()
  private var loggerOverride: TransmuteLogger? = null
  private var decodeOptions: AudioDecodeOptions = CanonicalAudioDecodeOptions()
  private var encodeOptions: AudioEncodeOptions = CanonicalAudioEncodeOptions()
  private var decodePipeline: DecodePipeline<IN, Decoded<AudioFormat, AudioIR>>? = null
  private var encodePipeline: EncodePipeline<Decoded<AudioFormat, AudioIR>, EncodedBytes<AudioFormat, AnyFormatTag<AudioFormat>>>? = null

  fun logger(logger: TransmuteLogger): DynamicAudioTransmuterBuilder<IN> = apply { loggerOverride = logger }

  fun decodeOptions(options: AudioDecodeOptions): DynamicAudioTransmuterBuilder<IN> = apply {
    decodeOptions = options
  }

  fun encodeOptions(options: AudioEncodeOptions): DynamicAudioTransmuterBuilder<IN> = apply {
    encodeOptions = options
  }

  fun transform(block: TransformPipeline<AudioIR>.() -> Unit): DynamicAudioTransmuterBuilder<IN> = apply {
    transformPipeline.block()
  }

  fun decode(
    block: PipelineBuilder<IN, IN>.() -> PipelineBuilder<IN, Decoded<AudioFormat, AudioIR>>,
  ): DynamicAudioTransmuterBuilder<IN> = apply {
    decodePipeline = PipelineBuilder.start<IN>().block().build()
  }

  fun encode(
    block: PipelineBuilder<Decoded<AudioFormat, AudioIR>, Decoded<AudioFormat, AudioIR>>.() ->
      PipelineBuilder<Decoded<AudioFormat, AudioIR>, EncodedBytes<AudioFormat, AnyFormatTag<AudioFormat>>>,
  ): DynamicAudioTransmuterBuilder<IN> = apply {
    encodePipeline = PipelineBuilder.start<Decoded<AudioFormat, AudioIR>>().block().build()
  }

  fun build(): AudioTransmuter<IN, AnyFormatTag<AudioFormat>> {
    val decode = decodePipeline
      ?: defaultDecodePipeline?.invoke()
      ?: error("No decode pipeline configured; call decode { ... }")
    val encode = encodePipeline ?: defaultDynamicAudioEncodePipeline()

    return AudioTransmuter(
      loggerOverride = loggerOverride,
      transforms = transformPipeline.transforms,
      decodePipeline = decode,
      encodePipeline = encode,
      decodeOptions = decodeOptions,
      encodeOptions = encodeOptions,
    )
  }

  private fun defaultDynamicAudioEncodePipeline(): EncodePipeline<Decoded<AudioFormat, AudioIR>, EncodedBytes<AudioFormat, AnyFormatTag<AudioFormat>>> =
    PipelineBuilder.start<Decoded<AudioFormat, AudioIR>>()
      .then(AudioDynamicEncodeHandler())
      .build()
}

class AudioTransmuterBuilder<IN, OUT : AudioFormatTag> internal constructor(
  private val output: OUT,
  private val defaultDecodePipeline: (() -> DecodePipeline<IN, Decoded<AudioFormat, AudioIR>>)? = null,
) {
  private val transformPipeline = TransformPipeline<AudioIR>()
  private var loggerOverride: TransmuteLogger? = null
  private var decodeOptions: AudioDecodeOptions = CanonicalAudioDecodeOptions()
  private var encodeOptions: AudioEncodeOptions = CanonicalAudioEncodeOptions()
  private var decodePipeline: DecodePipeline<IN, Decoded<AudioFormat, AudioIR>>? = null
  private var encodePipeline: EncodePipeline<Decoded<AudioFormat, AudioIR>, EncodedBytes<AudioFormat, OUT>>? = null

  fun logger(logger: TransmuteLogger): AudioTransmuterBuilder<IN, OUT> = apply { loggerOverride = logger }

  fun decodeOptions(options: AudioDecodeOptions): AudioTransmuterBuilder<IN, OUT> = apply {
    decodeOptions = options
  }

  fun encodeOptions(options: AudioEncodeOptions): AudioTransmuterBuilder<IN, OUT> = apply {
    encodeOptions = options
  }

  fun transform(block: TransformPipeline<AudioIR>.() -> Unit): AudioTransmuterBuilder<IN, OUT> = apply {
    transformPipeline.block()
  }

  fun decode(
    block: PipelineBuilder<IN, IN>.() -> PipelineBuilder<IN, Decoded<AudioFormat, AudioIR>>,
  ): AudioTransmuterBuilder<IN, OUT> = apply {
    decodePipeline = PipelineBuilder.start<IN>().block().build()
  }

  fun encode(
    block: PipelineBuilder<Decoded<AudioFormat, AudioIR>, Decoded<AudioFormat, AudioIR>>.() ->
      PipelineBuilder<Decoded<AudioFormat, AudioIR>, EncodedBytes<AudioFormat, OUT>>,
  ): AudioTransmuterBuilder<IN, OUT> = apply {
    encodePipeline = PipelineBuilder.start<Decoded<AudioFormat, AudioIR>>().block().build()
  }

  fun build(): AudioTransmuter<IN, OUT> {
    val decode = decodePipeline
      ?: defaultDecodePipeline?.invoke()
      ?: error("No decode pipeline configured; call decode { ... }")
    val encode = encodePipeline ?: defaultFixedAudioEncodePipeline()

    return AudioTransmuter(
      loggerOverride = loggerOverride,
      transforms = transformPipeline.transforms,
      decodePipeline = decode,
      encodePipeline = encode,
      decodeOptions = decodeOptions,
      encodeOptions = encodeOptions,
    )
  }

  private fun defaultFixedAudioEncodePipeline(): EncodePipeline<Decoded<AudioFormat, AudioIR>, EncodedBytes<AudioFormat, OUT>> =
    PipelineBuilder.start<Decoded<AudioFormat, AudioIR>>()
      .then(AudioFixedEncodeHandler(output))
      .build()
}

class AudioTransmuter<IN, OUT : FormatTag<AudioFormat>> internal constructor(
    private val loggerOverride: TransmuteLogger?,
    private val transforms: List<Transform<AudioIR>>,
    private val decodePipeline: DecodePipeline<IN, Decoded<AudioFormat, AudioIR>>,
    private val encodePipeline: EncodePipeline<Decoded<AudioFormat, AudioIR>, EncodedBytes<AudioFormat, OUT>>,
    private val decodeOptions: AudioDecodeOptions,
    private val encodeOptions: AudioEncodeOptions,
) : Transmuter<IN, AudioFormat, OUT> {

  fun wouldTransmute(hint: AudioHint): Boolean {
    return transforms.any { (it as? AudioTransform)?.wouldTransform(hint) ?: true }
  }

  override suspend fun transmute(source: IN): EncodedBytes<AudioFormat, OUT> {
    val context = createContext(
      loggerOverride = loggerOverride,
      decodeOptions = decodeOptions,
      encodeOptions = encodeOptions,
    )
    AudioRegistries.installDefaultsIfEmpty()

    val decoded = decodePipeline.run(source, context)
    val inputFormat = decoded.format
    var ir = decoded.ir

    val steps = transforms
    steps.forEachIndexed { index, transform ->
      ir = transform.apply(ir, context)
    }

    val encoded = encodePipeline.run(Decoded(inputFormat, ir), context)
    return encoded
  }
}

// -- Video ------------------------------------------------------------------─

class DynamicVideoTransmuterBuilder<IN> internal constructor(
  private val defaultDecodePipeline: (() -> DecodePipeline<IN, Decoded<VideoFormat, VideoIR>>)? = null,
) {
  private val transformPipeline = TransformPipeline<VideoIR>()
  private var loggerOverride: TransmuteLogger? = null
  private var decodeOptions: VideoDecodeOptions = CanonicalVideoDecodeOptions()
  private var encodeOptions: VideoEncodeOptions = CanonicalVideoEncodeOptions()
  private var decodePipeline: DecodePipeline<IN, Decoded<VideoFormat, VideoIR>>? = null
  private var encodePipeline: EncodePipeline<Decoded<VideoFormat, VideoIR>, EncodedBytes<VideoFormat, AnyFormatTag<VideoFormat>>>? = null

  fun logger(logger: TransmuteLogger): DynamicVideoTransmuterBuilder<IN> = apply { loggerOverride = logger }

  fun decodeOptions(options: VideoDecodeOptions): DynamicVideoTransmuterBuilder<IN> = apply {
    decodeOptions = options
  }

  fun encodeOptions(options: VideoEncodeOptions): DynamicVideoTransmuterBuilder<IN> = apply {
    encodeOptions = options
  }

  fun transform(block: TransformPipeline<VideoIR>.() -> Unit): DynamicVideoTransmuterBuilder<IN> = apply {
    transformPipeline.block()
  }

  fun decode(
    block: PipelineBuilder<IN, IN>.() -> PipelineBuilder<IN, Decoded<VideoFormat, VideoIR>>,
  ): DynamicVideoTransmuterBuilder<IN> = apply {
    decodePipeline = PipelineBuilder.start<IN>().block().build()
  }

  fun encode(
    block: PipelineBuilder<Decoded<VideoFormat, VideoIR>, Decoded<VideoFormat, VideoIR>>.() ->
      PipelineBuilder<Decoded<VideoFormat, VideoIR>, EncodedBytes<VideoFormat, AnyFormatTag<VideoFormat>>>,
  ): DynamicVideoTransmuterBuilder<IN> = apply {
    encodePipeline = PipelineBuilder.start<Decoded<VideoFormat, VideoIR>>().block().build()
  }

  fun build(): VideoTransmuter<IN, AnyFormatTag<VideoFormat>> {
    val decode = decodePipeline
      ?: defaultDecodePipeline?.invoke()
      ?: error("No decode pipeline configured; call decode { ... }")
    val encode = encodePipeline ?: defaultDynamicVideoEncodePipeline()

    return VideoTransmuter(
      loggerOverride = loggerOverride,
      transforms = transformPipeline.transforms,
      decodePipeline = decode,
      encodePipeline = encode,
      decodeOptions = decodeOptions,
      encodeOptions = encodeOptions,
    )
  }

  private fun defaultDynamicVideoEncodePipeline(): EncodePipeline<Decoded<VideoFormat, VideoIR>, EncodedBytes<VideoFormat, AnyFormatTag<VideoFormat>>> =
    PipelineBuilder.start<Decoded<VideoFormat, VideoIR>>()
      .then(VideoDynamicEncodeHandler())
      .build()
}

class VideoTransmuterBuilder<IN, OUT : VideoFormatTag> internal constructor(
  private val output: OUT,
  private val defaultDecodePipeline: (() -> DecodePipeline<IN, Decoded<VideoFormat, VideoIR>>)? = null,
) {
  private val transformPipeline = TransformPipeline<VideoIR>()
  private var loggerOverride: TransmuteLogger? = null
  private var decodeOptions: VideoDecodeOptions = CanonicalVideoDecodeOptions()
  private var encodeOptions: VideoEncodeOptions = CanonicalVideoEncodeOptions()
  private var decodePipeline: DecodePipeline<IN, Decoded<VideoFormat, VideoIR>>? = null
  private var encodePipeline: EncodePipeline<Decoded<VideoFormat, VideoIR>, EncodedBytes<VideoFormat, OUT>>? = null

  fun logger(logger: TransmuteLogger): VideoTransmuterBuilder<IN, OUT> = apply { loggerOverride = logger }

  fun decodeOptions(options: VideoDecodeOptions): VideoTransmuterBuilder<IN, OUT> = apply {
    decodeOptions = options
  }

  fun encodeOptions(options: VideoEncodeOptions): VideoTransmuterBuilder<IN, OUT> = apply {
    encodeOptions = options
  }

  fun transform(block: TransformPipeline<VideoIR>.() -> Unit): VideoTransmuterBuilder<IN, OUT> = apply {
    transformPipeline.block()
  }

  fun decode(
    block: PipelineBuilder<IN, IN>.() -> PipelineBuilder<IN, Decoded<VideoFormat, VideoIR>>,
  ): VideoTransmuterBuilder<IN, OUT> = apply {
    decodePipeline = PipelineBuilder.start<IN>().block().build()
  }

  fun encode(
    block: PipelineBuilder<Decoded<VideoFormat, VideoIR>, Decoded<VideoFormat, VideoIR>>.() ->
      PipelineBuilder<Decoded<VideoFormat, VideoIR>, EncodedBytes<VideoFormat, OUT>>,
  ): VideoTransmuterBuilder<IN, OUT> = apply {
    encodePipeline = PipelineBuilder.start<Decoded<VideoFormat, VideoIR>>().block().build()
  }

  fun build(): VideoTransmuter<IN, OUT> {
    val decode = decodePipeline
      ?: defaultDecodePipeline?.invoke()
      ?: error("No decode pipeline configured; call decode { ... }")
    val encode = encodePipeline ?: defaultFixedVideoEncodePipeline()

    return VideoTransmuter(
      loggerOverride = loggerOverride,
      transforms = transformPipeline.transforms,
      decodePipeline = decode,
      encodePipeline = encode,
      decodeOptions = decodeOptions,
      encodeOptions = encodeOptions,
    )
  }

  private fun defaultFixedVideoEncodePipeline(): EncodePipeline<Decoded<VideoFormat, VideoIR>, EncodedBytes<VideoFormat, OUT>> =
    PipelineBuilder.start<Decoded<VideoFormat, VideoIR>>()
      .then(VideoFixedEncodeHandler(output))
      .build()
}

class VideoTransmuter<IN, OUT : FormatTag<VideoFormat>> internal constructor(
    private val loggerOverride: TransmuteLogger?,
    private val transforms: List<Transform<VideoIR>>,
    private val decodePipeline: DecodePipeline<IN, Decoded<VideoFormat, VideoIR>>,
    private val encodePipeline: EncodePipeline<Decoded<VideoFormat, VideoIR>, EncodedBytes<VideoFormat, OUT>>,
    private val decodeOptions: VideoDecodeOptions,
    private val encodeOptions: VideoEncodeOptions,
) : Transmuter<IN, VideoFormat, OUT> {

  fun wouldTransmute(hint: VideoHint): Boolean {
    return transforms.any { (it as? VideoTransform)?.wouldTransform(hint) ?: true }
  }

  override suspend fun transmute(source: IN): EncodedBytes<VideoFormat, OUT> {
    val context = createContext(
      loggerOverride = loggerOverride,
      decodeOptions = decodeOptions,
      encodeOptions = encodeOptions,
    )
    VideoRegistries.installDefaultsIfEmpty()

    val decoded = decodePipeline.run(source, context)
    val inputFormat = decoded.format
    var ir = decoded.ir

    val steps = transforms
    steps.forEachIndexed { index, transform ->
      ir = transform.apply(ir, context)
    }

    val encoded = encodePipeline.run(Decoded(inputFormat, ir), context)
    return encoded
  }
}

// -- Shared helpers --

private fun defaultImageByteArrayDecodePipeline(): DecodePipeline<ByteArray, Decoded<ImageFormat, ImageIR>> =
  PipelineBuilder.start<ByteArray>()
    .then(ImageDecodeHandler())
    .build()

private fun defaultAudioByteArrayDecodePipeline(): DecodePipeline<ByteArray, Decoded<AudioFormat, AudioIR>> =
  PipelineBuilder.start<ByteArray>()
    .then(AudioDecodeHandler())
    .build()

private fun defaultVideoByteArrayDecodePipeline(): DecodePipeline<ByteArray, Decoded<VideoFormat, VideoIR>> =
  PipelineBuilder.start<ByteArray>()
    .then(VideoDecodeHandler())
    .build()

private fun createContext(
    loggerOverride: TransmuteLogger?,
    decodeOptions: DecodeOptions,
    encodeOptions: EncodeOptions,
): TransmuteContext = TransmuteContext(
  logger = loggerOverride ?: TransmuteLogging.logger,
  decodeOptions = decodeOptions,
  encodeOptions = encodeOptions,
)
