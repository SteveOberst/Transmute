package dev.transmute

import dev.transmute.core.Bytes
import dev.transmute.core.UnknownFormat
import dev.transmute.core.asBytes
import dev.transmute.audio.AudioFormatDetector
import dev.transmute.audio.AudioFormat
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
import dev.transmute.core.DecodeOptions
import dev.transmute.core.EncodeOptions
import dev.transmute.core.TransmuteContext
import dev.transmute.core.TransmuteLogger
import dev.transmute.core.MediaFormat
import dev.transmute.core.TransmuteLogging
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
import dev.transmute.image.ImageFormat
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
import dev.transmute.video.VideoFormat
import dev.transmute.video.VideoFormatDetector
import dev.transmute.video.VideoHint
import dev.transmute.video.VideoIR
import dev.transmute.video.VideoDecodeHandler
import dev.transmute.video.VideoDynamicEncodeHandler
import dev.transmute.video.VideoFixedEncodeHandler
import dev.transmute.video.VideoRegistries
import dev.transmute.video.VideoTransform

typealias DynamicImageTransmuter = ImageTransmuter<Bytes, ImageFormat>
typealias DynamicAudioTransmuter = AudioTransmuter<Bytes, AudioFormat>
typealias DynamicVideoTransmuter = VideoTransmuter<Bytes, VideoFormat>

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

/** Immutable, reusable transmutation executor built by a builder DSL. */
interface Transmuter<IN, F : MediaFormat<*, *>> {
  /** Convert [source] into encoded bytes tagged with the resolved output format. */
  suspend fun transmute(source: IN): EncodedBytes<F>

