package dev.transmute

import dev.transmute.audio.*
import dev.transmute.codec.pipeline.*
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.DecodeOptions
import dev.transmute.model.core.EncodeOptions
import dev.transmute.model.core.MediaFormat
import dev.transmute.model.core.RawMediaStructure
import dev.transmute.common.PipelineContext
import dev.transmute.common.TransmuteContext
import dev.transmute.common.TransmuteLogger
import dev.transmute.common.TransmuteLogging
import dev.transmute.model.core.asBytes
import dev.transmute.image.*
import dev.transmute.plugin.AggregateDiagnostics
import dev.transmute.plugin.InstalledPluginInfo
import dev.transmute.plugin.PluginInstallation
import dev.transmute.plugin.ServiceRegistry
import dev.transmute.plugin.TransmutePlugin
import dev.transmute.plugin.TransmuteScope
import dev.transmute.plugin.installPlatformAudioDefaults
import dev.transmute.plugin.installPlatformImageDefaults
import dev.transmute.plugin.installPlatformVideoDefaults
import dev.transmute.plugin.sortPluginInstallations
import dev.transmute.video.*
import dev.transmute.structure.DefaultStructureDecoders
import dev.transmute.structure.image.*
import dev.transmute.structure.audio.*
import dev.transmute.structure.video.*
import dev.transmute.model.core.MediaStructureRegistry
import dev.transmute.model.structure.image.*
import dev.transmute.model.structure.audio.*
import dev.transmute.model.structure.video.*

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
class Transmute private constructor(
  /** Low-level decode / encode / format detection. */
  val codec: TransmuteCodec,

  /** Decode-less format detection and lightweight probing. */
  val inspect: TransmuteInspect,

  val image: TransmuteImage,
  val audio: TransmuteAudio,
  val video: TransmuteVideo,

  /** Aggregated diagnostics from all installed plugins. */
  val diagnostics: AggregateDiagnostics = AggregateDiagnostics(),

  /** Shared service registry populated by plugins. */
  val services: ServiceRegistry = ServiceRegistry(),

  /** Plugin installations (kept for lifecycle management). */
  private val installations: List<PluginInstallation<*>> = emptyList(),
) {

  /** Metadata for all installed plugins (key, features, dependencies). */
  val installedPlugins: List<InstalledPluginInfo>
    get() = installations.map { inst ->
      InstalledPluginInfo(
        key = inst.plugin.key,
        features = inst.plugin.features,
        dependsOn = inst.plugin.dependsOn,
      )
    }

  suspend fun transmute(type: TransmuteType, source: ByteArray): ByteArray = when (type) {
    TransmuteType.Image -> image().transmute(source.asBytes()).bytes.data
    TransmuteType.Audio -> audio().transmute(source.asBytes()).bytes.data
    TransmuteType.Video -> video().transmute(source.asBytes()).bytes.data
  }

  /**
   * Close this Transmute instance, releasing resources held by plugins.
   *
   * Calls [PluginLifecycle.onClose] on all installed plugins that implement it.
   */
  fun close() {
    for (installation in installations) {
      installation.fireOnClose()
    }
  }

  // -- Builder DSL ----------------------------------------------------------

  class Builder internal constructor() {
    private var loggerOverride: TransmuteLogger? = null
    private val pluginInstallations = mutableListOf<PluginInstallation<*>>()

    /** Override the default logger for this instance. */
    fun logger(logger: TransmuteLogger): Builder = apply { loggerOverride = logger }

    /** Install plugins that register decoders, encoders, and other extensions. */
    fun plugins(block: PluginBlock.() -> Unit): Builder = apply {
      PluginBlock(pluginInstallations).block()
    }

    fun build(): Transmute {
      // Create grouped codec registries for this instance
      val imageCodecs = ImageCodecRegistry()
      val audioCodecs = AudioCodecRegistry()
      val videoCodecs = VideoCodecRegistry()
      val codecs = CodecRegistry(imageCodecs, audioCodecs, videoCodecs)
      val services = ServiceRegistry()
      val aggregateDiagnostics = AggregateDiagnostics()

      // Install platform defaults into the local registries
      installPlatformImageDefaults(imageCodecs.decoders, imageCodecs.encoders)
      installPlatformAudioDefaults(audioCodecs.decoders, audioCodecs.encoders)
      installPlatformVideoDefaults(videoCodecs.decoders, videoCodecs.encoders)

      // Register built-in structure decoders (raw + full) via DefaultStructureDecoders
      with(DefaultStructureDecoders) {
          imageCodecs.rawStructureDecoders.register(ImageFormat.Png,  pngRaw)
          imageCodecs.rawStructureDecoders.register(ImageFormat.Jpeg, jpegRaw)
          imageCodecs.rawStructureDecoders.register(ImageFormat.Bmp,  bmpRaw)
          imageCodecs.rawStructureDecoders.register(ImageFormat.Gif,  gifRaw)
          imageCodecs.rawStructureDecoders.register(ImageFormat.Tiff, tiffRaw)
          imageCodecs.rawStructureDecoders.register(ImageFormat.Webp, webpRaw)
          imageCodecs.rawStructureDecoders.register(ImageFormat.Heif, heifRaw)
          imageCodecs.rawStructureDecoders.register(ImageFormat.Avif, avifRaw)
          audioCodecs.rawStructureDecoders.register(AudioFormat.Wav,  wavRaw)
          audioCodecs.rawStructureDecoders.register(AudioFormat.Mp3,  mp3Raw)
          audioCodecs.rawStructureDecoders.register(AudioFormat.Flac, flacRaw)
          audioCodecs.rawStructureDecoders.register(AudioFormat.Aac,  aacRaw)
          audioCodecs.rawStructureDecoders.register(AudioFormat.M4a,  m4aRaw)
          audioCodecs.rawStructureDecoders.register(AudioFormat.Ogg,  oggAudioRaw)
          audioCodecs.rawStructureDecoders.register(AudioFormat.Opus, opusRaw)
          videoCodecs.rawStructureDecoders.register(VideoFormat.Mp4,  mp4Raw)
          videoCodecs.rawStructureDecoders.register(VideoFormat.Mov,  movRaw)
          videoCodecs.rawStructureDecoders.register(VideoFormat.Webm, webmRaw)
          videoCodecs.rawStructureDecoders.register(VideoFormat.Mkv,  mkvRaw)
          videoCodecs.rawStructureDecoders.register(VideoFormat.Avi,  aviRaw)

          imageCodecs.structureDecoders.register(ImageFormat.Png,  png)
          imageCodecs.structureDecoders.register(ImageFormat.Jpeg, jpeg)
          imageCodecs.structureDecoders.register(ImageFormat.Bmp,  bmp)
          imageCodecs.structureDecoders.register(ImageFormat.Gif,  gif)
          imageCodecs.structureDecoders.register(ImageFormat.Tiff, tiff)
          imageCodecs.structureDecoders.register(ImageFormat.Webp, webp)
          imageCodecs.structureDecoders.register(ImageFormat.Heif, heif)
          imageCodecs.structureDecoders.register(ImageFormat.Avif, avif)
          audioCodecs.structureDecoders.register(AudioFormat.Wav,  wav)
          audioCodecs.structureDecoders.register(AudioFormat.Mp3,  mp3)
          audioCodecs.structureDecoders.register(AudioFormat.Flac, flac)
          audioCodecs.structureDecoders.register(AudioFormat.Aac,  aac)
          audioCodecs.structureDecoders.register(AudioFormat.M4a,  m4a)
          audioCodecs.structureDecoders.register(AudioFormat.Ogg,  oggAudio)
          audioCodecs.structureDecoders.register(AudioFormat.Opus, opus)
          videoCodecs.structureDecoders.register(VideoFormat.Mp4,  mp4)
          videoCodecs.structureDecoders.register(VideoFormat.Mov,  mov)
          videoCodecs.structureDecoders.register(VideoFormat.Webm, webm)
          videoCodecs.structureDecoders.register(VideoFormat.Mkv,  mkv)
          videoCodecs.structureDecoders.register(VideoFormat.Avi,  avi)
      }

      // Register built-in MediaStructure types into the global serialization registry
      MediaStructureRegistry.register<PngStructure>("transmute.png",       PngStructure.serializer())
      MediaStructureRegistry.register<JpegStructure>("transmute.jpeg",     JpegStructure.serializer())
      MediaStructureRegistry.register<BmpStructure>("transmute.bmp",       BmpStructure.serializer())
      MediaStructureRegistry.register<GifStructure>("transmute.gif",       GifStructure.serializer())
      MediaStructureRegistry.register<TiffStructure>("transmute.tiff",     TiffStructure.serializer())
      MediaStructureRegistry.register<WebpStructure>("transmute.webp",     WebpStructure.serializer())
      MediaStructureRegistry.register<HeifStructure>("transmute.heif",     HeifStructure.serializer())
      MediaStructureRegistry.register<AvifStructure>("transmute.avif",     AvifStructure.serializer())
      MediaStructureRegistry.register<WavStructure>("transmute.wav",       WavStructure.serializer())
      MediaStructureRegistry.register<Mp3Structure>("transmute.mp3",       Mp3Structure.serializer())
      MediaStructureRegistry.register<FlacStructure>("transmute.flac",     FlacStructure.serializer())
      MediaStructureRegistry.register<AacStructure>("transmute.aac",       AacStructure.serializer())
      MediaStructureRegistry.register<M4aStructure>("transmute.m4a",       M4aStructure.serializer())
      MediaStructureRegistry.register<OggAudioStructure>("transmute.ogg",  OggAudioStructure.serializer())
      MediaStructureRegistry.register<OpusStructure>("transmute.opus",     OpusStructure.serializer())
      MediaStructureRegistry.register<Mp4Structure>("transmute.mp4",       Mp4Structure.serializer())
      MediaStructureRegistry.register<MovStructure>("transmute.mov",       MovStructure.serializer())
      MediaStructureRegistry.register<WebmStructure>("transmute.webm",     WebmStructure.serializer())
      MediaStructureRegistry.register<MkvStructure>("transmute.mkv",       MkvStructure.serializer())
      MediaStructureRegistry.register<AviStructure>("transmute.avi",       AviStructure.serializer())

      // Sort plugins by dependency/ordering constraints
      val sorted = sortPluginInstallations(pluginInstallations)

      // Apply user-registered plugins (sorted)
      val scope = TransmuteScope(
        codecs = codecs,
        services = services,
      )
      for (installation in sorted) {
        installation.apply(scope, aggregateDiagnostics)
      }

      // Fire onInstalled for plugins implementing PluginLifecycle
      for (installation in sorted) {
        installation.fireOnInstalled()
      }

      val codec = TransmuteCodec(
        imageDecoderRegistry = imageCodecs.decoders,
        imageEncoderRegistry = imageCodecs.encoders,
        audioDecoderRegistry = audioCodecs.decoders,
        audioEncoderRegistry = audioCodecs.encoders,
        videoDecoderRegistry = videoCodecs.decoders,
        videoEncoderRegistry = videoCodecs.encoders,
        imageRawStructureDecoderRegistry = imageCodecs.rawStructureDecoders,
        imageStructureDecoderRegistry = imageCodecs.structureDecoders,
        audioRawStructureDecoderRegistry = audioCodecs.rawStructureDecoders,
        audioStructureDecoderRegistry = audioCodecs.structureDecoders,
        videoRawStructureDecoderRegistry = videoCodecs.rawStructureDecoders,
        videoStructureDecoderRegistry = videoCodecs.structureDecoders,
      )
      val inspect = TransmuteInspect(codec = codec)

      return Transmute(
        codec = codec,
        inspect = inspect,
        image = TransmuteImage(codec = codec),
        audio = TransmuteAudio(codec = codec),
        video = TransmuteVideo(codec = codec),
        diagnostics = aggregateDiagnostics,
        services = services,
        installations = sorted,
      )
    }
  }

  class PluginBlock internal constructor(
    private val installations: MutableList<PluginInstallation<*>>,
  ) {
    /** Install a plugin with optional configuration. */
    fun <C : Any> install(
      plugin: TransmutePlugin<C>,
      block: C.() -> Unit = {},
    ) {
      installations.add(PluginInstallation(plugin, block))
    }
  }

  companion object {
    /**
     * Lazily built default instance that uses platform defaults and no plugins.
     *
     * Backward-compatible: existing code using `Transmute.image`, `Transmute.codec`, etc.
     * will use this instance transparently.
     */
    val Default: Transmute by lazy { Builder().build() }

    // -- Backward-compatible delegated properties --

    val codec: TransmuteCodec get() = Default.codec
    val inspect: TransmuteInspect get() = Default.inspect
    val image: TransmuteImage get() = Default.image
    val audio: TransmuteAudio get() = Default.audio
    val video: TransmuteVideo get() = Default.video

    suspend fun transmute(type: TransmuteType, source: ByteArray): ByteArray =
      Default.transmute(type, source)
  }
}

