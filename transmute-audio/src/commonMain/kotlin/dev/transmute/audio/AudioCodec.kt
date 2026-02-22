package dev.transmute.audio

import dev.transmute.core.AudioFormat
import dev.transmute.core.Codec
import dev.transmute.core.TransmuteContext

/**
 * A full audio codec that can decode **and** encode, plus sniff format
 * from raw bytes. Prefer implementing this over the split interfaces.
 */
interface AudioCodec : Codec<AudioFormat, AudioIR, AudioDecodeOptions, AudioEncodeOptions>

// Split interfaces - kept for codecs that only decode or only encode.

interface AudioDecoder {
  val supportedFormats: Set<AudioFormat>
  fun sniff(data: ByteArray): AudioFormat? = null
  suspend fun decode(source: ByteArray, options: AudioDecodeOptions, context: TransmuteContext): AudioIR
}

interface AudioDecoderRegistry {
  fun decoderFor(format: AudioFormat): AudioDecoder?
}

interface AudioEncoder {
  val supportedFormats: Set<AudioFormat>
  suspend fun encode(ir: AudioIR, format: AudioFormat, options: AudioEncodeOptions, context: TransmuteContext): ByteArray
}

interface AudioEncoderRegistry {
  fun encoderFor(format: AudioFormat): AudioEncoder?
}

// Adapters: bridge split decoder/encoder implementations into the unified Codec shape.

class AudioCodecAdapter(
  private val decoder: AudioDecoder,
  private val encoder: AudioEncoder,
  private val sniffer: ((ByteArray) -> AudioFormat?)? = null,
) : AudioCodec {
  override val decodableFormats: Set<AudioFormat> get() = decoder.supportedFormats
  override val encodableFormats: Set<AudioFormat> get() = encoder.supportedFormats
  override fun sniff(data: ByteArray): AudioFormat? = sniffer?.invoke(data)
  override suspend fun decode(source: ByteArray, options: AudioDecodeOptions, context: TransmuteContext): AudioIR =
    decoder.decode(source, options, context)
  override suspend fun encode(
    ir: AudioIR,
    format: AudioFormat,
    options: AudioEncodeOptions,
    context: TransmuteContext,
  ): ByteArray = encoder.encode(ir, format, options, context)
}

class AudioDecoderCodecAdapter(
  private val decoder: AudioDecoder,
  private val sniffer: ((ByteArray) -> AudioFormat?)? = null,
) : Codec<AudioFormat, AudioIR, AudioDecodeOptions, AudioEncodeOptions> {
  override val decodableFormats: Set<AudioFormat> get() = decoder.supportedFormats
  override val encodableFormats: Set<AudioFormat> get() = emptySet()
  override fun sniff(data: ByteArray): AudioFormat? = sniffer?.invoke(data)
  override suspend fun decode(source: ByteArray, options: AudioDecodeOptions, context: TransmuteContext): AudioIR =
    decoder.decode(source, options, context)

  override suspend fun encode(
    ir: AudioIR,
    format: AudioFormat,
    options: AudioEncodeOptions,
    context: TransmuteContext,
  ): ByteArray = error("${this::class.simpleName} is decode-only")
}

class AudioEncoderCodecAdapter(
  private val encoder: AudioEncoder,
) : Codec<AudioFormat, AudioIR, AudioDecodeOptions, AudioEncodeOptions> {
  override val encodableFormats: Set<AudioFormat> get() = encoder.supportedFormats

  override val decodableFormats: Set<AudioFormat> get() = emptySet()
  override fun sniff(data: ByteArray): AudioFormat? = null
  override suspend fun decode(source: ByteArray, options: AudioDecodeOptions, context: TransmuteContext): AudioIR =
    error("${this::class.simpleName} is encode-only")

  override suspend fun encode(
    ir: AudioIR,
    format: AudioFormat,
    options: AudioEncodeOptions,
    context: TransmuteContext,
  ): ByteArray = encoder.encode(ir, format, options, context)
}
