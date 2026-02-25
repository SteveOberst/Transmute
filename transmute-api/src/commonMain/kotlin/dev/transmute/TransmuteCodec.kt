package dev.transmute

import dev.transmute.audio.AudioDecodeOptions
import dev.transmute.audio.AudioEncodeOptions
import dev.transmute.audio.AudioFormat
import dev.transmute.audio.AudioFormatDetector
import dev.transmute.audio.AudioIR
import dev.transmute.audio.AudioRegistries
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
import dev.transmute.image.ImageEncodeOptions
import dev.transmute.image.ImageFormat
import dev.transmute.image.ImageFormatDetector
import dev.transmute.image.ImageIR
import dev.transmute.image.ImageRegistries
import dev.transmute.video.CanonicalVideoDecodeOptions
import dev.transmute.video.CanonicalVideoEncodeOptions
import dev.transmute.video.VideoDecodeOptions
import dev.transmute.video.VideoEncodeOptions
import dev.transmute.video.VideoFormat
import dev.transmute.video.VideoFormatDetector
import dev.transmute.video.VideoIR
import dev.transmute.video.VideoRegistries

class TransmuteCodec internal constructor() {
  val image: ImageCodec = ImageCodec()
  val audio: AudioCodec = AudioCodec()
  val video: VideoCodec = VideoCodec()
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

class ImageCodec internal constructor() {
  private val defaultDecodePipeline: DecodePipeline<Bytes, Decoded<ImageFormat, ImageIR>> =
    defaultImageBytesDecodePipeline()
  private val defaultEncodePipeline: EncodePipeline<Decoded<ImageFormat, ImageIR>, EncodedBytes<ImageFormat>> =
    defaultDynamicImageEncodePipeline()

  fun detectFormat(source: Bytes): ImageFormat = ImageFormatDetector.detect(source)

  fun defaultDecoder(): ConfiguredDecoder<ImageFormat, Decoded<ImageFormat, ImageIR>, ImageDecodeOptions> =
    ConfiguredDecoder(
      options = CanonicalImageDecodeOptions(),
      pipeline = defaultDecodePipeline,
      sniffFormat = { bytes -> detectFormat(bytes).takeUnless { it == ImageFormat.Unknown } },
      decodableFormatsProvider = {
        ImageRegistries.installDefaultsIfEmpty()
        ImageRegistries.decoders.supportedFormats
      },
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
      decodableFormatsProvider = {
        ImageRegistries.installDefaultsIfEmpty()
        ImageRegistries.decoders.supportedFormats
      },
    )
  }

  fun defaultEncoder(): ConfiguredEncoder<ImageFormat, Decoded<ImageFormat, ImageIR>, ImageEncodeOptions> =
    ConfiguredEncoder(
      options = CanonicalImageEncodeOptions(),
      pipeline = defaultEncodePipeline,
      encodableFormatsProvider = {
        ImageRegistries.installDefaultsIfEmpty()
        ImageRegistries.encoders.supportedFormats
      },
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
      encodableFormatsProvider = {
        ImageRegistries.installDefaultsIfEmpty()
        ImageRegistries.encoders.supportedFormats
      },
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

class AudioCodec internal constructor() {
  private val defaultDecodePipeline: DecodePipeline<Bytes, Decoded<AudioFormat, AudioIR>> =
    defaultAudioBytesDecodePipeline()
  private val defaultEncodePipeline: EncodePipeline<Decoded<AudioFormat, AudioIR>, EncodedBytes<AudioFormat>> =
    defaultDynamicAudioEncodePipeline()

  fun detectFormat(source: Bytes): AudioFormat = AudioFormatDetector.detect(source)

  fun defaultDecoder(): ConfiguredDecoder<AudioFormat, Decoded<AudioFormat, AudioIR>, AudioDecodeOptions> =
    ConfiguredDecoder(
      options = CanonicalAudioDecodeOptions(),
      pipeline = defaultDecodePipeline,
      sniffFormat = { bytes -> detectFormat(bytes).takeUnless { it == AudioFormat.Unknown } },
      decodableFormatsProvider = {
        AudioRegistries.installDefaultsIfEmpty()
        AudioRegistries.decoders.supportedFormats
      },
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
      decodableFormatsProvider = {
        AudioRegistries.installDefaultsIfEmpty()
        AudioRegistries.decoders.supportedFormats
      },
    )
  }

  fun defaultEncoder(): ConfiguredEncoder<AudioFormat, Decoded<AudioFormat, AudioIR>, AudioEncodeOptions> =
    ConfiguredEncoder(
      options = CanonicalAudioEncodeOptions(),
      pipeline = defaultEncodePipeline,
      encodableFormatsProvider = {
        AudioRegistries.installDefaultsIfEmpty()
        AudioRegistries.encoders.supportedFormats
      },
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
      encodableFormatsProvider = {
        AudioRegistries.installDefaultsIfEmpty()
        AudioRegistries.encoders.supportedFormats
      },
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

class VideoCodec internal constructor() {
  private val defaultDecodePipeline: DecodePipeline<Bytes, Decoded<VideoFormat, VideoIR>> =
    defaultVideoBytesDecodePipeline()
  private val defaultEncodePipeline: EncodePipeline<Decoded<VideoFormat, VideoIR>, EncodedBytes<VideoFormat>> =
    defaultDynamicVideoEncodePipeline()

  fun detectFormat(source: Bytes): VideoFormat = VideoFormatDetector.detect(source)

  fun defaultDecoder(): ConfiguredDecoder<VideoFormat, Decoded<VideoFormat, VideoIR>, VideoDecodeOptions> =
    ConfiguredDecoder(
      options = CanonicalVideoDecodeOptions(),
      pipeline = defaultDecodePipeline,
      sniffFormat = { bytes -> detectFormat(bytes).takeUnless { it == VideoFormat.Unknown } },
      decodableFormatsProvider = {
        VideoRegistries.installDefaultsIfEmpty()
        VideoRegistries.decoders.supportedFormats
      },
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
      decodableFormatsProvider = {
        VideoRegistries.installDefaultsIfEmpty()
        VideoRegistries.decoders.supportedFormats
      },
    )
  }

  fun defaultEncoder(): ConfiguredEncoder<VideoFormat, Decoded<VideoFormat, VideoIR>, VideoEncodeOptions> =
    ConfiguredEncoder(
      options = CanonicalVideoEncodeOptions(),
      pipeline = defaultEncodePipeline,
      encodableFormatsProvider = {
        VideoRegistries.installDefaultsIfEmpty()
        VideoRegistries.encoders.supportedFormats
      },
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
      encodableFormatsProvider = {
        VideoRegistries.installDefaultsIfEmpty()
        VideoRegistries.encoders.supportedFormats
      },
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