/**
 * Top-level factory function for building a [transmute] instance.
 *
 * ```kotlin
 * val transmute = Transmute {
 *     plugins {
 *         install(GStreamer) {
 *             domains(MediaDomain.AUDIO or MediaDomain.VIDEO)
 *
 *             configure {
 *                 logging {
 *                     level(LogLevel.DEBUG)
 *                 }
 *             }
 *         }
 *     }
 * }
 * ```
 */
fun transmute(block: Transmute.Builder.() -> Unit = {}): Transmute =
  Transmute.Builder().apply(block).build()

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

internal fun defaultImageBytesDecodePipeline(
  decoders: ImageDecoderRegistry? = null,
): DecodePipeline<Bytes, Decoded<ImageFormat, ImageIR>> =
  PipelineBuilder.start<Bytes>()
    .then(if (decoders != null) ImageDecodeHandler(decoders = decoders) else ImageDecodeHandler())
    .build()

internal fun defaultAudioBytesDecodePipeline(
  decoders: AudioDecoderRegistry? = null,
): DecodePipeline<Bytes, Decoded<AudioFormat, AudioIR>> =
  PipelineBuilder.start<Bytes>()
    .then(if (decoders != null) AudioDecodeHandler(decoders = decoders) else AudioDecodeHandler())
    .build()

