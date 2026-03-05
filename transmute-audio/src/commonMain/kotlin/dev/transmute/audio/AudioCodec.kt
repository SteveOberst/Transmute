package dev.transmute.audio

import dev.transmute.codec.MediaCodec
import dev.transmute.common.PipelineContext
import dev.transmute.io.TSource
import dev.transmute.model.core.Bytes

/**
 * A full audio codec that can decode **and** encode.
 * Prefer implementing this over the split interfaces.
 */
interface AudioCodec : MediaCodec<AudioFormat, AudioIR, AudioDecodeOptions, AudioEncodeOptions>

// Split interfaces - kept for codecs that only decode or only encode.

interface AudioDecoder {
  val supportedFormats: Set<AudioFormat>
  suspend fun decode(source: TSource, options: AudioDecodeOptions, context: PipelineContext): AudioIR
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

class AudioCodecAdapter(private val decoder: AudioDecoder, private val encoder: AudioEncoder) : AudioCodec {
  override val decodableFormats: Set<AudioFormat> get() = decoder.supportedFormats
  override val encodableFormats: Set<AudioFormat> get() = encoder.supportedFormats
  override suspend fun decode(source: TSource, options: AudioDecodeOptions, context: PipelineContext): AudioIR =
    decoder.decode(source, options, context)
  override suspend fun encode(ir: AudioIR, format: AudioFormat, options: AudioEncodeOptions, context: PipelineContext): Bytes =
    encoder.encode(ir, format, options, context)
}

class AudioDecoderCodecAdapter(private val decoder: AudioDecoder) :
  MediaCodec<AudioFormat, AudioIR, AudioDecodeOptions, AudioEncodeOptions> {
  override val decodableFormats: Set<AudioFormat> get() = decoder.supportedFormats
  override val encodableFormats: Set<AudioFormat> get() = emptySet()
  override suspend fun decode(source: TSource, options: AudioDecodeOptions, context: PipelineContext): AudioIR =
    decoder.decode(source, options, context)

  override suspend fun encode(ir: AudioIR, format: AudioFormat, options: AudioEncodeOptions, context: PipelineContext): Bytes =
    error("${this::class.simpleName} is decode-only")
}

class AudioEncoderCodecAdapter(private val encoder: AudioEncoder) :
  MediaCodec<AudioFormat, AudioIR, AudioDecodeOptions, AudioEncodeOptions> {
  override val encodableFormats: Set<AudioFormat> get() = encoder.supportedFormats

  override val decodableFormats: Set<AudioFormat> get() = emptySet()
  override suspend fun decode(source: TSource, options: AudioDecodeOptions, context: PipelineContext): AudioIR =
    error("${this::class.simpleName} is encode-only")

  override suspend fun encode(ir: AudioIR, format: AudioFormat, options: AudioEncodeOptions, context: PipelineContext): Bytes =
    encoder.encode(ir, format, options, context)
}
