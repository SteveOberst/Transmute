package dev.transmute.video

import dev.transmute.core.Codec
import dev.transmute.core.VideoFormat

/**
 * Mutable registry for [VideoDecoder] instances.
 */
class MutableVideoDecoderRegistry : VideoDecoderRegistry {
  private val decoders = mutableMapOf<VideoFormat, VideoDecoder>()

  fun register(decoder: VideoDecoder) {
    for (format in decoder.supportedFormats) {
      decoders[format] = decoder
    }
  }

  /** Register a unified [Codec] as a decoder. */
  fun register(codec: Codec<VideoFormat, VideoIR>) {
    for (format in codec.decodableFormats) {
      decoders[format] = object : VideoDecoder {
        override val supportedFormats = codec.decodableFormats
        override suspend fun decode(source: ByteArray, context: dev.transmute.core.ConversionContext) =
          codec.decode(source, context)
      }
    }
  }

  override fun decoderFor(format: VideoFormat): VideoDecoder? = decoders[format]

  val supportedFormats: Set<VideoFormat> get() = decoders.keys.toSet()
}

/**
 * Mutable registry for [VideoEncoder] instances.
 */
class MutableVideoEncoderRegistry : VideoEncoderRegistry {
  private val encoders = mutableMapOf<VideoFormat, VideoEncoder>()

  fun register(encoder: VideoEncoder) {
    for (format in encoder.supportedFormats) {
      encoders[format] = encoder
    }
  }

  fun register(format: VideoFormat, encoder: VideoEncoder) {
    encoders[format] = encoder
  }

  /** Register a unified [Codec] as an encoder. */
  fun register(codec: Codec<VideoFormat, VideoIR>) {
    for (format in codec.encodableFormats) {
      encoders[format] = object : VideoEncoder {
        override val supportedFormats = codec.encodableFormats
        override suspend fun encode(ir: VideoIR, context: dev.transmute.core.ConversionContext) =
          codec.encode(ir, context)
      }
    }
  }

  override fun encoderFor(format: VideoFormat): VideoEncoder? = encoders[format]

  val supportedFormats: Set<VideoFormat> get() = encoders.keys.toSet()
}

/**
 * Global video registries.
 */
object VideoRegistries {
  val decoders = MutableVideoDecoderRegistry()
  val encoders = MutableVideoEncoderRegistry()

  /** Codecs that participate in format sniffing. */
  val codecs = mutableListOf<Codec<VideoFormat, VideoIR>>()

  /** Register a unified codec for both decode, encode, and sniffing. */
  fun register(codec: Codec<VideoFormat, VideoIR>) {
    codecs.add(codec)
    decoders.register(codec)
    encoders.register(codec)
  }

  /** Installs platform defaults unconditionally. */
  fun installDefaults() {
    installPlatformVideoCodecs(decoders, encoders)
  }

  /** Installs platform defaults if the registries look empty. */
  fun installDefaultsIfEmpty() {
    if (decoders.supportedFormats.isEmpty() || encoders.supportedFormats.isEmpty()) {
      installDefaults()
    }
  }
}

expect fun installPlatformVideoCodecs(
  decoders: MutableVideoDecoderRegistry,
  encoders: MutableVideoEncoderRegistry,
)