  /** Convert [source] and copy the resulting bytes into [buffer]. Returns bytes written. */
  suspend fun transmute(source: IN, buffer: ByteArray, offset: Int = 0): Int {
    val bytes = transmute(source).bytes.data
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
  fun image(block: DynamicImageTransmuterBuilder<Bytes>.() -> Unit = {}): DynamicImageTransmuter =
    DynamicImageTransmuterBuilder(defaultDecodePipeline = ::defaultImageBytesDecodePipeline).apply(block).build()

  /** Build a dynamic-output image transmuter with an explicit input type. */
  fun <IN> imageFrom(block: DynamicImageTransmuterBuilder<IN>.() -> Unit = {}): ImageTransmuter<IN, ImageFormat> =
    DynamicImageTransmuterBuilder<IN>().apply(block).build()

  /** Build a fixed-output image transmuter with a typed output format object. */
  fun <OUT : ImageFormat> imageTo(output: OUT, block: ImageTransmuterBuilder<Bytes, OUT>.() -> Unit = {}): ImageTransmuter<Bytes, OUT> =
    ImageTransmuterBuilder(output, defaultDecodePipeline = ::defaultImageBytesDecodePipeline).apply(block).build()

  /** Build a fixed-output image transmuter with an explicit input type. */
  fun <IN, OUT : ImageFormat> imageToFrom(
    output: OUT,
    block: ImageTransmuterBuilder<IN, OUT>.() -> Unit = {},
  ): ImageTransmuter<IN, OUT> = ImageTransmuterBuilder<IN, OUT>(output).apply(block).build()

  // ---- Audio ----

  fun audio(block: DynamicAudioTransmuterBuilder<Bytes>.() -> Unit = {}): DynamicAudioTransmuter =
    DynamicAudioTransmuterBuilder(defaultDecodePipeline = ::defaultAudioBytesDecodePipeline).apply(block).build()

  fun <IN> audioFrom(block: DynamicAudioTransmuterBuilder<IN>.() -> Unit = {}): AudioTransmuter<IN, AudioFormat> =
    DynamicAudioTransmuterBuilder<IN>().apply(block).build()

  fun <OUT : AudioFormat> audioTo(output: OUT, block: AudioTransmuterBuilder<Bytes, OUT>.() -> Unit = {}): AudioTransmuter<Bytes, OUT> =
    AudioTransmuterBuilder(output, defaultDecodePipeline = ::defaultAudioBytesDecodePipeline).apply(block).build()

  fun <IN, OUT : AudioFormat> audioToFrom(
    output: OUT,
    block: AudioTransmuterBuilder<IN, OUT>.() -> Unit = {},
  ): AudioTransmuter<IN, OUT> = AudioTransmuterBuilder<IN, OUT>(output).apply(block).build()

  // ---- Video ----

  fun video(block: DynamicVideoTransmuterBuilder<Bytes>.() -> Unit = {}): DynamicVideoTransmuter =
    DynamicVideoTransmuterBuilder(defaultDecodePipeline = ::defaultVideoBytesDecodePipeline).apply(block).build()

  fun <IN> videoFrom(block: DynamicVideoTransmuterBuilder<IN>.() -> Unit = {}): VideoTransmuter<IN, VideoFormat> =
    DynamicVideoTransmuterBuilder<IN>().apply(block).build()

  fun <OUT : VideoFormat> videoTo(output: OUT, block: VideoTransmuterBuilder<Bytes, OUT>.() -> Unit = {}): VideoTransmuter<Bytes, OUT> =
    VideoTransmuterBuilder(output, defaultDecodePipeline = ::defaultVideoBytesDecodePipeline).apply(block).build()

  fun <IN, OUT : VideoFormat> videoToFrom(
    output: OUT,
    block: VideoTransmuterBuilder<IN, OUT>.() -> Unit = {},
  ): VideoTransmuter<IN, OUT> = VideoTransmuterBuilder<IN, OUT>(output).apply(block).build()

  // ---- Typed dispatch ----

  suspend fun transmute(type: TransmuteType, source: ByteArray): ByteArray = when (type) {
    TransmuteType.Image -> image().transmute(source.asBytes()).bytes.data
    TransmuteType.Audio -> audio().transmute(source.asBytes()).bytes.data
    TransmuteType.Video -> video().transmute(source.asBytes()).bytes.data
  }

  fun detectImageFormat(bytes: Bytes): ImageFormat = ImageFormatDetector.detect(bytes)
  fun detectAudioFormat(bytes: Bytes): AudioFormat = AudioFormatDetector.detect(bytes)
  fun detectVideoFormat(bytes: Bytes): VideoFormat = VideoFormatDetector.detect(bytes)

  fun detectImageFormat(bytes: ByteArray): ImageFormat = detectImageFormat(bytes.asBytes())
  fun detectAudioFormat(bytes: ByteArray): AudioFormat = detectAudioFormat(bytes.asBytes())
  fun detectVideoFormat(bytes: ByteArray): VideoFormat = detectVideoFormat(bytes.asBytes())

  /** Auto-detect the media domain and format from raw bytes. */
  fun detectFormat(bytes: Bytes): MediaFormat<*, *> {
    // ISO BMFF containers (MP4/MOV/M4A/HEIF/HEIC/AVIF) are ambiguous across domains.
    // Handle them explicitly first to avoid cross-domain misclassification.
    if (isBmff(bytes)) {
      val img = ImageFormatDetector.detect(bytes)
      if (img != ImageFormat.Unknown) return img

      val brand = bmffMajorBrand(bytes)
      if (brand == "qt  ") return VideoFormat.Mov

      val hasVideo = bmffHasVideoTrack(bytes)
      val hasAudio = bmffHasAudioTrack(bytes)
      if (hasVideo) return VideoFormat.Mp4
      if (hasAudio) return AudioFormat.M4a
      // Fall through to other detectors.
    }

    val img = ImageFormatDetector.detect(bytes)
    if (img != ImageFormat.Unknown) return img

    val vid = VideoFormatDetector.detect(bytes)
    if (vid != VideoFormat.Unknown) return vid

    val aud = AudioFormatDetector.detect(bytes)
    if (aud != AudioFormat.Unknown) return aud

    return UnknownFormat
  }

  fun detectFormat(bytes: ByteArray): MediaFormat<*, *> = detectFormat(bytes.asBytes())
}

// -- Image ------------------------------------------------------------------

private fun isBmff(bytes: Bytes): Boolean =
  bytes.size >= 8 &&
    bytes.data[4] == 0x66.toByte() && bytes.data[5] == 0x74.toByte() &&
    bytes.data[6] == 0x79.toByte() && bytes.data[7] == 0x70.toByte()

private fun bmffMajorBrand(bytes: Bytes): String? {
  if (!isBmff(bytes) || bytes.size < 12) return null
  return (8 until 12).map { bytes.data[it].toInt().toChar() }.joinToString("")
}

private fun bmffHasVideoTrack(bytes: Bytes): Boolean {
  val max = 256 * 1024
  return containsAscii(bytes, "vide", max) || containsAscii(bytes, "avc1", max) || containsAscii(bytes, "hvc1", max)
}

private fun bmffHasAudioTrack(bytes: Bytes): Boolean {
  val max = 256 * 1024
  return containsAscii(bytes, "soun", max) || containsAscii(bytes, "mp4a", max)
}

private fun containsAscii(bytes: Bytes, needleAscii: String, maxBytes: Int): Boolean {
  val needle = needleAscii.encodeToByteArray()
  val limit = minOf(bytes.size, maxBytes)
  if (needle.isEmpty() || limit < needle.size) return false

  val data = bytes.data
  outer@ for (i in 0..(limit - needle.size)) {
    for (j in needle.indices) {
      if (data[i + j] != needle[j]) continue@outer
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
  private var encodePipeline: EncodePipeline<Decoded<ImageFormat, ImageIR>, EncodedBytes<ImageFormat>>? = null

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
   * Configure the decode pipeline (IN → decode → ImageIR).
   *
   * The decode stage is explicit and must end in `Decoded<ImageFormat, ImageIR>`.
   */
  fun decode(
    block: PipelineBuilder<IN, IN>.() -> PipelineBuilder<IN, Decoded<ImageFormat, ImageIR>>,
  ): DynamicImageTransmuterBuilder<IN> = apply {
    decodePipeline = PipelineBuilder.start<IN>().block().build()
  }

  /**
   * Configure the encode pipeline (ImageIR → encode → EncodedBytes).
   */
  fun encode(
    block: PipelineBuilder<Decoded<ImageFormat, ImageIR>, Decoded<ImageFormat, ImageIR>>.() ->
      PipelineBuilder<Decoded<ImageFormat, ImageIR>, EncodedBytes<ImageFormat>>,
  ): DynamicImageTransmuterBuilder<IN> = apply {
    encodePipeline = PipelineBuilder.start<Decoded<ImageFormat, ImageIR>>().block().build()
  }

  fun build(): ImageTransmuter<IN, ImageFormat> {
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

  private fun defaultDynamicImageEncodePipeline(): EncodePipeline<Decoded<ImageFormat, ImageIR>, EncodedBytes<ImageFormat>> =
    PipelineBuilder.start<Decoded<ImageFormat, ImageIR>>()
      .then(ImageDynamicEncodeHandler())
      .build()
}

class ImageTransmuterBuilder<IN, OUT : ImageFormat> internal constructor(
  private val output: OUT,
  private val defaultDecodePipeline: (() -> DecodePipeline<IN, Decoded<ImageFormat, ImageIR>>)? = null,
) {
  private val transformPipeline = TransformPipeline<ImageIR>()
  private var loggerOverride: TransmuteLogger? = null
  private var decodeOptions: ImageDecodeOptions = CanonicalImageDecodeOptions()
  private var encodeOptions: ImageEncodeOptions = CanonicalImageEncodeOptions()
  private var decodePipeline: DecodePipeline<IN, Decoded<ImageFormat, ImageIR>>? = null
  private var encodePipeline: EncodePipeline<Decoded<ImageFormat, ImageIR>, EncodedBytes<OUT>>? = null

  fun logger(logger: TransmuteLogger): ImageTransmuterBuilder<IN, OUT> = apply { loggerOverride = logger }

  fun decodeOptions(options: ImageDecodeOptions): ImageTransmuterBuilder<IN, OUT> = apply {
    decodeOptions = options
  }

  /** Quality for JPEG encoding only (you probably want [ImageFormat.Jpeg]). */
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

  /** Fixed output enables type-safe post-encode handlers via the `OUT` format type. */
  fun encode(
    block: PipelineBuilder<Decoded<ImageFormat, ImageIR>, Decoded<ImageFormat, ImageIR>>.() ->
      PipelineBuilder<Decoded<ImageFormat, ImageIR>, EncodedBytes<OUT>>,
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

  private fun defaultFixedImageEncodePipeline(): EncodePipeline<Decoded<ImageFormat, ImageIR>, EncodedBytes<OUT>> =
    PipelineBuilder.start<Decoded<ImageFormat, ImageIR>>()
      .then(ImageFixedEncodeHandler(output))
      .build()
}

class ImageTransmuter<IN, OUT : ImageFormat> internal constructor(
    private val loggerOverride: TransmuteLogger?,
    private val transforms: List<Transform<ImageIR>>,
    private val decodePipeline: DecodePipeline<IN, Decoded<ImageFormat, ImageIR>>,
    private val encodePipeline: EncodePipeline<Decoded<ImageFormat, ImageIR>, EncodedBytes<OUT>>,
    private val decodeOptions: ImageDecodeOptions,
    private val encodeOptions: ImageEncodeOptions,
) : Transmuter<IN, OUT> {

  fun wouldTransmute(hint: ImageHint): Boolean {
    return transforms.any { (it as? ImageTransform)?.wouldTransform(hint) ?: true }
  }

  override suspend fun transmute(source: IN): EncodedBytes<OUT> {
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
  private var encodePipeline: EncodePipeline<Decoded<AudioFormat, AudioIR>, EncodedBytes<AudioFormat>>? = null

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
      PipelineBuilder<Decoded<AudioFormat, AudioIR>, EncodedBytes<AudioFormat>>,
  ): DynamicAudioTransmuterBuilder<IN> = apply {
    encodePipeline = PipelineBuilder.start<Decoded<AudioFormat, AudioIR>>().block().build()
  }

  fun build(): AudioTransmuter<IN, AudioFormat> {
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

  private fun defaultDynamicAudioEncodePipeline(): EncodePipeline<Decoded<AudioFormat, AudioIR>, EncodedBytes<AudioFormat>> =
    PipelineBuilder.start<Decoded<AudioFormat, AudioIR>>()
      .then(AudioDynamicEncodeHandler())
      .build()
}

class AudioTransmuterBuilder<IN, OUT : AudioFormat> internal constructor(
  private val output: OUT,
  private val defaultDecodePipeline: (() -> DecodePipeline<IN, Decoded<AudioFormat, AudioIR>>)? = null,
) {
  private val transformPipeline = TransformPipeline<AudioIR>()
  private var loggerOverride: TransmuteLogger? = null
  private var decodeOptions: AudioDecodeOptions = CanonicalAudioDecodeOptions()
  private var encodeOptions: AudioEncodeOptions = CanonicalAudioEncodeOptions()
  private var decodePipeline: DecodePipeline<IN, Decoded<AudioFormat, AudioIR>>? = null
  private var encodePipeline: EncodePipeline<Decoded<AudioFormat, AudioIR>, EncodedBytes<OUT>>? = null

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
      PipelineBuilder<Decoded<AudioFormat, AudioIR>, EncodedBytes<OUT>>,
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

  private fun defaultFixedAudioEncodePipeline(): EncodePipeline<Decoded<AudioFormat, AudioIR>, EncodedBytes<OUT>> =
    PipelineBuilder.start<Decoded<AudioFormat, AudioIR>>()
      .then(AudioFixedEncodeHandler(output))
      .build()
}

class AudioTransmuter<IN, OUT : AudioFormat> internal constructor(
    private val loggerOverride: TransmuteLogger?,
    private val transforms: List<Transform<AudioIR>>,
    private val decodePipeline: DecodePipeline<IN, Decoded<AudioFormat, AudioIR>>,
    private val encodePipeline: EncodePipeline<Decoded<AudioFormat, AudioIR>, EncodedBytes<OUT>>,
    private val decodeOptions: AudioDecodeOptions,
    private val encodeOptions: AudioEncodeOptions,
) : Transmuter<IN, OUT> {

  fun wouldTransmute(hint: AudioHint): Boolean {
    return transforms.any { (it as? AudioTransform)?.wouldTransform(hint) ?: true }
  }

  override suspend fun transmute(source: IN): EncodedBytes<OUT> {
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
  private var encodePipeline: EncodePipeline<Decoded<VideoFormat, VideoIR>, EncodedBytes<VideoFormat>>? = null

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
      PipelineBuilder<Decoded<VideoFormat, VideoIR>, EncodedBytes<VideoFormat>>,
  ): DynamicVideoTransmuterBuilder<IN> = apply {
    encodePipeline = PipelineBuilder.start<Decoded<VideoFormat, VideoIR>>().block().build()
  }

  fun build(): VideoTransmuter<IN, VideoFormat> {
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

  private fun defaultDynamicVideoEncodePipeline(): EncodePipeline<Decoded<VideoFormat, VideoIR>, EncodedBytes<VideoFormat>> =
    PipelineBuilder.start<Decoded<VideoFormat, VideoIR>>()
      .then(VideoDynamicEncodeHandler())
      .build()
}

class VideoTransmuterBuilder<IN, OUT : VideoFormat> internal constructor(
  private val output: OUT,
  private val defaultDecodePipeline: (() -> DecodePipeline<IN, Decoded<VideoFormat, VideoIR>>)? = null,
) {
  private val transformPipeline = TransformPipeline<VideoIR>()
  private var loggerOverride: TransmuteLogger? = null
  private var decodeOptions: VideoDecodeOptions = CanonicalVideoDecodeOptions()
  private var encodeOptions: VideoEncodeOptions = CanonicalVideoEncodeOptions()
  private var decodePipeline: DecodePipeline<IN, Decoded<VideoFormat, VideoIR>>? = null
  private var encodePipeline: EncodePipeline<Decoded<VideoFormat, VideoIR>, EncodedBytes<OUT>>? = null

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
      PipelineBuilder<Decoded<VideoFormat, VideoIR>, EncodedBytes<OUT>>,
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

  private fun defaultFixedVideoEncodePipeline(): EncodePipeline<Decoded<VideoFormat, VideoIR>, EncodedBytes<OUT>> =
    PipelineBuilder.start<Decoded<VideoFormat, VideoIR>>()
      .then(VideoFixedEncodeHandler(output))
      .build()
}

class VideoTransmuter<IN, OUT : VideoFormat> internal constructor(
    private val loggerOverride: TransmuteLogger?,
    private val transforms: List<Transform<VideoIR>>,
    private val decodePipeline: DecodePipeline<IN, Decoded<VideoFormat, VideoIR>>,
    private val encodePipeline: EncodePipeline<Decoded<VideoFormat, VideoIR>, EncodedBytes<OUT>>,
    private val decodeOptions: VideoDecodeOptions,
    private val encodeOptions: VideoEncodeOptions,
) : Transmuter<IN, OUT> {

  fun wouldTransmute(hint: VideoHint): Boolean {
    return transforms.any { (it as? VideoTransform)?.wouldTransform(hint) ?: true }
  }

  override suspend fun transmute(source: IN): EncodedBytes<OUT> {
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

private fun defaultImageBytesDecodePipeline(): DecodePipeline<Bytes, Decoded<ImageFormat, ImageIR>> =
  PipelineBuilder.start<Bytes>()
    .then(ImageDecodeHandler())
    .build()

private fun defaultAudioBytesDecodePipeline(): DecodePipeline<Bytes, Decoded<AudioFormat, AudioIR>> =
  PipelineBuilder.start<Bytes>()
    .then(AudioDecodeHandler())
    .build()

private fun defaultVideoBytesDecodePipeline(): DecodePipeline<Bytes, Decoded<VideoFormat, VideoIR>> =
  PipelineBuilder.start<Bytes>()
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
