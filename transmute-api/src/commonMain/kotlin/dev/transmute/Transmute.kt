package dev.transmute

import dev.transmute.audio.*
import dev.transmute.codec.*
import dev.transmute.codec.pipeline.*
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.DecodeOptions
import dev.transmute.model.core.EncodeOptions
import dev.transmute.model.core.MediaFormat
import dev.transmute.model.structure.MediaStructure
import dev.transmute.common.PipelineContext
import dev.transmute.common.TransmuteContext
import dev.transmute.common.TransmuteLogger
import dev.transmute.common.TransmuteLogging
import dev.transmute.model.core.asBytes
import dev.transmute.image.*
import dev.transmute.video.*

typealias DynamicImageTransmuter = ImageTransmuter<Bytes, EncodedBytes<ImageFormat>>
typealias DynamicAudioTransmuter = AudioTransmuter<Bytes, EncodedBytes<AudioFormat>>
typealias DynamicVideoTransmuter = VideoTransmuter<Bytes, EncodedBytes<VideoFormat>>

/**
 * Discriminates the three media domains at the type level.
 *
 * This dispatch type is for the *dynamic-output* transmuters.
 * If you want a type-level output format (e.g. PNG-only post-encode handlers),
 * build via `Transmute.image.to(ImageFormat.Png) { ... }` instead.
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

  private val codecInstance: TransmuteCodec = TransmuteCodec()
  private val inspectInstance: TransmuteInspect = TransmuteInspect(codec = codecInstance)
  private val structureInstance: TransmuteStructure = TransmuteStructure(inspect = inspectInstance)

  val image: TransmuteImage = TransmuteImage()
  val audio: TransmuteAudio = TransmuteAudio()
  val video: TransmuteVideo = TransmuteVideo()

  /** Read raw file bytes into [MediaStructure] objects and write them back. */
  val structure: TransmuteStructure get() = structureInstance

  fun codec(): TransmuteCodec = codecInstance

  fun inspect(): TransmuteInspect = inspectInstance

  suspend fun transmute(type: TransmuteType, source: ByteArray): ByteArray = when (type) {
    TransmuteType.Image -> image().transmute(source.asBytes()).bytes.data
    TransmuteType.Audio -> audio().transmute(source.asBytes()).bytes.data
    TransmuteType.Video -> video().transmute(source.asBytes()).bytes.data
  }
}

