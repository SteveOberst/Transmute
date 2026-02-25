package dev.transmute.image

import kotlin.concurrent.Volatile
import dev.transmute.model.core.Bytes
import dev.transmute.codec.Codec
import dev.transmute.codec.Decoder
import dev.transmute.codec.Encoder
import dev.transmute.common.PipelineContext
import dev.transmute.image.codecs.bmp.BmpImageDecoder
import dev.transmute.image.codecs.bmp.BmpImageEncoder

/**
 * Mutable registry for [ImageDecoder] instances.
 *
 * Platform codecs are registered at startup via [installPlatformImageCodecs];
 * custom codecs can be added at any time.
 */
class MutableImageDecoderRegistry : ImageDecoderRegistry {
  private val decoders = mutableMapOf<ImageFormat, ImageDecoder>()
  private val decoderList = mutableListOf<ImageDecoder>()

  fun register(decoder: ImageDecoder) {
    decoderList.add(decoder)
    for (format in decoder.supportedFormats) {
      decoders[format] = decoder
    }
  }

  /** Register a core [Decoder] as an [ImageDecoder]. */
  fun register(decoder: Decoder<ImageFormat, ImageIR, ImageDecodeOptions>) {
    val wrapper = object : ImageDecoder {
      override val supportedFormats = decoder.decodableFormats
      override fun sniff(data: Bytes) = decoder.sniff(data)
      override suspend fun decode(source: Bytes, options: ImageDecodeOptions, context: PipelineContext) =
        decoder.decode(source, options, context)
    }
    register(wrapper)
  }

  /** Register a unified [Codec] as a decoder. */
  fun register(codec: Codec<ImageFormat, ImageIR, ImageDecodeOptions, ImageEncodeOptions>) {
    val wrapper = object : ImageDecoder {
      override val supportedFormats = codec.decodableFormats
      override fun sniff(data: Bytes) = codec.sniff(data)
      override suspend fun decode(source: Bytes, options: ImageDecodeOptions, context: PipelineContext) =
        codec.decode(source, options, context)
    }
    decoderList.add(wrapper)
    for (format in codec.decodableFormats) {
      decoders[format] = wrapper
    }
  }

  override fun decoderFor(format: ImageFormat): ImageDecoder? = decoders[format]

  val supportedFormats: Set<ImageFormat> get() = decoders.keys.toSet()

  val allDecoders: List<ImageDecoder> get() = decoderList
}

/**
 * Mutable registry for [ImageEncoder] instances.
 */
class MutableImageEncoderRegistry : ImageEncoderRegistry {
  private val encoders = mutableMapOf<ImageFormat, ImageEncoder>()

  fun register(encoder: ImageEncoder) {
    for (format in encoder.supportedFormats) {
      encoders[format] = encoder
    }
  }

  /** Register a core [Encoder] as an [ImageEncoder]. */
  fun register(encoder: Encoder<ImageFormat, ImageIR, ImageEncodeOptions>) {
    val wrapper = object : ImageEncoder {
      override val supportedFormats = encoder.encodableFormats
      override suspend fun encode(
        ir: ImageIR,
        format: ImageFormat,
        options: ImageEncodeOptions,
        context: PipelineContext,
      ) = encoder.encode(ir, format, options, context)
    }
    register(wrapper)
  }

  fun register(format: ImageFormat, encoder: ImageEncoder) {
    encoders[format] = encoder
  }

  /** Register a unified [Codec] as an encoder. */
  fun register(codec: Codec<ImageFormat, ImageIR, ImageDecodeOptions, ImageEncodeOptions>) {
    for (format in codec.encodableFormats) {
      encoders[format] = object : ImageEncoder {
        override val supportedFormats = codec.encodableFormats
        override suspend fun encode(
          ir: ImageIR,
          format: ImageFormat,
          options: ImageEncodeOptions,
          context: PipelineContext,
        ) = codec.encode(ir, format, options, context)
      }
    }
  }

  override fun encoderFor(format: ImageFormat): ImageEncoder? = encoders[format]

  val supportedFormats: Set<ImageFormat> get() = encoders.keys.toSet()
}

/**
 * Global image codec registry.
 *
 * Holds all registered decoders and encoders, and a list of unified codecs
 * that participate in format sniffing via [ImageFormatDetector].
 *
 * ---
 *
 * **Migration notice:** This global singleton is being phased out in favour of
 * per-context registries via [TransmuteContext].  Prefer:
 *
 * ```kotlin
 * val ctx = TransmuteContext {
 *     imageDecoders(myDecoderRegistry)
 *     imageEncoders(myEncoderRegistry)
 * }
 * Transmute.image { context(ctx) }.transmute(source)
 * ```
 *
 * @see imageDecoders
 * @see imageEncoders
 */
object ImageRegistries {
  @Volatile private var defaultsInstalled: Boolean = false

  val decoders: MutableImageDecoderRegistry = MutableImageDecoderRegistry()
  val encoders: MutableImageEncoderRegistry = MutableImageEncoderRegistry()

  private val supplementaryInstallers =
    mutableListOf<(MutableImageDecoderRegistry, MutableImageEncoderRegistry) -> Unit>()

  /**
   * Register a supplementary codec installer that runs during [installDefaults]
   * after platform-native codecs.  This is the primary mechanism for optional
   * modules (e.g. `transmute-gstreamer`) to fill codec gaps automatically.
   */
  fun addSupplementaryInstaller(
    installer: (MutableImageDecoderRegistry, MutableImageEncoderRegistry) -> Unit,
  ) {
    supplementaryInstallers.add(installer)
  }

  fun register(decoder: ImageDecoder) {
    decoders.register(decoder)
  }

  fun register(encoder: ImageEncoder) {
    encoders.register(encoder)
  }

  fun register(decoder: Decoder<ImageFormat, ImageIR, ImageDecodeOptions>) {
    decoders.register(decoder)
  }

  fun register(encoder: Encoder<ImageFormat, ImageIR, ImageEncodeOptions>) {
    encoders.register(encoder)
  }

  /** Register a unified codec for both decode, encode, and sniffing. */
  fun register(codec: Codec<ImageFormat, ImageIR, ImageDecodeOptions, ImageEncodeOptions>) {
    decoders.register(codec)
    encoders.register(codec)
  }

  /** Installs platform defaults unconditionally. */
  fun installDefaults() {
    installPlatformImageCodecs(decoders, encoders)

    // Cross-platform fallback codecs.
    if (decoders.decoderFor(ImageFormat.Bmp) == null) {
      decoders.register(BmpImageDecoder())
    }
    if (encoders.encoderFor(ImageFormat.Bmp) == null) {
      encoders.register(BmpImageEncoder())
    }

    // Supplementary installers fill gaps left by native codecs.
    for (installer in supplementaryInstallers) {
      installer(decoders, encoders)
    }

    defaultsInstalled = true
  }

  /** Installs platform defaults if the registries look empty. */
  fun installDefaultsIfEmpty() {
    if (defaultsInstalled) return
    synchronized(this) {
      if (defaultsInstalled) return
      installDefaults()
      defaultsInstalled = true
    }
  }
}

expect fun installPlatformImageCodecs(
  decoders: MutableImageDecoderRegistry,
  encoders: MutableImageEncoderRegistry,
)
