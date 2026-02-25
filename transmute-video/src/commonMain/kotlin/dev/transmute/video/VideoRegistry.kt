package dev.transmute.video

import kotlin.concurrent.Volatile
import dev.transmute.model.core.Bytes
import dev.transmute.codec.Codec
import dev.transmute.codec.Decoder
import dev.transmute.codec.Encoder
import dev.transmute.common.PipelineContext

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
      override suspend fun decode(source: Bytes, options: VideoDecodeOptions, context: PipelineContext) =
        decoder.decode(source, options, context)
    }
    register(wrapper)
  }

  /** Register a unified [Codec] as a decoder. */
  fun register(codec: Codec<VideoFormat, VideoIR, VideoDecodeOptions, VideoEncodeOptions>) {
    val wrapper = object : VideoDecoder {
      override val supportedFormats = codec.decodableFormats
      override fun sniff(data: Bytes) = codec.sniff(data)
      override suspend fun decode(source: Bytes, options: VideoDecodeOptions, context: PipelineContext) =
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
        context: PipelineContext,
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
          context: PipelineContext,
        ) = codec.encode(ir, format, options, context)
      }
    }
  }

  override fun encoderFor(format: VideoFormat): VideoEncoder? = encoders[format]

  val supportedFormats: Set<VideoFormat> get() = encoders.keys.toSet()
}

/**
 * Global video registries.
 *
 * ---
 *
 * **Migration notice:** This global singleton is being phased out in favour of
 * per-context registries via [TransmuteContext].  Prefer:
 *
 * ```kotlin
 * val ctx = TransmuteContext {
 *     videoDecoders(myDecoderRegistry)
 *     videoEncoders(myEncoderRegistry)
 * }
 * Transmute.video { context(ctx) }.transmute(source)
 * ```
 *
 * @see videoDecoders
 * @see videoEncoders
 */
object VideoRegistries {
  @Volatile private var defaultsInstalled: Boolean = false

  val decoders = MutableVideoDecoderRegistry()
  val encoders = MutableVideoEncoderRegistry()

  private val supplementaryInstallers =
    mutableListOf<(MutableVideoDecoderRegistry, MutableVideoEncoderRegistry) -> Unit>()

  /**
   * Register a supplementary codec installer that runs during [installDefaults]
   * after platform-native codecs.  This is the primary mechanism for optional
   * modules (e.g. `transmute-gstreamer`) to fill codec gaps automatically.
   */
  fun addSupplementaryInstaller(
    installer: (MutableVideoDecoderRegistry, MutableVideoEncoderRegistry) -> Unit,
  ) {
    supplementaryInstallers.add(installer)
  }

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

expect fun installPlatformVideoCodecs(
  decoders: MutableVideoDecoderRegistry,
  encoders: MutableVideoEncoderRegistry,
)