internal fun defaultVideoBytesDecodePipeline(
  decoders: VideoDecoderRegistry? = null,
): DecodePipeline<Bytes, Decoded<VideoFormat, VideoIR>> =
  PipelineBuilder.start<Bytes>()
    .then(if (decoders != null) VideoDecodeHandler(decoders = decoders) else VideoDecodeHandler())
    .build()

internal fun defaultDynamicImageEncodePipeline(
  encoders: ImageEncoderRegistry? = null,
): EncodePipeline<Decoded<ImageFormat, ImageIR>, EncodedBytes<ImageFormat>> =
  PipelineBuilder.start<Decoded<ImageFormat, ImageIR>>()
    .then(if (encoders != null) ImageDynamicEncodeHandler(encoders = encoders) else ImageDynamicEncodeHandler())
    .build()

internal fun defaultDynamicAudioEncodePipeline(
  encoders: AudioEncoderRegistry? = null,
): EncodePipeline<Decoded<AudioFormat, AudioIR>, EncodedBytes<AudioFormat>> =
  PipelineBuilder.start<Decoded<AudioFormat, AudioIR>>()
    .then(if (encoders != null) AudioDynamicEncodeHandler(encoders = encoders) else AudioDynamicEncodeHandler())
    .build()

internal fun defaultDynamicVideoEncodePipeline(
  encoders: VideoEncoderRegistry? = null,
): EncodePipeline<Decoded<VideoFormat, VideoIR>, EncodedBytes<VideoFormat>> =
  PipelineBuilder.start<Decoded<VideoFormat, VideoIR>>()
    .then(if (encoders != null) VideoDynamicEncodeHandler(encoders = encoders) else VideoDynamicEncodeHandler())
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
