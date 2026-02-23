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
import dev.transmute.core.TransmuteLogging
import dev.transmute.core.MediaFormat
import dev.transmute.core.pipeline.DecodePipeline
import dev.transmute.core.pipeline.Decoded
import dev.transmute.core.pipeline.EncodePipeline
import dev.transmute.core.pipeline.EncodedBytes
import dev.transmute.core.pipeline.PipelineHandler
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

typealias DynamicImageTransmuter = ImageTransmuter<Bytes, EncodedBytes<ImageFormat>>
typealias DynamicAudioTransmuter = AudioTransmuter<Bytes, EncodedBytes<AudioFormat>>
typealias DynamicVideoTransmuter = VideoTransmuter<Bytes, EncodedBytes<VideoFormat>>

/**
 * Discriminates the three media domains at the type level.
 *
 * This dispatch type is for the *dynamic-output* transmuters.
 * If you want a type-level output format (e.g. PNG-only post-encode handlers),
 * build via `Transmute.imageTo(ImageFormat.Png) { ... }` instead.
 */
sealed class TransmuteType {
  data object Image : TransmuteType()
  data object Audio : TransmuteType()
  data object Video : TransmuteType()
}

/** Immutable, reusable transmutation executor built by a builder DSL. */
interface Transmuter<IN, OUT> {
  /** Convert [source] into the encode pipeline's output type. */
  suspend fun transmute(source: IN): OUT
}

/** Convert [source] and copy the resulting bytes into [buffer]. Returns bytes written. */
suspend fun <IN, F : MediaFormat<*, *>> Transmuter<IN, EncodedBytes<F>>.transmute(
  source: IN,
  buffer: ByteArray,
  offset: Int = 0,
): Int {
  val bytes = transmute(source).bytes.data
  require(offset >= 0 && offset <= buffer.size) { "offset out of bounds" }
  require(bytes.size <= buffer.size - offset) {
    "buffer too small: need ${bytes.size}, have ${buffer.size - offset}"
  }
  bytes.copyInto(buffer, destinationOffset = offset)
  return bytes.size
}

/** Public API facade for Transmute. */
object Transmute {

  // ---- Image ----

  /** Build a dynamic-output image transmuter (output defaults to input format). */
  fun image(block: DynamicImageTransmuterBuilder<Bytes, EncodedBytes<ImageFormat>>.() -> Unit = {}): DynamicImageTransmuter =
    DynamicImageTransmuterBuilder<Bytes, EncodedBytes<ImageFormat>>(
      defaultDecodePipeline = ::defaultImageBytesDecodePipeline,
      defaultEncodePipeline = ::defaultDynamicImageEncodePipeline,
    ).apply(block).build()

  /**
   * Build a dynamic-output image transmuter whose encode pipeline output type is [OUT].
   *
   * When you choose a custom output type, you must configure an encode pipeline explicitly.
   * A typical pattern is:
   *
   * `encode { pipeline(start = ImageCodecs.Encode.DEFAULT + MyEncodedBytesToPlatformHandler()) }`
   */
  fun <OUT> imageOut(block: DynamicImageTransmuterBuilder<Bytes, OUT>.() -> Unit): ImageTransmuter<Bytes, OUT> =
    DynamicImageTransmuterBuilder<Bytes, OUT>(defaultDecodePipeline = ::defaultImageBytesDecodePipeline).apply(block).build()

  /** Build a dynamic-output image transmuter with an explicit input type. */
  fun <IN> imageFrom(block: DynamicImageTransmuterBuilder<IN, EncodedBytes<ImageFormat>>.() -> Unit = {}): ImageTransmuter<IN, EncodedBytes<ImageFormat>> =
    DynamicImageTransmuterBuilder<IN, EncodedBytes<ImageFormat>>(defaultEncodePipeline = ::defaultDynamicImageEncodePipeline).apply(block).build()

  /** Build a dynamic-output image transmuter with an explicit input + output type. */
  fun <IN, OUT> imageFromOut(block: DynamicImageTransmuterBuilder<IN, OUT>.() -> Unit): ImageTransmuter<IN, OUT> =
    DynamicImageTransmuterBuilder<IN, OUT>().apply(block).build()

