package dev.transmute.image

import dev.transmute.core.Codec
import dev.transmute.core.Bytes
import dev.transmute.core.TransmuteContext

/**
 * A full image codec that can decode **and** encode, plus sniff format
 * from raw bytes. Prefer implementing this over the split interfaces.
 */
interface ImageCodec : Codec<ImageFormat, ImageIR, ImageDecodeOptions, ImageEncodeOptions>

// Split interfaces - kept for codecs that only decode or only encode
// (e.g. platform decoders with no matching encoder).

interface ImageDecoder {
  val supportedFormats: Set<ImageFormat>
  fun sniff(data: Bytes): ImageFormat? = null
  suspend fun decode(source: Bytes, options: ImageDecodeOptions, context: TransmuteContext): ImageIR
}

interface ImageDecoderRegistry {
  fun decoderFor(format: ImageFormat): ImageDecoder?
}

interface ImageEncoder {
  val supportedFormats: Set<ImageFormat>
  suspend fun encode(ir: ImageIR, format: ImageFormat, options: ImageEncodeOptions, context: TransmuteContext): Bytes
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
  private val sniffer: ((Bytes) -> ImageFormat?)? = null,
) : ImageCodec {
  override val decodableFormats: Set<ImageFormat> get() = decoder.supportedFormats
  override val encodableFormats: Set<ImageFormat> get() = encoder.supportedFormats
  override fun sniff(data: Bytes): ImageFormat? = sniffer?.invoke(data)
  override suspend fun decode(source: Bytes, options: ImageDecodeOptions, context: TransmuteContext): ImageIR =
    decoder.decode(source, options, context)
  override suspend fun encode(
    ir: ImageIR,
    format: ImageFormat,
    options: ImageEncodeOptions,
    context: TransmuteContext,
  ): Bytes = encoder.encode(ir, format, options, context)
}

/**
 * Wraps a decode-only [ImageDecoder] as a [Codec].
 */
class ImageDecoderCodecAdapter(
  private val decoder: ImageDecoder,
  private val sniffer: ((Bytes) -> ImageFormat?)? = null,
) : Codec<ImageFormat, ImageIR, ImageDecodeOptions, ImageEncodeOptions> {
  override val decodableFormats: Set<ImageFormat> get() = decoder.supportedFormats
  override val encodableFormats: Set<ImageFormat> get() = emptySet()
  override fun sniff(data: Bytes): ImageFormat? = sniffer?.invoke(data)
  override suspend fun decode(source: Bytes, options: ImageDecodeOptions, context: TransmuteContext): ImageIR =
    decoder.decode(source, options, context)

  override suspend fun encode(
    ir: ImageIR,
    format: ImageFormat,
    options: ImageEncodeOptions,
    context: TransmuteContext,
  ): Bytes = error("${this::class.simpleName} is decode-only")
}

/**
 * Wraps an encode-only [ImageEncoder] as a [Codec].
 */
class ImageEncoderCodecAdapter(
  private val encoder: ImageEncoder,
) : Codec<ImageFormat, ImageIR, ImageDecodeOptions, ImageEncodeOptions> {
  override val encodableFormats: Set<ImageFormat> get() = encoder.supportedFormats
  override val decodableFormats: Set<ImageFormat> get() = emptySet()

  override fun sniff(data: Bytes): ImageFormat? = null

  override suspend fun decode(
    source: Bytes,
    options: ImageDecodeOptions,
    context: TransmuteContext,
  ): ImageIR = error("${this::class.simpleName} is encode-only")

  override suspend fun encode(
    ir: ImageIR,
    format: ImageFormat,
    options: ImageEncodeOptions,
    context: TransmuteContext,
  ): Bytes = encoder.encode(ir, format, options, context)
}
