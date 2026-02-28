package dev.transmute

import dev.transmute.audio.AudioDecodeOptions
import dev.transmute.audio.AudioDecoderRegistry
import dev.transmute.audio.AudioEncodeOptions
import dev.transmute.audio.AudioEncoderRegistry
import dev.transmute.audio.AudioFormat
import dev.transmute.audio.AudioFormatDetector
import dev.transmute.audio.AudioIR
import dev.transmute.audio.AudioRegistries
import dev.transmute.audio.MutableAudioDecoderRegistry
import dev.transmute.audio.MutableAudioEncoderRegistry
import dev.transmute.audio.CanonicalAudioDecodeOptions
import dev.transmute.audio.CanonicalAudioEncodeOptions
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.DecodeOptions
import dev.transmute.codec.Decoder
import dev.transmute.codec.Encoder
import dev.transmute.model.core.EncodeOptions
import dev.transmute.model.core.MediaFormat
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.core.NoEncodeOptions
import dev.transmute.common.PipelineContext
import dev.transmute.codec.pipeline.DecodePipeline
import dev.transmute.codec.pipeline.Decoded
import dev.transmute.codec.pipeline.EncodePipeline
import dev.transmute.codec.pipeline.EncodedBytes
import dev.transmute.image.CanonicalImageDecodeOptions
import dev.transmute.image.CanonicalImageEncodeOptions
import dev.transmute.image.ImageDecodeOptions
import dev.transmute.image.ImageDecoderRegistry
import dev.transmute.image.ImageEncodeOptions
import dev.transmute.image.ImageEncoderRegistry
import dev.transmute.image.ImageFormat
import dev.transmute.image.ImageFormatDetector
import dev.transmute.image.ImageIR
import dev.transmute.image.ImageRegistries
import dev.transmute.image.MutableImageDecoderRegistry
import dev.transmute.image.MutableImageEncoderRegistry
import dev.transmute.video.CanonicalVideoDecodeOptions
import dev.transmute.video.CanonicalVideoEncodeOptions
import dev.transmute.video.VideoDecodeOptions
import dev.transmute.video.VideoDecoderRegistry
import dev.transmute.video.VideoEncodeOptions
import dev.transmute.video.VideoEncoderRegistry
import dev.transmute.video.VideoFormat
import dev.transmute.video.VideoFormatDetector
import dev.transmute.video.VideoIR
import dev.transmute.video.VideoRegistries
import dev.transmute.video.MutableVideoDecoderRegistry
import dev.transmute.video.MutableVideoEncoderRegistry
import dev.transmute.model.core.MediaStructure
import dev.transmute.model.core.RawMediaStructure