  /** Build a fixed-output image transmuter with a typed output format object. */
  fun <OUT : ImageFormat> imageTo(output: OUT, block: ImageTransmuterBuilder<Bytes, OUT>.() -> Unit = {}): ImageTransmuter<Bytes, EncodedBytes<OUT>> =
    ImageTransmuterBuilder(output, defaultDecodePipeline = ::defaultImageBytesDecodePipeline).apply(block).build()

  /** Build a fixed-output image transmuter with an explicit input type. */
  fun <IN, OUT : ImageFormat> imageToFrom(
    output: OUT,
    block: ImageTransmuterBuilder<IN, OUT>.() -> Unit = {},
  ): ImageTransmuter<IN, EncodedBytes<OUT>> = ImageTransmuterBuilder<IN, OUT>(output).apply(block).build()

  // ---- Audio ----

  fun audio(block: DynamicAudioTransmuterBuilder<Bytes, EncodedBytes<AudioFormat>>.() -> Unit = {}): DynamicAudioTransmuter =
    DynamicAudioTransmuterBuilder<Bytes, EncodedBytes<AudioFormat>>(
      defaultDecodePipeline = ::defaultAudioBytesDecodePipeline,
      defaultEncodePipeline = ::defaultDynamicAudioEncodePipeline,
    ).apply(block).build()

  fun <OUT> audioOut(block: DynamicAudioTransmuterBuilder<Bytes, OUT>.() -> Unit): AudioTransmuter<Bytes, OUT> =
    DynamicAudioTransmuterBuilder<Bytes, OUT>(defaultDecodePipeline = ::defaultAudioBytesDecodePipeline).apply(block).build()

  fun <IN> audioFrom(block: DynamicAudioTransmuterBuilder<IN, EncodedBytes<AudioFormat>>.() -> Unit = {}): AudioTransmuter<IN, EncodedBytes<AudioFormat>> =
    DynamicAudioTransmuterBuilder<IN, EncodedBytes<AudioFormat>>(defaultEncodePipeline = ::defaultDynamicAudioEncodePipeline).apply(block).build()

  fun <IN, OUT> audioFromOut(block: DynamicAudioTransmuterBuilder<IN, OUT>.() -> Unit): AudioTransmuter<IN, OUT> =
    DynamicAudioTransmuterBuilder<IN, OUT>().apply(block).build()

  fun <OUT : AudioFormat> audioTo(output: OUT, block: AudioTransmuterBuilder<Bytes, OUT>.() -> Unit = {}): AudioTransmuter<Bytes, EncodedBytes<OUT>> =
    AudioTransmuterBuilder(output, defaultDecodePipeline = ::defaultAudioBytesDecodePipeline).apply(block).build()

  fun <IN, OUT : AudioFormat> audioToFrom(
    output: OUT,
    block: AudioTransmuterBuilder<IN, OUT>.() -> Unit = {},
  ): AudioTransmuter<IN, EncodedBytes<OUT>> = AudioTransmuterBuilder<IN, OUT>(output).apply(block).build()

  // ---- Video ----

  fun video(block: DynamicVideoTransmuterBuilder<Bytes, EncodedBytes<VideoFormat>>.() -> Unit = {}): DynamicVideoTransmuter =
    DynamicVideoTransmuterBuilder<Bytes, EncodedBytes<VideoFormat>>(
      defaultDecodePipeline = ::defaultVideoBytesDecodePipeline,
      defaultEncodePipeline = ::defaultDynamicVideoEncodePipeline,
    ).apply(block).build()

  fun <OUT> videoOut(block: DynamicVideoTransmuterBuilder<Bytes, OUT>.() -> Unit): VideoTransmuter<Bytes, OUT> =
    DynamicVideoTransmuterBuilder<Bytes, OUT>(defaultDecodePipeline = ::defaultVideoBytesDecodePipeline).apply(block).build()

  fun <IN> videoFrom(block: DynamicVideoTransmuterBuilder<IN, EncodedBytes<VideoFormat>>.() -> Unit = {}): VideoTransmuter<IN, EncodedBytes<VideoFormat>> =
    DynamicVideoTransmuterBuilder<IN, EncodedBytes<VideoFormat>>(defaultEncodePipeline = ::defaultDynamicVideoEncodePipeline).apply(block).build()

  fun <IN, OUT> videoFromOut(block: DynamicVideoTransmuterBuilder<IN, OUT>.() -> Unit): VideoTransmuter<IN, OUT> =
    DynamicVideoTransmuterBuilder<IN, OUT>().apply(block).build()

