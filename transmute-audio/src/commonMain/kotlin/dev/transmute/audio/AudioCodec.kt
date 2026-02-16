package dev.transmute.audio

import dev.transmute.core.AudioFormat
import dev.transmute.core.Codec
import dev.transmute.core.ConversionContext
import dev.transmute.core.DecoderCodec
import dev.transmute.core.EncoderCodec

/**
 * A full audio codec that can decode **and** encode, plus sniff format
 * from raw bytes. Prefer implementing this over the split interfaces.
 */
interface AudioCodec : Codec<AudioFormat, AudioIR>

// Split interfaces — kept for codecs that only decode or only encode.

interface AudioDecoder {
  val supportedFormats: Set<AudioFormat>
  suspend fun decode(source: ByteArray, context: ConversionContext): AudioIR
}

interface AudioDecoderRegistry {
  fun decoderFor(format: AudioFormat): AudioDecoder?
}

interface AudioEncoder {
  val supportedFormats: Set<AudioFormat>
  suspend fun encode(ir: AudioIR, context: ConversionContext): ByteArray
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
  override suspend fun decode(source: ByteArray, context: ConversionContext): AudioIR =
    decoder.decode(source, context)
  override suspend fun encode(ir: AudioIR, context: ConversionContext): ByteArray =
    encoder.encode(ir, context)
}

class AudioDecoderCodecAdapter(
  private val decoder: AudioDecoder,
  private val sniffer: ((ByteArray) -> AudioFormat?)? = null,
) : DecoderCodec<AudioFormat, AudioIR> {
  override val decodableFormats: Set<AudioFormat> get() = decoder.supportedFormats
  override fun sniff(data: ByteArray): AudioFormat? = sniffer?.invoke(data)
  override suspend fun decode(source: ByteArray, context: ConversionContext): AudioIR =
    decoder.decode(source, context)
}

class AudioEncoderCodecAdapter(
  private val encoder: AudioEncoder,
) : EncoderCodec<AudioFormat, AudioIR> {
  override val encodableFormats: Set<AudioFormat> get() = encoder.supportedFormats
  override suspend fun encode(ir: AudioIR, context: ConversionContext): ByteArray =
    encoder.encode(ir, context)
}