class TransmuteCodec internal constructor(
  imageDecoderRegistry: ImageDecoderRegistry? = null,
  imageEncoderRegistry: ImageEncoderRegistry? = null,
  audioDecoderRegistry: AudioDecoderRegistry? = null,
  audioEncoderRegistry: AudioEncoderRegistry? = null,
  videoDecoderRegistry: VideoDecoderRegistry? = null,
  videoEncoderRegistry: VideoEncoderRegistry? = null,
  private val imageRawStructureDecoderRegistry: MutableDecoderRegistry<ImageFormat, RawMediaStructure>? = null,
  private val imageStructureDecoderRegistry: MutableDecoderRegistry<ImageFormat, MediaStructure>? = null,
  private val audioRawStructureDecoderRegistry: MutableDecoderRegistry<AudioFormat, RawMediaStructure>? = null,
  private val audioStructureDecoderRegistry: MutableDecoderRegistry<AudioFormat, MediaStructure>? = null,
  private val videoRawStructureDecoderRegistry: MutableDecoderRegistry<VideoFormat, RawMediaStructure>? = null,
  private val videoStructureDecoderRegistry: MutableDecoderRegistry<VideoFormat, MediaStructure>? = null,
) {
  val image: ImageCodec = ImageCodec(imageDecoderRegistry, imageEncoderRegistry)
  val audio: AudioCodec = AudioCodec(audioDecoderRegistry, audioEncoderRegistry)
  val video: VideoCodec = VideoCodec(videoDecoderRegistry, videoEncoderRegistry)

  /**
   * Decode [source] bytes into a [RawMediaStructure] for the given [format].
   *
   * Requires a [RawMediaStructure] decoder registered for [format] (e.g. via
   * `scope.codecs.image.rawStructureDecoders.register(ImageFormat.Png, PngRawDecoder())`).
   *
   * @throws IllegalArgumentException if no decoder is registered for [format].
   */
  suspend fun decodeRawStructure(source: Bytes, format: MediaFormat<*, *>): RawMediaStructure {
    val decoder = when (format) {
      is ImageFormat -> imageRawStructureDecoderRegistry?.get(format)
      is AudioFormat -> audioRawStructureDecoderRegistry?.get(format)
      is VideoFormat -> videoRawStructureDecoderRegistry?.get(format)
      else -> null
    } ?: throw IllegalArgumentException("No raw structure decoder registered for format: $format")
    val ctx = createContext(loggerOverride = null, decodeOptions = NoDecodeOptions, encodeOptions = NoEncodeOptions)
    @Suppress("UNCHECKED_CAST")
    return decoder.decode(source, NoDecodeOptions, ctx)
  }

  /**
   * Decode [source] bytes into a [MediaStructure] for the given [format].
   *
   * Requires a [MediaStructure] decoder registered for [format].
   *
   * @throws IllegalArgumentException if no decoder is registered for [format].
   */
  suspend fun decodeStructure(source: Bytes, format: MediaFormat<*, *>): MediaStructure {
    val decoder = when (format) {
      is ImageFormat -> imageStructureDecoderRegistry?.get(format)
      is AudioFormat -> audioStructureDecoderRegistry?.get(format)
      is VideoFormat -> videoStructureDecoderRegistry?.get(format)
      else -> null
    } ?: throw IllegalArgumentException("No structure decoder registered for format: $format")
    val ctx = createContext(loggerOverride = null, decodeOptions = NoDecodeOptions, encodeOptions = NoEncodeOptions)
    @Suppress("UNCHECKED_CAST")
    return decoder.decode(source, NoDecodeOptions, ctx)
  }

  /**
   * Returns `true` if a [MediaStructure] decoder is registered for [format].
   */
  fun hasStructureDecoder(format: MediaFormat<*, *>): Boolean = when (format) {
    is ImageFormat -> imageStructureDecoderRegistry?.get(format) != null
    is AudioFormat -> audioStructureDecoderRegistry?.get(format) != null
    is VideoFormat -> videoStructureDecoderRegistry?.get(format) != null
    else -> false
  }
}

data class ConfiguredDecoder<F : MediaFormat<*, *>, OUT, OPTS : DecodeOptions>(
  val options: OPTS,
  val pipeline: DecodePipeline<Bytes, OUT>,
  private val sniffFormat: (Bytes) -> F?,
  private val decodableFormatsProvider: () -> Set<F>,
) : Decoder<F, OUT, OPTS> {
  override val decodableFormats: Set<F> get() = decodableFormatsProvider()
  override fun sniff(data: Bytes): F? = sniffFormat(data)
  override suspend fun decode(source: Bytes, options: OPTS, context: PipelineContext): OUT =
    pipeline.run(source, context.copy(decodeOptions = options))
}

data class ConfiguredEncoder<F : MediaFormat<*, *>, IN, OPTS : EncodeOptions>(
  val options: OPTS,
  val pipeline: EncodePipeline<IN, EncodedBytes<F>>,
  private val encodableFormatsProvider: () -> Set<F>,
) : Encoder<F, IN, OPTS> {
  override val encodableFormats: Set<F> get() = encodableFormatsProvider()

  override suspend fun encode(
    ir: IN,
    format: F,
    options: OPTS,
    context: PipelineContext,
  ): Bytes {
    val out = pipeline.run(ir, context.copy(encodeOptions = options))
    require(out.format == format) { "Encoded format ${out.format} does not match requested format=$format" }
    return out.bytes
  }
}

