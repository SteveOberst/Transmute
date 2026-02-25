package dev.transmute.audio

import dev.transmute.model.core.Bytes
import dev.transmute.codec.Codec
import dev.transmute.common.PipelineContext

/**
 * A full audio codec that can decode **and** encode, plus sniff format
 * from raw bytes. Prefer implementing this over the split interfaces.
 */
interface AudioCodec : Codec<AudioFormat, AudioIR, AudioDecodeOptions, AudioEncodeOptions>

// Split interfaces - kept for codecs that only decode or only encode.

interface AudioDecoder {
  val supportedFormats: Set<AudioFormat>
  fun sniff(data: Bytes): AudioFormat? = null
  suspend fun decode(source: Bytes, options: AudioDecodeOptions, context: PipelineContext): AudioIR
}

interface AudioDecoderRegistry {
  fun decoderFor(format: AudioFormat): AudioDecoder?
}

interface AudioEncoder {
  val supportedFormats: Set<AudioFormat>
  suspend fun encode(ir: AudioIR, format: AudioFormat, options: AudioEncodeOptions, context: PipelineContext): Bytes
}

interface AudioEncoderRegistry {
  fun encoderFor(format: AudioFormat): AudioEncoder?
}

// Adapters: bridge split decoder/encoder implementations into the unified Codec shape.

class AudioCodecAdapter(
  private val decoder: AudioDecoder,
  private val encoder: AudioEncoder,
  private val sniffer: ((Bytes) -> AudioFormat?)? = null,
) : AudioCodec {
  override val decodableFormats: Set<AudioFormat> get() = decoder.supportedFormats
  override val encodableFormats: Set<AudioFormat> get() = encoder.supportedFormats
  override fun sniff(data: Bytes): AudioFormat? = sniffer?.invoke(data)
  override suspend fun decode(source: Bytes, options: AudioDecodeOptions, context: PipelineContext): AudioIR =
    decoder.decode(source, options, context)
  override suspend fun encode(
    ir: AudioIR,
    format: AudioFormat,
    options: AudioEncodeOptions,
    context: PipelineContext,
  ): Bytes = encoder.encode(ir, format, options, context)
}

class AudioDecoderCodecAdapter(
  private val decoder: AudioDecoder,
  private val sniffer: ((Bytes) -> AudioFormat?)? = null,
) : Codec<AudioFormat, AudioIR, AudioDecodeOptions, AudioEncodeOptions> {
  override val decodableFormats: Set<AudioFormat> get() = decoder.supportedFormats
  override val encodableFormats: Set<AudioFormat> get() = emptySet()
  override fun sniff(data: Bytes): AudioFormat? = sniffer?.invoke(data)
  override suspend fun decode(source: Bytes, options: AudioDecodeOptions, context: PipelineContext): AudioIR =
    decoder.decode(source, options, context)

  override suspend fun encode(
    ir: AudioIR,
    format: AudioFormat,
    options: AudioEncodeOptions,
    context: PipelineContext,
  ): Bytes = error("${this::class.simpleName} is decode-only")
}

class AudioEncoderCodecAdapter(
  private val encoder: AudioEncoder,
) : Codec<AudioFormat, AudioIR, AudioDecodeOptions, AudioEncodeOptions> {
  override val encodableFormats: Set<AudioFormat> get() = encoder.supportedFormats

  override val decodableFormats: Set<AudioFormat> get() = emptySet()
  override fun sniff(data: Bytes): AudioFormat? = null
  override suspend fun decode(source: Bytes, options: AudioDecodeOptions, context: PipelineContext): AudioIR =
    error("${this::class.simpleName} is encode-only")

  override suspend fun encode(
    ir: AudioIR,
    format: AudioFormat,
    options: AudioEncodeOptions,
    context: PipelineContext,
  ): Bytes = encoder.encode(ir, format, options, context)
}
