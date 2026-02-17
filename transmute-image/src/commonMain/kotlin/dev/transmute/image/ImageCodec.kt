package dev.transmute.image

import dev.transmute.core.Codec
import dev.transmute.core.ConversionContext
import dev.transmute.core.DecoderCodec
import dev.transmute.core.EncoderCodec
import dev.transmute.core.ImageFormat

/**
 * A full image codec that can decode **and** encode, plus sniff format
 * from raw bytes. Prefer implementing this over the split interfaces.
 */
interface ImageCodec : Codec<ImageFormat, ImageIR>

// Split interfaces — kept for codecs that only decode or only encode
// (e.g. platform decoders with no matching encoder).

interface ImageDecoder {
  val supportedFormats: Set<ImageFormat>
  fun sniff(data: ByteArray): ImageFormat? = null
  suspend fun decode(source: ByteArray, context: ConversionContext): ImageIR
}

interface ImageDecoderRegistry {
  fun decoderFor(format: ImageFormat): ImageDecoder?
}

interface ImageEncoder {
  val supportedFormats: Set<ImageFormat>
  suspend fun encode(ir: ImageIR, context: ConversionContext): ByteArray
}

interface ImageEncoderRegistry {
  fun encoderFor(format: ImageFormat): ImageEncoder?
}

// Adapters: bridge split decoder/encoder implementations into the unified Codec shape.

/**
 * Wraps a pair of [ImageDecoder] + [ImageEncoder] into a single [ImageCodec].
 */
class ImageCodecAdapter(
  private val decoder: ImageDecoder,
  private val encoder: ImageEncoder,
  private val sniffer: ((ByteArray) -> ImageFormat?)? = null,
) : ImageCodec {
  override val decodableFormats: Set<ImageFormat> get() = decoder.supportedFormats
  override val encodableFormats: Set<ImageFormat> get() = encoder.supportedFormats
  override fun sniff(data: ByteArray): ImageFormat? = sniffer?.invoke(data)
  override suspend fun decode(source: ByteArray, context: ConversionContext): ImageIR =
    decoder.decode(source, context)
  override suspend fun encode(ir: ImageIR, context: ConversionContext): ByteArray =
    encoder.encode(ir, context)
}

/**
 * Wraps a decode-only [ImageDecoder] as a [DecoderCodec].
 */
class ImageDecoderCodecAdapter(
  private val decoder: ImageDecoder,
  private val sniffer: ((ByteArray) -> ImageFormat?)? = null,
) : DecoderCodec<ImageFormat, ImageIR> {
  override val decodableFormats: Set<ImageFormat> get() = decoder.supportedFormats
  override fun sniff(data: ByteArray): ImageFormat? = sniffer?.invoke(data)
  override suspend fun decode(source: ByteArray, context: ConversionContext): ImageIR =
    decoder.decode(source, context)
}

/**
 * Wraps an encode-only [ImageEncoder] as an [EncoderCodec].
 */
class ImageEncoderCodecAdapter(
  private val encoder: ImageEncoder,
) : EncoderCodec<ImageFormat, ImageIR> {
  override val encodableFormats: Set<ImageFormat> get() = encoder.supportedFormats
  override suspend fun encode(ir: ImageIR, context: ConversionContext): ByteArray =
    encoder.encode(ir, context)
}
