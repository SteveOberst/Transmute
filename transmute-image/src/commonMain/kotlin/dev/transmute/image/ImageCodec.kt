package dev.transmute.image

import dev.transmute.codec.MediaCodec
import dev.transmute.io.TSource
import dev.transmute.model.core.Bytes
import dev.transmute.common.PipelineContext

/**
 * A full image codec that can decode **and** encode.
 * Prefer implementing this over the split interfaces.
 */
interface ImageCodec : MediaCodec<ImageFormat, ImageIR, ImageDecodeOptions, ImageEncodeOptions>

// Split interfaces - kept for codecs that only decode or only encode
// (e.g. platform decoders with no matching encoder).

interface ImageDecoder {
  val supportedFormats: Set<ImageFormat>
  suspend fun decode(source: TSource, options: ImageDecodeOptions, context: PipelineContext): ImageIR
}

interface ImageDecoderRegistry {
  fun decoderFor(format: ImageFormat): ImageDecoder?
}

interface ImageEncoder {
  val supportedFormats: Set<ImageFormat>
  suspend fun encode(ir: ImageIR, format: ImageFormat, options: ImageEncodeOptions, context: PipelineContext): Bytes
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
) : ImageCodec {
  override val decodableFormats: Set<ImageFormat> get() = decoder.supportedFormats
  override val encodableFormats: Set<ImageFormat> get() = encoder.supportedFormats
  override suspend fun decode(source: TSource, options: ImageDecodeOptions, context: PipelineContext): ImageIR =
    decoder.decode(source, options, context)
  override suspend fun encode(
    ir: ImageIR,
    format: ImageFormat,
    options: ImageEncodeOptions,
    context: PipelineContext,
  ): Bytes = encoder.encode(ir, format, options, context)
}

/**
 * Wraps a decode-only [ImageDecoder] as a [MediaCodec].
 */
class ImageDecoderCodecAdapter(
  private val decoder: ImageDecoder,
) : MediaCodec<ImageFormat, ImageIR, ImageDecodeOptions, ImageEncodeOptions> {
  override val decodableFormats: Set<ImageFormat> get() = decoder.supportedFormats
  override val encodableFormats: Set<ImageFormat> get() = emptySet()
  override suspend fun decode(source: TSource, options: ImageDecodeOptions, context: PipelineContext): ImageIR =
    decoder.decode(source, options, context)

  override suspend fun encode(
    ir: ImageIR,
    format: ImageFormat,
    options: ImageEncodeOptions,
    context: PipelineContext,
  ): Bytes = error("${this::class.simpleName} is decode-only")
}

/**
 * Wraps an encode-only [ImageEncoder] as a [MediaCodec].
 */
class ImageEncoderCodecAdapter(
  private val encoder: ImageEncoder,
) : MediaCodec<ImageFormat, ImageIR, ImageDecodeOptions, ImageEncodeOptions> {
  override val encodableFormats: Set<ImageFormat> get() = encoder.supportedFormats
  override val decodableFormats: Set<ImageFormat> get() = emptySet()

  override suspend fun decode(
    source: TSource,
    options: ImageDecodeOptions,
    context: PipelineContext,
  ): ImageIR = error("${this::class.simpleName} is encode-only")

  override suspend fun encode(
    ir: ImageIR,
    format: ImageFormat,
    options: ImageEncodeOptions,
    context: PipelineContext,
  ): Bytes = encoder.encode(ir, format, options, context)
}