  fun <OUT : VideoFormat> videoTo(output: OUT, block: VideoTransmuterBuilder<Bytes, OUT>.() -> Unit = {}): VideoTransmuter<Bytes, EncodedBytes<OUT>> =
    VideoTransmuterBuilder(output, defaultDecodePipeline = ::defaultVideoBytesDecodePipeline).apply(block).build()

  fun <IN, OUT : VideoFormat> videoToFrom(
    output: OUT,
    block: VideoTransmuterBuilder<IN, OUT>.() -> Unit = {},
  ): VideoTransmuter<IN, EncodedBytes<OUT>> = VideoTransmuterBuilder<IN, OUT>(output).apply(block).build()

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

class DynamicImageTransmuterBuilder<IN, OUT> internal constructor(
  private val defaultDecodePipeline: (() -> DecodePipeline<IN, Decoded<ImageFormat, ImageIR>>)? = null,
  private val defaultEncodePipeline: (() -> EncodePipeline<Decoded<ImageFormat, ImageIR>, OUT>)? = null,
) {
  private val transformPipeline = TransformPipeline<ImageIR>()
  private var loggerOverride: TransmuteLogger? = null
  private val decodeStage =
    DecodeStage<IN, Decoded<ImageFormat, ImageIR>, ImageDecodeOptions>(CanonicalImageDecodeOptions())
  private val encodeStage =
    EncodeStage<Decoded<ImageFormat, ImageIR>, OUT, ImageEncodeOptions>(CanonicalImageEncodeOptions())

  fun logger(logger: TransmuteLogger): DynamicImageTransmuterBuilder<IN, OUT> = apply { loggerOverride = logger }

  fun transform(block: TransformPipeline<ImageIR>.() -> Unit): DynamicImageTransmuterBuilder<IN, OUT> = apply {
    transformPipeline.block()
  }

  /**
   * Configure the decode pipeline (IN → decode → ImageIR).
   *
   * The decode stage is explicit and must end in `Decoded<ImageFormat, ImageIR>`.
   */
  fun decode(block: DecodeStage<IN, Decoded<ImageFormat, ImageIR>, ImageDecodeOptions>.() -> Unit): DynamicImageTransmuterBuilder<IN, OUT> =
    apply { decodeStage.block() }

  /**
   * Configure the encode pipeline (ImageIR → encode → EncodedBytes).
   */
  fun encode(block: EncodeStage<Decoded<ImageFormat, ImageIR>, OUT, ImageEncodeOptions>.() -> Unit): DynamicImageTransmuterBuilder<IN, OUT> =
    apply { encodeStage.block() }

  fun build(): ImageTransmuter<IN, OUT> {
    val decode = decodeStage.pipeline
      ?: defaultDecodePipeline?.invoke()
      ?: error("No decode pipeline configured; call decode { ... }")
    val encode = encodeStage.pipeline
      ?: defaultEncodePipeline?.invoke()
      ?: error("No encode pipeline configured; call encode { ... }")

    return ImageTransmuter(
      loggerOverride = loggerOverride,
      transforms = transformPipeline.transforms,
      decodePipeline = decode,
      encodePipeline = encode,
      decodeOptions = decodeStage.options,
      encodeOptions = encodeStage.options,
    )
  }
}

class ImageTransmuterBuilder<IN, OUT_FORMAT : ImageFormat> internal constructor(
  private val output: OUT_FORMAT,
  private val defaultDecodePipeline: (() -> DecodePipeline<IN, Decoded<ImageFormat, ImageIR>>)? = null,
) {
  private val transformPipeline = TransformPipeline<ImageIR>()
  private var loggerOverride: TransmuteLogger? = null
  private val decodeStage =
    DecodeStage<IN, Decoded<ImageFormat, ImageIR>, ImageDecodeOptions>(CanonicalImageDecodeOptions())
  private val encodeStage =
    EncodeStage<Decoded<ImageFormat, ImageIR>, EncodedBytes<OUT_FORMAT>, ImageEncodeOptions>(CanonicalImageEncodeOptions())

  fun logger(logger: TransmuteLogger): ImageTransmuterBuilder<IN, OUT_FORMAT> = apply { loggerOverride = logger }

  /** Quality for JPEG encoding only (you probably want [ImageFormat.Jpeg]). */
  fun quality(value: Float): ImageTransmuterBuilder<IN, OUT_FORMAT> = apply {
    encodeStage.options = JpegEncodeOptions(quality = value.coerceIn(0f, 1f))
  }

  fun transform(block: TransformPipeline<ImageIR>.() -> Unit): ImageTransmuterBuilder<IN, OUT_FORMAT> = apply {
    transformPipeline.block()
  }

  fun decode(block: DecodeStage<IN, Decoded<ImageFormat, ImageIR>, ImageDecodeOptions>.() -> Unit): ImageTransmuterBuilder<IN, OUT_FORMAT> =
    apply { decodeStage.block() }

  /** Fixed output enables type-safe post-encode handlers via the `OUT` format type. */
  fun encode(block: EncodeStage<Decoded<ImageFormat, ImageIR>, EncodedBytes<OUT_FORMAT>, ImageEncodeOptions>.() -> Unit): ImageTransmuterBuilder<IN, OUT_FORMAT> =
    apply { encodeStage.block() }

  fun build(): ImageTransmuter<IN, EncodedBytes<OUT_FORMAT>> {
    val decode = decodeStage.pipeline
      ?: defaultDecodePipeline?.invoke()
      ?: error("No decode pipeline configured; call decode { ... }")
    val encode = encodeStage.pipeline ?: defaultFixedImageEncodePipeline()

    return ImageTransmuter(
      loggerOverride = loggerOverride,
      transforms = transformPipeline.transforms,
      decodePipeline = decode,
      encodePipeline = encode,
      decodeOptions = decodeStage.options,
      encodeOptions = encodeStage.options,
    )
  }

  private fun defaultFixedImageEncodePipeline(): EncodePipeline<Decoded<ImageFormat, ImageIR>, EncodedBytes<OUT_FORMAT>> =
    PipelineBuilder.start<Decoded<ImageFormat, ImageIR>>()
      .then(ImageFixedEncodeHandler(output))
      .build()
}

class ImageTransmuter<IN, OUT> internal constructor(
    private val loggerOverride: TransmuteLogger?,
    private val transforms: List<Transform<ImageIR>>,
    private val decodePipeline: DecodePipeline<IN, Decoded<ImageFormat, ImageIR>>,
    private val encodePipeline: EncodePipeline<Decoded<ImageFormat, ImageIR>, OUT>,
    private val decodeOptions: ImageDecodeOptions,
    private val encodeOptions: ImageEncodeOptions,
) : Transmuter<IN, OUT> {

  fun wouldTransmute(hint: ImageHint): Boolean {
    return transforms.any { (it as? ImageTransform)?.wouldTransform(hint) ?: true }
  }

  override suspend fun transmute(source: IN): OUT {
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

    return encodePipeline.run(Decoded(inputFormat, ir), context)
  }
}

// -- Audio ------------------------------------------------------------------─

class DynamicAudioTransmuterBuilder<IN, OUT> internal constructor(
  private val defaultDecodePipeline: (() -> DecodePipeline<IN, Decoded<AudioFormat, AudioIR>>)? = null,
  private val defaultEncodePipeline: (() -> EncodePipeline<Decoded<AudioFormat, AudioIR>, OUT>)? = null,
) {
  private val transformPipeline = TransformPipeline<AudioIR>()
  private var loggerOverride: TransmuteLogger? = null
  private val decodeStage =
    DecodeStage<IN, Decoded<AudioFormat, AudioIR>, AudioDecodeOptions>(CanonicalAudioDecodeOptions())
  private val encodeStage =
    EncodeStage<Decoded<AudioFormat, AudioIR>, OUT, AudioEncodeOptions>(CanonicalAudioEncodeOptions())

  fun logger(logger: TransmuteLogger): DynamicAudioTransmuterBuilder<IN, OUT> = apply { loggerOverride = logger }

  fun transform(block: TransformPipeline<AudioIR>.() -> Unit): DynamicAudioTransmuterBuilder<IN, OUT> = apply {
    transformPipeline.block()
  }

  fun decode(block: DecodeStage<IN, Decoded<AudioFormat, AudioIR>, AudioDecodeOptions>.() -> Unit): DynamicAudioTransmuterBuilder<IN, OUT> =
    apply { decodeStage.block() }

  fun encode(block: EncodeStage<Decoded<AudioFormat, AudioIR>, OUT, AudioEncodeOptions>.() -> Unit): DynamicAudioTransmuterBuilder<IN, OUT> =
    apply { encodeStage.block() }

  fun build(): AudioTransmuter<IN, OUT> {
    val decode = decodeStage.pipeline
      ?: defaultDecodePipeline?.invoke()
      ?: error("No decode pipeline configured; call decode { ... }")
    val encode = encodeStage.pipeline
      ?: defaultEncodePipeline?.invoke()
      ?: error("No encode pipeline configured; call encode { ... }")

    return AudioTransmuter(
      loggerOverride = loggerOverride,
      transforms = transformPipeline.transforms,
      decodePipeline = decode,
      encodePipeline = encode,
      decodeOptions = decodeStage.options,
      encodeOptions = encodeStage.options,
    )
  }
}

class AudioTransmuterBuilder<IN, OUT : AudioFormat> internal constructor(
  private val output: OUT,
  private val defaultDecodePipeline: (() -> DecodePipeline<IN, Decoded<AudioFormat, AudioIR>>)? = null,
) {
  private val transformPipeline = TransformPipeline<AudioIR>()
  private var loggerOverride: TransmuteLogger? = null
  private val decodeStage =
    DecodeStage<IN, Decoded<AudioFormat, AudioIR>, AudioDecodeOptions>(CanonicalAudioDecodeOptions())
  private val encodeStage =
    EncodeStage<Decoded<AudioFormat, AudioIR>, EncodedBytes<OUT>, AudioEncodeOptions>(CanonicalAudioEncodeOptions())

  fun logger(logger: TransmuteLogger): AudioTransmuterBuilder<IN, OUT> = apply { loggerOverride = logger }

  fun transform(block: TransformPipeline<AudioIR>.() -> Unit): AudioTransmuterBuilder<IN, OUT> = apply {
    transformPipeline.block()
  }

  fun decode(block: DecodeStage<IN, Decoded<AudioFormat, AudioIR>, AudioDecodeOptions>.() -> Unit): AudioTransmuterBuilder<IN, OUT> =
    apply { decodeStage.block() }

  fun encode(block: EncodeStage<Decoded<AudioFormat, AudioIR>, EncodedBytes<OUT>, AudioEncodeOptions>.() -> Unit): AudioTransmuterBuilder<IN, OUT> =
    apply { encodeStage.block() }

  fun build(): AudioTransmuter<IN, EncodedBytes<OUT>> {
    val decode = decodeStage.pipeline
      ?: defaultDecodePipeline?.invoke()
      ?: error("No decode pipeline configured; call decode { ... }")
    val encode = encodeStage.pipeline ?: defaultFixedAudioEncodePipeline()

    return AudioTransmuter(
      loggerOverride = loggerOverride,
      transforms = transformPipeline.transforms,
      decodePipeline = decode,
      encodePipeline = encode,
      decodeOptions = decodeStage.options,
      encodeOptions = encodeStage.options,
    )
  }

  private fun defaultFixedAudioEncodePipeline(): EncodePipeline<Decoded<AudioFormat, AudioIR>, EncodedBytes<OUT>> =
    PipelineBuilder.start<Decoded<AudioFormat, AudioIR>>()
      .then(AudioFixedEncodeHandler(output))
      .build()
}

class AudioTransmuter<IN, OUT> internal constructor(
  private val loggerOverride: TransmuteLogger?,
  private val transforms: List<Transform<AudioIR>>,
  private val decodePipeline: DecodePipeline<IN, Decoded<AudioFormat, AudioIR>>,
  private val encodePipeline: EncodePipeline<Decoded<AudioFormat, AudioIR>, OUT>,
  private val decodeOptions: AudioDecodeOptions,
  private val encodeOptions: AudioEncodeOptions,
) : Transmuter<IN, OUT> {

  fun wouldTransmute(hint: AudioHint): Boolean {
    return transforms.any { (it as? AudioTransform)?.wouldTransform(hint) ?: true }
  }

  override suspend fun transmute(source: IN): OUT {
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

    return encodePipeline.run(Decoded(inputFormat, ir), context)
  }
}

// -- Video ------------------------------------------------------------------─

class DynamicVideoTransmuterBuilder<IN, OUT> internal constructor(
  private val defaultDecodePipeline: (() -> DecodePipeline<IN, Decoded<VideoFormat, VideoIR>>)? = null,
  private val defaultEncodePipeline: (() -> EncodePipeline<Decoded<VideoFormat, VideoIR>, OUT>)? = null,
) {
  private val transformPipeline = TransformPipeline<VideoIR>()
  private var loggerOverride: TransmuteLogger? = null
  private val decodeStage =
    DecodeStage<IN, Decoded<VideoFormat, VideoIR>, VideoDecodeOptions>(CanonicalVideoDecodeOptions())
  private val encodeStage =
    EncodeStage<Decoded<VideoFormat, VideoIR>, OUT, VideoEncodeOptions>(CanonicalVideoEncodeOptions())

  fun logger(logger: TransmuteLogger): DynamicVideoTransmuterBuilder<IN, OUT> = apply { loggerOverride = logger }

  fun transform(block: TransformPipeline<VideoIR>.() -> Unit): DynamicVideoTransmuterBuilder<IN, OUT> = apply {
    transformPipeline.block()
  }

  fun decode(block: DecodeStage<IN, Decoded<VideoFormat, VideoIR>, VideoDecodeOptions>.() -> Unit): DynamicVideoTransmuterBuilder<IN, OUT> =
    apply { decodeStage.block() }

  fun encode(block: EncodeStage<Decoded<VideoFormat, VideoIR>, OUT, VideoEncodeOptions>.() -> Unit): DynamicVideoTransmuterBuilder<IN, OUT> =
    apply { encodeStage.block() }

  fun build(): VideoTransmuter<IN, OUT> {
    val decode = decodeStage.pipeline
      ?: defaultDecodePipeline?.invoke()
      ?: error("No decode pipeline configured; call decode { ... }")
    val encode = encodeStage.pipeline
      ?: defaultEncodePipeline?.invoke()
      ?: error("No encode pipeline configured; call encode { ... }")

    return VideoTransmuter(
      loggerOverride = loggerOverride,
      transforms = transformPipeline.transforms,
      decodePipeline = decode,
      encodePipeline = encode,
      decodeOptions = decodeStage.options,
      encodeOptions = encodeStage.options,
    )
  }
}

class VideoTransmuterBuilder<IN, OUT : VideoFormat> internal constructor(
  private val output: OUT,
  private val defaultDecodePipeline: (() -> DecodePipeline<IN, Decoded<VideoFormat, VideoIR>>)? = null,
) {
  private val transformPipeline = TransformPipeline<VideoIR>()
  private var loggerOverride: TransmuteLogger? = null
  private val decodeStage =
    DecodeStage<IN, Decoded<VideoFormat, VideoIR>, VideoDecodeOptions>(CanonicalVideoDecodeOptions())
  private val encodeStage =
    EncodeStage<Decoded<VideoFormat, VideoIR>, EncodedBytes<OUT>, VideoEncodeOptions>(CanonicalVideoEncodeOptions())

  fun logger(logger: TransmuteLogger): VideoTransmuterBuilder<IN, OUT> = apply { loggerOverride = logger }

  fun transform(block: TransformPipeline<VideoIR>.() -> Unit): VideoTransmuterBuilder<IN, OUT> = apply {
    transformPipeline.block()
  }

  fun decode(block: DecodeStage<IN, Decoded<VideoFormat, VideoIR>, VideoDecodeOptions>.() -> Unit): VideoTransmuterBuilder<IN, OUT> =
    apply { decodeStage.block() }

  fun encode(block: EncodeStage<Decoded<VideoFormat, VideoIR>, EncodedBytes<OUT>, VideoEncodeOptions>.() -> Unit): VideoTransmuterBuilder<IN, OUT> =
    apply { encodeStage.block() }

  fun build(): VideoTransmuter<IN, EncodedBytes<OUT>> {
    val decode = decodeStage.pipeline
      ?: defaultDecodePipeline?.invoke()
      ?: error("No decode pipeline configured; call decode { ... }")
    val encode = encodeStage.pipeline ?: defaultFixedVideoEncodePipeline()

    return VideoTransmuter(
      loggerOverride = loggerOverride,
      transforms = transformPipeline.transforms,
      decodePipeline = decode,
      encodePipeline = encode,
      decodeOptions = decodeStage.options,
      encodeOptions = encodeStage.options,
    )
  }

  private fun defaultFixedVideoEncodePipeline(): EncodePipeline<Decoded<VideoFormat, VideoIR>, EncodedBytes<OUT>> =
    PipelineBuilder.start<Decoded<VideoFormat, VideoIR>>()
      .then(VideoFixedEncodeHandler(output))
      .build()
}

class VideoTransmuter<IN, OUT> internal constructor(
    private val loggerOverride: TransmuteLogger?,
    private val transforms: List<Transform<VideoIR>>,
    private val decodePipeline: DecodePipeline<IN, Decoded<VideoFormat, VideoIR>>,
    private val encodePipeline: EncodePipeline<Decoded<VideoFormat, VideoIR>, OUT>,
    private val decodeOptions: VideoDecodeOptions,
    private val encodeOptions: VideoEncodeOptions,
) : Transmuter<IN, OUT> {

  fun wouldTransmute(hint: VideoHint): Boolean {
    return transforms.any { (it as? VideoTransform)?.wouldTransform(hint) ?: true }
  }

  override suspend fun transmute(source: IN): OUT {
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

    return encodePipeline.run(Decoded(inputFormat, ir), context)
  }
}

class DecodeStage<IN, OUT, OPTS : DecodeOptions>(
  defaultOptions: OPTS,
) {
  var options: OPTS = defaultOptions
  var pipeline: DecodePipeline<IN, OUT>? = null

  fun options(options: OPTS): DecodeStage<IN, OUT, OPTS> = apply { this.options = options }

  fun pipeline(pipeline: DecodePipeline<IN, OUT>): DecodeStage<IN, OUT, OPTS> = apply { this.pipeline = pipeline }

  fun pipeline(start: PipelineHandler<IN, OUT>): DecodeStage<IN, OUT, OPTS> = apply {
    pipeline = PipelineBuilder.start<IN>().startWith(start).build()
  }

  fun <CUR> pipeline(
    start: PipelineHandler<IN, CUR>,
    block: PipelineBuilder<IN, CUR>.() -> PipelineBuilder<IN, OUT>,
  ): DecodeStage<IN, OUT, OPTS> = apply {
    pipeline = PipelineBuilder.start<IN>().startWith(start).block().build()
  }
}

class EncodeStage<IN, OUT, OPTS : EncodeOptions>(
  defaultOptions: OPTS,
) {
  var options: OPTS = defaultOptions
  var pipeline: EncodePipeline<IN, OUT>? = null

  fun options(options: OPTS): EncodeStage<IN, OUT, OPTS> = apply { this.options = options }

  fun pipeline(pipeline: EncodePipeline<IN, OUT>): EncodeStage<IN, OUT, OPTS> = apply { this.pipeline = pipeline }

  fun pipeline(start: PipelineHandler<IN, OUT>): EncodeStage<IN, OUT, OPTS> = apply {
    pipeline = PipelineBuilder.start<IN>().startWith(start).build()
  }

  fun <CUR> pipeline(
    start: PipelineHandler<IN, CUR>,
    block: PipelineBuilder<IN, CUR>.() -> PipelineBuilder<IN, OUT>,
  ): EncodeStage<IN, OUT, OPTS> = apply {
    pipeline = PipelineBuilder.start<IN>().startWith(start).block().build()
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

private fun defaultDynamicImageEncodePipeline(): EncodePipeline<Decoded<ImageFormat, ImageIR>, EncodedBytes<ImageFormat>> =
  PipelineBuilder.start<Decoded<ImageFormat, ImageIR>>()
    .then(ImageDynamicEncodeHandler())
    .build()

private fun defaultDynamicAudioEncodePipeline(): EncodePipeline<Decoded<AudioFormat, AudioIR>, EncodedBytes<AudioFormat>> =
  PipelineBuilder.start<Decoded<AudioFormat, AudioIR>>()
    .then(AudioDynamicEncodeHandler())
    .build()

private fun defaultDynamicVideoEncodePipeline(): EncodePipeline<Decoded<VideoFormat, VideoIR>, EncodedBytes<VideoFormat>> =
  PipelineBuilder.start<Decoded<VideoFormat, VideoIR>>()
    .then(VideoDynamicEncodeHandler())
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