class ImageCodec internal constructor(
  private val decoderRegistry: ImageDecoderRegistry? = null,
  private val encoderRegistry: ImageEncoderRegistry? = null,
) {
  private val defaultDecodePipeline: DecodePipeline<Bytes, Decoded<ImageFormat, ImageIR>> =
    defaultImageBytesDecodePipeline(decoderRegistry)
  private val defaultEncodePipeline: EncodePipeline<Decoded<ImageFormat, ImageIR>, EncodedBytes<ImageFormat>> =
    defaultDynamicImageEncodePipeline(encoderRegistry)

  private fun resolveDecodableFormats(): Set<ImageFormat> {
    if (decoderRegistry == null) ImageRegistries.installDefaultsIfEmpty()
    return (decoderRegistry ?: ImageRegistries.decoders).let { reg ->
      if (reg is MutableImageDecoderRegistry) reg.supportedFormats else emptySet()
    }
  }

  private fun resolveEncodableFormats(): Set<ImageFormat> {
    if (encoderRegistry == null) ImageRegistries.installDefaultsIfEmpty()
    return (encoderRegistry ?: ImageRegistries.encoders).let { reg ->
      if (reg is MutableImageEncoderRegistry) reg.supportedFormats else emptySet()
    }
  }

  /** All image formats for which a decoder is registered. */
  val decodableFormats: Set<ImageFormat> get() = resolveDecodableFormats()

  /** All image formats for which an encoder is registered. */
  val encodableFormats: Set<ImageFormat> get() = resolveEncodableFormats()

  fun detectFormat(source: Bytes): ImageFormat = ImageFormatDetector.detect(source)

  fun defaultDecoder(): ConfiguredDecoder<ImageFormat, Decoded<ImageFormat, ImageIR>, ImageDecodeOptions> =
    ConfiguredDecoder(
      options = CanonicalImageDecodeOptions(),
      pipeline = defaultDecodePipeline,
      sniffFormat = { bytes -> detectFormat(bytes).takeUnless { it == ImageFormat.Unknown } },
      decodableFormatsProvider = ::resolveDecodableFormats,
    )

  fun decoder(
    block: DecodeStage<Bytes, Decoded<ImageFormat, ImageIR>, ImageDecodeOptions>.() -> Unit,
  ): ConfiguredDecoder<ImageFormat, Decoded<ImageFormat, ImageIR>, ImageDecodeOptions> {
    val stage = DecodeStage<Bytes, Decoded<ImageFormat, ImageIR>, ImageDecodeOptions>(CanonicalImageDecodeOptions())
    stage.block()
    val pipeline = stage.pipeline ?: error("No decode pipeline configured; call pipeline(...) inside decoder { ... }")
    return ConfiguredDecoder(
      options = stage.options,
      pipeline = pipeline,
      sniffFormat = { bytes -> detectFormat(bytes).takeUnless { it == ImageFormat.Unknown } },
      decodableFormatsProvider = ::resolveDecodableFormats,
    )
  }

  fun defaultEncoder(): ConfiguredEncoder<ImageFormat, Decoded<ImageFormat, ImageIR>, ImageEncodeOptions> =
    ConfiguredEncoder(
      options = CanonicalImageEncodeOptions(),
      pipeline = defaultEncodePipeline,
      encodableFormatsProvider = ::resolveEncodableFormats,
    )

  fun encoder(
    block: EncodeStage<Decoded<ImageFormat, ImageIR>, EncodedBytes<ImageFormat>, ImageEncodeOptions>.() -> Unit,
  ): ConfiguredEncoder<ImageFormat, Decoded<ImageFormat, ImageIR>, ImageEncodeOptions> {
    val stage =
      EncodeStage<Decoded<ImageFormat, ImageIR>, EncodedBytes<ImageFormat>, ImageEncodeOptions>(CanonicalImageEncodeOptions())
    stage.block()
    val pipeline = stage.pipeline ?: error("No encode pipeline configured; call pipeline(...) inside encoder { ... }")
    return ConfiguredEncoder(
      options = stage.options,
      pipeline = pipeline,
      encodableFormatsProvider = ::resolveEncodableFormats,
    )
  }

  suspend fun decode(
    source: Bytes,
    options: ImageDecodeOptions = CanonicalImageDecodeOptions(),
  ): Decoded<ImageFormat, ImageIR> {
    val ctx = createContext(loggerOverride = null, decodeOptions = options, encodeOptions = NoEncodeOptions)
    return defaultDecodePipeline.run(source, ctx)
  }

  suspend fun encode(
    decoded: Decoded<ImageFormat, ImageIR>,
    options: ImageEncodeOptions = CanonicalImageEncodeOptions(),
  ): EncodedBytes<ImageFormat> {
    val ctx = createContext(loggerOverride = null, decodeOptions = NoDecodeOptions, encodeOptions = options)
    return defaultEncodePipeline.run(decoded, ctx)
  }
}

