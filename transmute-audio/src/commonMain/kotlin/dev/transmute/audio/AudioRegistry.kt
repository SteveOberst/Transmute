package dev.transmute.audio

import kotlin.concurrent.Volatile
import dev.transmute.audio.codecs.WavDecoder
import dev.transmute.audio.codecs.WavEncoder
import dev.transmute.core.AudioFormat
import dev.transmute.core.Codec
import dev.transmute.core.Decoder
import dev.transmute.core.Encoder
import dev.transmute.core.TransmuteContext

/**
 * Mutable registry for [AudioDecoder] instances.
 */
class MutableAudioDecoderRegistry : AudioDecoderRegistry {
  private val decoders = mutableMapOf<AudioFormat, AudioDecoder>()
  private val decoderList = mutableListOf<AudioDecoder>()

  fun register(decoder: AudioDecoder) {
    decoderList.add(decoder)
    for (format in decoder.supportedFormats) {
      decoders[format] = decoder
    }
  }

  /** Register a core [Decoder] as an [AudioDecoder]. */
  fun register(decoder: Decoder<AudioFormat, AudioIR, AudioDecodeOptions>) {
    val wrapper = object : AudioDecoder {
      override val supportedFormats = decoder.decodableFormats
      override fun sniff(data: ByteArray) = decoder.sniff(data)
      override suspend fun decode(source: ByteArray, options: AudioDecodeOptions, context: TransmuteContext) =
        decoder.decode(source, options, context)
    }
    register(wrapper)
  }

  /** Register a unified [Codec] as a decoder. */
  fun register(codec: Codec<AudioFormat, AudioIR, AudioDecodeOptions, AudioEncodeOptions>) {
    val wrapper = object : AudioDecoder {
      override val supportedFormats = codec.decodableFormats
      override fun sniff(data: ByteArray) = codec.sniff(data)
      override suspend fun decode(source: ByteArray, options: AudioDecodeOptions, context: TransmuteContext) =
        codec.decode(source, options, context)
    }
    decoderList.add(wrapper)
    for (format in codec.decodableFormats) {
      decoders[format] = wrapper
    }
  }

  override fun decoderFor(format: AudioFormat): AudioDecoder? = decoders[format]

  val supportedFormats: Set<AudioFormat> get() = decoders.keys.toSet()

  val allDecoders: List<AudioDecoder> get() = decoderList
}

/**
 * Mutable registry for [AudioEncoder] instances.
 */
class MutableAudioEncoderRegistry : AudioEncoderRegistry {
  private val encoders = mutableMapOf<AudioFormat, AudioEncoder>()

  fun register(encoder: AudioEncoder) {
    for (format in encoder.supportedFormats) {
      encoders[format] = encoder
    }
  }

  /** Register a core [Encoder] as an [AudioEncoder]. */
  fun register(encoder: Encoder<AudioFormat, AudioIR, AudioEncodeOptions>) {
    val wrapper = object : AudioEncoder {
      override val supportedFormats = encoder.encodableFormats
      override suspend fun encode(
        ir: AudioIR,
        format: AudioFormat,
        options: AudioEncodeOptions,
        context: TransmuteContext,
      ) = encoder.encode(ir, format, options, context)
    }
    register(wrapper)
  }

  /** Register a unified [Codec] as an encoder. */
  fun register(codec: Codec<AudioFormat, AudioIR, AudioDecodeOptions, AudioEncodeOptions>) {
    for (format in codec.encodableFormats) {
      encoders[format] = object : AudioEncoder {
        override val supportedFormats = codec.encodableFormats
        override suspend fun encode(
          ir: AudioIR,
          format: AudioFormat,
          options: AudioEncodeOptions,
          context: TransmuteContext,
        ) = codec.encode(ir, format, options, context)
      }
    }
  }

  override fun encoderFor(format: AudioFormat): AudioEncoder? = encoders[format]

  val supportedFormats: Set<AudioFormat> get() = encoders.keys.toSet()
}

/**
 * Global audio registries.
 */
object AudioRegistries {
  @Volatile private var defaultsInstalled: Boolean = false

  val decoders = MutableAudioDecoderRegistry()
  val encoders = MutableAudioEncoderRegistry()

  fun register(decoder: AudioDecoder) {
    decoders.register(decoder)
  }

  fun register(encoder: AudioEncoder) {
    encoders.register(encoder)
  }

  fun register(decoder: Decoder<AudioFormat, AudioIR, AudioDecodeOptions>) {
    decoders.register(decoder)
  }

  fun register(encoder: Encoder<AudioFormat, AudioIR, AudioEncodeOptions>) {
    encoders.register(encoder)
  }

  /** Register a unified codec for both decode, encode, and sniffing. */
  fun register(codec: Codec<AudioFormat, AudioIR, AudioDecodeOptions, AudioEncodeOptions>) {
    decoders.register(codec)
    encoders.register(codec)
  }

  /** Installs common + platform defaults unconditionally. */
  fun installDefaults() {
    // Pure-Kotlin WAV codec works on every target.
    decoders.register(WavDecoder())
    encoders.register(WavEncoder())

    // Platform codecs add hardware-accelerated decoders (MP3, AAC, etc.).
    installPlatformAudioCodecs(decoders, encoders)

    defaultsInstalled = true
  }

  /** Installs defaults if the registries look empty. */
  fun installDefaultsIfEmpty() {
    if (defaultsInstalled) return
    synchronized(this) {
      if (defaultsInstalled) return
      installDefaults()
      defaultsInstalled = true
    }
  }
}

expect fun installPlatformAudioCodecs(
  decoders: MutableAudioDecoderRegistry,
  encoders: MutableAudioEncoderRegistry,
)
