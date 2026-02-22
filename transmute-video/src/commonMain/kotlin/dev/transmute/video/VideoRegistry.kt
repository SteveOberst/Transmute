package dev.transmute.video

import kotlin.concurrent.Volatile
import dev.transmute.core.Bytes
import dev.transmute.core.Codec
import dev.transmute.core.Decoder
import dev.transmute.core.Encoder
import dev.transmute.core.TransmuteContext

/**
 * Mutable registry for [VideoDecoder] instances.
 */
class MutableVideoDecoderRegistry : VideoDecoderRegistry {
  private val decoders = mutableMapOf<VideoFormat, VideoDecoder>()
  private val decoderList = mutableListOf<VideoDecoder>()

  fun register(decoder: VideoDecoder) {
    decoderList.add(decoder)
    for (format in decoder.supportedFormats) {
      decoders[format] = decoder
    }
  }

  /** Register a core [Decoder] as a [VideoDecoder]. */
  fun register(decoder: Decoder<VideoFormat, VideoIR, VideoDecodeOptions>) {
    val wrapper = object : VideoDecoder {
      override val supportedFormats = decoder.decodableFormats
      override fun sniff(data: Bytes) = decoder.sniff(data)
      override suspend fun decode(source: Bytes, options: VideoDecodeOptions, context: TransmuteContext) =
        decoder.decode(source, options, context)
    }
    register(wrapper)
  }

  /** Register a unified [Codec] as a decoder. */
  fun register(codec: Codec<VideoFormat, VideoIR, VideoDecodeOptions, VideoEncodeOptions>) {
    val wrapper = object : VideoDecoder {
      override val supportedFormats = codec.decodableFormats
      override fun sniff(data: Bytes) = codec.sniff(data)
      override suspend fun decode(source: Bytes, options: VideoDecodeOptions, context: TransmuteContext) =
        codec.decode(source, options, context)
    }
    decoderList.add(wrapper)
    for (format in codec.decodableFormats) {
      decoders[format] = wrapper
    }
  }

  override fun decoderFor(format: VideoFormat): VideoDecoder? = decoders[format]

  val allDecoders: List<VideoDecoder> get() = decoderList

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

  /** Register a core [Encoder] as a [VideoEncoder]. */
  fun register(encoder: Encoder<VideoFormat, VideoIR, VideoEncodeOptions>) {
    val wrapper = object : VideoEncoder {
      override val supportedFormats = encoder.encodableFormats
      override suspend fun encode(
        ir: VideoIR,
        format: VideoFormat,
        options: VideoEncodeOptions,
        context: TransmuteContext,
      ) = encoder.encode(ir, format, options, context)
    }
    register(wrapper)
  }

  fun register(format: VideoFormat, encoder: VideoEncoder) {
    encoders[format] = encoder
  }

  /** Register a unified [Codec] as an encoder. */
  fun register(codec: Codec<VideoFormat, VideoIR, VideoDecodeOptions, VideoEncodeOptions>) {
    for (format in codec.encodableFormats) {
      encoders[format] = object : VideoEncoder {
        override val supportedFormats = codec.encodableFormats
        override suspend fun encode(
          ir: VideoIR,
          format: VideoFormat,
          options: VideoEncodeOptions,
          context: TransmuteContext,
        ) = codec.encode(ir, format, options, context)
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
  @Volatile private var defaultsInstalled: Boolean = false

  val decoders = MutableVideoDecoderRegistry()
  val encoders = MutableVideoEncoderRegistry()

  fun register(decoder: VideoDecoder) {
    decoders.register(decoder)
  }

  fun register(encoder: VideoEncoder) {
    encoders.register(encoder)
  }

  fun register(decoder: Decoder<VideoFormat, VideoIR, VideoDecodeOptions>) {
    decoders.register(decoder)
  }

  fun register(encoder: Encoder<VideoFormat, VideoIR, VideoEncodeOptions>) {
    encoders.register(encoder)
  }

  /** Register a unified codec for both decode, encode, and sniffing. */
  fun register(codec: Codec<VideoFormat, VideoIR, VideoDecodeOptions, VideoEncodeOptions>) {
    decoders.register(codec)
    encoders.register(codec)
  }

  /** Installs platform defaults unconditionally. */
  fun installDefaults() {
    installPlatformVideoCodecs(decoders, encoders)
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

expect fun installPlatformVideoCodecs(
  decoders: MutableVideoDecoderRegistry,
  encoders: MutableVideoEncoderRegistry,
)