class AudioCodec internal constructor(
  private val decoderRegistry: AudioDecoderRegistry? = null,
  private val encoderRegistry: AudioEncoderRegistry? = null,
) {
  private val defaultDecodePipeline: DecodePipeline<Bytes, Decoded<AudioFormat, AudioIR>> =
    defaultAudioBytesDecodePipeline(decoderRegistry)
  private val defaultEncodePipeline: EncodePipeline<Decoded<AudioFormat, AudioIR>, EncodedBytes<AudioFormat>> =
    defaultDynamicAudioEncodePipeline(encoderRegistry)

  private fun resolveDecodableFormats(): Set<AudioFormat> {
    if (decoderRegistry == null) AudioRegistries.installDefaultsIfEmpty()
    return (decoderRegistry ?: AudioRegistries.decoders).let { reg ->
      if (reg is MutableAudioDecoderRegistry) reg.supportedFormats else emptySet()
    }
  }

  private fun resolveEncodableFormats(): Set<AudioFormat> {
    if (encoderRegistry == null) AudioRegistries.installDefaultsIfEmpty()
    return (encoderRegistry ?: AudioRegistries.encoders).let { reg ->
      if (reg is MutableAudioEncoderRegistry) reg.supportedFormats else emptySet()
    }
  }

  /** All audio formats for which a decoder is registered. */
  val decodableFormats: Set<AudioFormat> get() = resolveDecodableFormats()

  /** All audio formats for which an encoder is registered. */
  val encodableFormats: Set<AudioFormat> get() = resolveEncodableFormats()

  fun detectFormat(source: Bytes): AudioFormat = AudioFormatDetector.detect(source)

  fun defaultDecoder(): ConfiguredDecoder<AudioFormat, Decoded<AudioFormat, AudioIR>, AudioDecodeOptions> =
    ConfiguredDecoder(
      options = CanonicalAudioDecodeOptions(),
      pipeline = defaultDecodePipeline,
      sniffFormat = { bytes -> detectFormat(bytes).takeUnless { it == AudioFormat.Unknown } },
      decodableFormatsProvider = ::resolveDecodableFormats,
    )

  fun decoder(
    block: DecodeStage<Bytes, Decoded<AudioFormat, AudioIR>, AudioDecodeOptions>.() -> Unit,
  ): ConfiguredDecoder<AudioFormat, Decoded<AudioFormat, AudioIR>, AudioDecodeOptions> {
    val stage = DecodeStage<Bytes, Decoded<AudioFormat, AudioIR>, AudioDecodeOptions>(CanonicalAudioDecodeOptions())
    stage.block()
    val pipeline = stage.pipeline ?: error("No decode pipeline configured; call pipeline(...) inside decoder { ... }")
    return ConfiguredDecoder(
      options = stage.options,
      pipeline = pipeline,
      sniffFormat = { bytes -> detectFormat(bytes).takeUnless { it == AudioFormat.Unknown } },
      decodableFormatsProvider = ::resolveDecodableFormats,
    )
  }

  fun defaultEncoder(): ConfiguredEncoder<AudioFormat, Decoded<AudioFormat, AudioIR>, AudioEncodeOptions> =
    ConfiguredEncoder(
      options = CanonicalAudioEncodeOptions(),
      pipeline = defaultEncodePipeline,
      encodableFormatsProvider = ::resolveEncodableFormats,
    )

  fun encoder(
    block: EncodeStage<Decoded<AudioFormat, AudioIR>, EncodedBytes<AudioFormat>, AudioEncodeOptions>.() -> Unit,
  ): ConfiguredEncoder<AudioFormat, Decoded<AudioFormat, AudioIR>, AudioEncodeOptions> {
    val stage =
      EncodeStage<Decoded<AudioFormat, AudioIR>, EncodedBytes<AudioFormat>, AudioEncodeOptions>(CanonicalAudioEncodeOptions())
    stage.block()
    val pipeline = stage.pipeline ?: error("No encode pipeline configured; call pipeline(...) inside encoder { ... }")
    return ConfiguredEncoder(
      options = stage.options,
      pipeline = pipeline,
      encodableFormatsProvider = ::resolveEncodableFormats,
    )
  }

  suspend fun decode(
    source: Bytes,
    options: AudioDecodeOptions = CanonicalAudioDecodeOptions(),
  ): Decoded<AudioFormat, AudioIR> {
    val ctx = createContext(loggerOverride = null, decodeOptions = options, encodeOptions = NoEncodeOptions)
    return defaultDecodePipeline.run(source, ctx)
  }

  suspend fun encode(
    decoded: Decoded<AudioFormat, AudioIR>,
    options: AudioEncodeOptions = CanonicalAudioEncodeOptions(),
  ): EncodedBytes<AudioFormat> {
    val ctx = createContext(loggerOverride = null, decodeOptions = NoDecodeOptions, encodeOptions = options)
    return defaultEncodePipeline.run(decoded, ctx)
  }
}

