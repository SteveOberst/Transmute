package dev.transmute.video

import dev.transmute.core.Codec
import dev.transmute.core.ConversionContext
import dev.transmute.core.DecoderCodec
import dev.transmute.core.EncoderCodec
import dev.transmute.core.VideoFormat

/**
 * A full video codec that can decode **and** encode, plus sniff format
 * from raw bytes. Prefer implementing this over the split interfaces.
 */
interface VideoCodec : Codec<VideoFormat, VideoIR>

// Split interfaces - kept for codecs that only decode or only encode.

interface VideoDecoder {
  val supportedFormats: Set<VideoFormat>
  fun sniff(data: ByteArray): VideoFormat? = null
  suspend fun decode(source: ByteArray, context: ConversionContext): VideoIR
}

interface VideoDecoderRegistry {
  fun decoderFor(format: VideoFormat): VideoDecoder?
}

interface VideoEncoder {
  val supportedFormats: Set<VideoFormat>
  suspend fun encode(ir: VideoIR, context: ConversionContext): ByteArray
}

interface VideoEncoderRegistry {
  fun encoderFor(format: VideoFormat): VideoEncoder?
}

// Adapters: bridge split decoder/encoder implementations into the unified Codec shape.

class VideoCodecAdapter(
  private val decoder: VideoDecoder,
  private val encoder: VideoEncoder,
  private val sniffer: ((ByteArray) -> VideoFormat?)? = null,
) : VideoCodec {
  override val decodableFormats: Set<VideoFormat> get() = decoder.supportedFormats
  override val encodableFormats: Set<VideoFormat> get() = encoder.supportedFormats
  override fun sniff(data: ByteArray): VideoFormat? = sniffer?.invoke(data)
  override suspend fun decode(source: ByteArray, context: ConversionContext): VideoIR =
    decoder.decode(source, context)
  override suspend fun encode(ir: VideoIR, context: ConversionContext): ByteArray =
    encoder.encode(ir, context)
}

class VideoDecoderCodecAdapter(
  private val decoder: VideoDecoder,
  private val sniffer: ((ByteArray) -> VideoFormat?)? = null,
) : DecoderCodec<VideoFormat, VideoIR> {
  override val decodableFormats: Set<VideoFormat> get() = decoder.supportedFormats
  override fun sniff(data: ByteArray): VideoFormat? = sniffer?.invoke(data)
  override suspend fun decode(source: ByteArray, context: ConversionContext): VideoIR =
    decoder.decode(source, context)
}

class VideoEncoderCodecAdapter(
  private val encoder: VideoEncoder,
) : EncoderCodec<VideoFormat, VideoIR> {
  override val encodableFormats: Set<VideoFormat> get() = encoder.supportedFormats
  override suspend fun encode(ir: VideoIR, context: ConversionContext): ByteArray =
    encoder.encode(ir, context)
}
