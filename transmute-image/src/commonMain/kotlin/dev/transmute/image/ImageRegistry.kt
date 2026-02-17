package dev.transmute.image

import dev.transmute.core.Codec
import dev.transmute.core.ImageFormat
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

  /** Register a unified [Codec] as a decoder. */
  fun register(codec: Codec<ImageFormat, ImageIR>) {
    val wrapper = object : ImageDecoder {
      override val supportedFormats = codec.decodableFormats
      override fun sniff(data: ByteArray) = codec.sniff(data)
      override suspend fun decode(source: ByteArray, context: dev.transmute.core.ConversionContext) =
        codec.decode(source, context)
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

  fun register(format: ImageFormat, encoder: ImageEncoder) {
    encoders[format] = encoder
  }

  /** Register a unified [Codec] as an encoder. */
  fun register(codec: Codec<ImageFormat, ImageIR>) {
    for (format in codec.encodableFormats) {
      encoders[format] = object : ImageEncoder {
        override val supportedFormats = codec.encodableFormats
        override suspend fun encode(ir: ImageIR, context: dev.transmute.core.ConversionContext) =
          codec.encode(ir, context)
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
 */
object ImageRegistries {
  @Volatile private var defaultsInstalled: Boolean = false

  val decoders: MutableImageDecoderRegistry = MutableImageDecoderRegistry()
  val encoders: MutableImageEncoderRegistry = MutableImageEncoderRegistry()

  /** Register a unified codec for both decode, encode, and sniffing. */
  fun register(codec: Codec<ImageFormat, ImageIR>) {
    decoders.register(codec)
    encoders.register(codec)
  }

  /** Installs platform defaults unconditionally. */
  fun installDefaults() {
    installPlatformImageCodecs(decoders, encoders)

    // Cross-platform fallback codecs.
    if (decoders.decoderFor(ImageFormat.BMP) == null) {
      decoders.register(BmpImageDecoder())
    }
    if (encoders.encoderFor(ImageFormat.BMP) == null) {
      encoders.register(BmpImageEncoder())
    }

    defaultsInstalled = true
  }

  /** Installs platform defaults if the registries look empty. */
  fun installDefaultsIfEmpty() {
    if (defaultsInstalled) return
    installDefaults()
  }
}

expect fun installPlatformImageCodecs(
  decoders: MutableImageDecoderRegistry,
  encoders: MutableImageEncoderRegistry,
)
