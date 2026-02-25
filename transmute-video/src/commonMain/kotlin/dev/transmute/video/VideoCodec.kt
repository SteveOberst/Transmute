package dev.transmute.video

import dev.transmute.codec.Codec
import dev.transmute.model.core.Bytes
import dev.transmute.common.PipelineContext

/**
 * A full video codec that can decode **and** encode, plus sniff format
 * from raw bytes. Prefer implementing this over the split interfaces.
 */
interface VideoCodec : Codec<VideoFormat, VideoIR, VideoDecodeOptions, VideoEncodeOptions>

// Split interfaces - kept for codecs that only decode or only encode.

interface VideoDecoder {
  val supportedFormats: Set<VideoFormat>
  fun sniff(data: Bytes): VideoFormat? = null
  suspend fun decode(source: Bytes, options: VideoDecodeOptions, context: PipelineContext): VideoIR
}

interface VideoDecoderRegistry {
  fun decoderFor(format: VideoFormat): VideoDecoder?
}

interface VideoEncoder {
  val supportedFormats: Set<VideoFormat>
  suspend fun encode(ir: VideoIR, format: VideoFormat, options: VideoEncodeOptions, context: PipelineContext): Bytes
}

interface VideoEncoderRegistry {
  fun encoderFor(format: VideoFormat): VideoEncoder?
}

// Adapters: bridge split decoder/encoder implementations into the unified Codec shape.

class VideoCodecAdapter(
  private val decoder: VideoDecoder,
  private val encoder: VideoEncoder,
  private val sniffer: ((Bytes) -> VideoFormat?)? = null,
) : VideoCodec {
  override val decodableFormats: Set<VideoFormat> get() = decoder.supportedFormats
  override val encodableFormats: Set<VideoFormat> get() = encoder.supportedFormats
  override fun sniff(data: Bytes): VideoFormat? = sniffer?.invoke(data)
  override suspend fun decode(source: Bytes, options: VideoDecodeOptions, context: PipelineContext): VideoIR =
    decoder.decode(source, options, context)
  override suspend fun encode(
    ir: VideoIR,
    format: VideoFormat,
    options: VideoEncodeOptions,
    context: PipelineContext,
  ): Bytes = encoder.encode(ir, format, options, context)
}

class VideoDecoderCodecAdapter(
  private val decoder: VideoDecoder,
  private val sniffer: ((Bytes) -> VideoFormat?)? = null,
) : Codec<VideoFormat, VideoIR, VideoDecodeOptions, VideoEncodeOptions> {
  override val decodableFormats: Set<VideoFormat> get() = decoder.supportedFormats
  override val encodableFormats: Set<VideoFormat> get() = emptySet()
  override fun sniff(data: Bytes): VideoFormat? = sniffer?.invoke(data)
  override suspend fun decode(source: Bytes, options: VideoDecodeOptions, context: PipelineContext): VideoIR =
    decoder.decode(source, options, context)

  override suspend fun encode(
    ir: VideoIR,
    format: VideoFormat,
    options: VideoEncodeOptions,
    context: PipelineContext,
  ): Bytes = error("${this::class.simpleName} is decode-only")
}

class VideoEncoderCodecAdapter(
  private val encoder: VideoEncoder,
) : Codec<VideoFormat, VideoIR, VideoDecodeOptions, VideoEncodeOptions> {
  override val encodableFormats: Set<VideoFormat> get() = encoder.supportedFormats

  override val decodableFormats: Set<VideoFormat> get() = emptySet()
  override fun sniff(data: Bytes): VideoFormat? = null
  override suspend fun decode(source: Bytes, options: VideoDecodeOptions, context: PipelineContext): VideoIR =
    error("${this::class.simpleName} is encode-only")

  override suspend fun encode(
    ir: VideoIR,
    format: VideoFormat,
    options: VideoEncodeOptions,
    context: PipelineContext,
  ): Bytes = encoder.encode(ir, format, options, context)
}