class VideoCodec internal constructor(
  private val decoderRegistry: VideoDecoderRegistry? = null,
  private val encoderRegistry: VideoEncoderRegistry? = null,
) {
  private val defaultDecodePipeline: DecodePipeline<Bytes, Decoded<VideoFormat, VideoIR>> =
    defaultVideoBytesDecodePipeline(decoderRegistry)
  private val defaultEncodePipeline: EncodePipeline<Decoded<VideoFormat, VideoIR>, EncodedBytes<VideoFormat>> =
    defaultDynamicVideoEncodePipeline(encoderRegistry)

  private fun resolveDecodableFormats(): Set<VideoFormat> {
    if (decoderRegistry == null) VideoRegistries.installDefaultsIfEmpty()
    return (decoderRegistry ?: VideoRegistries.decoders).let { reg ->
      if (reg is MutableVideoDecoderRegistry) reg.supportedFormats else emptySet()
    }
  }

  private fun resolveEncodableFormats(): Set<VideoFormat> {
    if (encoderRegistry == null) VideoRegistries.installDefaultsIfEmpty()
    return (encoderRegistry ?: VideoRegistries.encoders).let { reg ->
      if (reg is MutableVideoEncoderRegistry) reg.supportedFormats else emptySet()
    }
  }

  /** All video formats for which a decoder is registered. */
  val decodableFormats: Set<VideoFormat> get() = resolveDecodableFormats()

  /** All video formats for which an encoder is registered. */
  val encodableFormats: Set<VideoFormat> get() = resolveEncodableFormats()

  fun detectFormat(source: Bytes): VideoFormat = VideoFormatDetector.detect(source)

  fun defaultDecoder(): ConfiguredDecoder<VideoFormat, Decoded<VideoFormat, VideoIR>, VideoDecodeOptions> =
    ConfiguredDecoder(
      options = CanonicalVideoDecodeOptions(),
      pipeline = defaultDecodePipeline,
      sniffFormat = { bytes -> detectFormat(bytes).takeUnless { it == VideoFormat.Unknown } },
      decodableFormatsProvider = ::resolveDecodableFormats,
    )

  fun decoder(
    block: DecodeStage<Bytes, Decoded<VideoFormat, VideoIR>, VideoDecodeOptions>.() -> Unit,
  ): ConfiguredDecoder<VideoFormat, Decoded<VideoFormat, VideoIR>, VideoDecodeOptions> {
    val stage = DecodeStage<Bytes, Decoded<VideoFormat, VideoIR>, VideoDecodeOptions>(CanonicalVideoDecodeOptions())
    stage.block()
    val pipeline = stage.pipeline ?: error("No decode pipeline configured; call pipeline(...) inside decoder { ... }")
    return ConfiguredDecoder(
      options = stage.options,
      pipeline = pipeline,
      sniffFormat = { bytes -> detectFormat(bytes).takeUnless { it == VideoFormat.Unknown } },
      decodableFormatsProvider = ::resolveDecodableFormats,
    )
  }

  fun defaultEncoder(): ConfiguredEncoder<VideoFormat, Decoded<VideoFormat, VideoIR>, VideoEncodeOptions> =
    ConfiguredEncoder(
      options = CanonicalVideoEncodeOptions(),
      pipeline = defaultEncodePipeline,
      encodableFormatsProvider = ::resolveEncodableFormats,
    )

  fun encoder(
    block: EncodeStage<Decoded<VideoFormat, VideoIR>, EncodedBytes<VideoFormat>, VideoEncodeOptions>.() -> Unit,
  ): ConfiguredEncoder<VideoFormat, Decoded<VideoFormat, VideoIR>, VideoEncodeOptions> {
    val stage =
      EncodeStage<Decoded<VideoFormat, VideoIR>, EncodedBytes<VideoFormat>, VideoEncodeOptions>(CanonicalVideoEncodeOptions())
    stage.block()
    val pipeline = stage.pipeline ?: error("No encode pipeline configured; call pipeline(...) inside encoder { ... }")
    return ConfiguredEncoder(
      options = stage.options,
      pipeline = pipeline,
      encodableFormatsProvider = ::resolveEncodableFormats,
    )
  }

  suspend fun decode(
    source: Bytes,
    options: VideoDecodeOptions = CanonicalVideoDecodeOptions(),
  ): Decoded<VideoFormat, VideoIR> {
    val ctx = createContext(loggerOverride = null, decodeOptions = options, encodeOptions = NoEncodeOptions)
    return defaultDecodePipeline.run(source, ctx)
  }

  suspend fun encode(
    decoded: Decoded<VideoFormat, VideoIR>,
    options: VideoEncodeOptions = CanonicalVideoEncodeOptions(),
  ): EncodedBytes<VideoFormat> {
    val ctx = createContext(loggerOverride = null, decodeOptions = NoDecodeOptions, encodeOptions = options)
    return defaultEncodePipeline.run(decoded, ctx)
  }
}