class DynamicImageTransmuterBuilder<IN, OUT> internal constructor(
  private val defaultDecodePipeline: (() -> DecodePipeline<IN, Decoded<ImageFormat, ImageIR>>)? = null,
  private val defaultEncodePipeline: (() -> EncodePipeline<Decoded<ImageFormat, ImageIR>, OUT>)? = null,
) {
  private val transformPipeline = TransformPipeline<ImageIR>()
  private var loggerOverride: TransmuteLogger? = null
  private var transmuteContext: TransmuteContext? = null
  private val decodeStage =
    DecodeStage<IN, Decoded<ImageFormat, ImageIR>, ImageDecodeOptions>(CanonicalImageDecodeOptions())
  private val encodeStage =
    EncodeStage<Decoded<ImageFormat, ImageIR>, OUT, ImageEncodeOptions>(CanonicalImageEncodeOptions())

  fun context(ctx: TransmuteContext): DynamicImageTransmuterBuilder<IN, OUT> = apply { transmuteContext = ctx }

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
      transmuteContext = transmuteContext,
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
  private var transmuteContext: TransmuteContext? = null
  private val decodeStage =
    DecodeStage<IN, Decoded<ImageFormat, ImageIR>, ImageDecodeOptions>(CanonicalImageDecodeOptions())
  private val encodeStage =
    EncodeStage<Decoded<ImageFormat, ImageIR>, EncodedBytes<OUT_FORMAT>, ImageEncodeOptions>(CanonicalImageEncodeOptions())

  fun context(ctx: TransmuteContext): ImageTransmuterBuilder<IN, OUT_FORMAT> = apply { transmuteContext = ctx }

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
      transmuteContext = transmuteContext,
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
    private val transmuteContext: TransmuteContext? = null,
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
      transmuteContext = transmuteContext,
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
  private var transmuteContext: TransmuteContext? = null
  private val decodeStage =
    DecodeStage<IN, Decoded<AudioFormat, AudioIR>, AudioDecodeOptions>(CanonicalAudioDecodeOptions())
  private val encodeStage =
    EncodeStage<Decoded<AudioFormat, AudioIR>, OUT, AudioEncodeOptions>(CanonicalAudioEncodeOptions())

  fun context(ctx: TransmuteContext): DynamicAudioTransmuterBuilder<IN, OUT> = apply { transmuteContext = ctx }

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
      transmuteContext = transmuteContext,
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
  private var transmuteContext: TransmuteContext? = null
  private val decodeStage =
    DecodeStage<IN, Decoded<AudioFormat, AudioIR>, AudioDecodeOptions>(CanonicalAudioDecodeOptions())
  private val encodeStage =
    EncodeStage<Decoded<AudioFormat, AudioIR>, EncodedBytes<OUT>, AudioEncodeOptions>(CanonicalAudioEncodeOptions())

  fun context(ctx: TransmuteContext): AudioTransmuterBuilder<IN, OUT> = apply { transmuteContext = ctx }

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
      transmuteContext = transmuteContext,
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
    private val transmuteContext: TransmuteContext? = null,
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
      transmuteContext = transmuteContext,
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
  private var transmuteContext: TransmuteContext? = null
  private val decodeStage =
    DecodeStage<IN, Decoded<VideoFormat, VideoIR>, VideoDecodeOptions>(CanonicalVideoDecodeOptions())
  private val encodeStage =
    EncodeStage<Decoded<VideoFormat, VideoIR>, OUT, VideoEncodeOptions>(CanonicalVideoEncodeOptions())

  fun context(ctx: TransmuteContext): DynamicVideoTransmuterBuilder<IN, OUT> = apply { transmuteContext = ctx }

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
      transmuteContext = transmuteContext,
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
  private var transmuteContext: TransmuteContext? = null
  private val decodeStage =
    DecodeStage<IN, Decoded<VideoFormat, VideoIR>, VideoDecodeOptions>(CanonicalVideoDecodeOptions())
  private val encodeStage =
    EncodeStage<Decoded<VideoFormat, VideoIR>, EncodedBytes<OUT>, VideoEncodeOptions>(CanonicalVideoEncodeOptions())

  fun context(ctx: TransmuteContext): VideoTransmuterBuilder<IN, OUT> = apply { transmuteContext = ctx }

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
      transmuteContext = transmuteContext,
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
    private val transmuteContext: TransmuteContext? = null,
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
      transmuteContext = transmuteContext,
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

// -- Shared helpers --

internal fun defaultImageBytesDecodePipeline(): DecodePipeline<Bytes, Decoded<ImageFormat, ImageIR>> =
  PipelineBuilder.start<Bytes>()
    .then(ImageDecodeHandler())
    .build()

internal fun defaultAudioBytesDecodePipeline(): DecodePipeline<Bytes, Decoded<AudioFormat, AudioIR>> =
  PipelineBuilder.start<Bytes>()
    .then(AudioDecodeHandler())
    .build()

internal fun defaultVideoBytesDecodePipeline(): DecodePipeline<Bytes, Decoded<VideoFormat, VideoIR>> =
  PipelineBuilder.start<Bytes>()
    .then(VideoDecodeHandler())
    .build()

internal fun defaultDynamicImageEncodePipeline(): EncodePipeline<Decoded<ImageFormat, ImageIR>, EncodedBytes<ImageFormat>> =
  PipelineBuilder.start<Decoded<ImageFormat, ImageIR>>()
    .then(ImageDynamicEncodeHandler())
    .build()

internal fun defaultDynamicAudioEncodePipeline(): EncodePipeline<Decoded<AudioFormat, AudioIR>, EncodedBytes<AudioFormat>> =
  PipelineBuilder.start<Decoded<AudioFormat, AudioIR>>()
    .then(AudioDynamicEncodeHandler())
    .build()

internal fun defaultDynamicVideoEncodePipeline(): EncodePipeline<Decoded<VideoFormat, VideoIR>, EncodedBytes<VideoFormat>> =
  PipelineBuilder.start<Decoded<VideoFormat, VideoIR>>()
    .then(VideoDynamicEncodeHandler())
    .build()

internal fun createContext(
    loggerOverride: TransmuteLogger?,
    decodeOptions: DecodeOptions,
    encodeOptions: EncodeOptions,
    transmuteContext: TransmuteContext? = null,
): PipelineContext = if (transmuteContext != null) {
    transmuteContext.pipelineContext(
        decodeOptions = decodeOptions,
        encodeOptions = encodeOptions,
        logger = loggerOverride ?: transmuteContext.logger,
    )
} else {
    PipelineContext(
        logger = loggerOverride ?: TransmuteLogging.logger,
        decodeOptions = decodeOptions,
        encodeOptions = encodeOptions,
    )
}
