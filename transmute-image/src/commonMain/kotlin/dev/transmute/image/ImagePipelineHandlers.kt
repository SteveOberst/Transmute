package dev.transmute.image

import dev.transmute.core.AnyFormatTag
import dev.transmute.core.ImageFormat
import dev.transmute.core.OutputFormat
import dev.transmute.core.resolve
import dev.transmute.core.TransmuteContext
import dev.transmute.core.pipeline.Decoded
import dev.transmute.core.pipeline.EncodedBytes
import dev.transmute.core.pipeline.PipelineHandler
import dev.transmute.core.pipeline.PipelineBuilder

/**
 * Image decode handler: detects format (unless constrained), validates accepted formats,
 * then decodes via the registry.
 *
 * Reads [ImageDecodeOptions] from [TransmuteContext.decodeOptions].
 */
class ImageDecodeHandler(
  private val detector: (ByteArray) -> ImageFormat = ImageFormatDetector::detect,
  private val decoders: ImageDecoderRegistry = ImageRegistries.decoders,
) : PipelineHandler<ByteArray, Decoded<ImageFormat, ImageIR>> {

  override suspend fun handle(value: ByteArray, context: TransmuteContext): Decoded<ImageFormat, ImageIR> {
    ImageRegistries.installDefaultsIfEmpty()

    val options = (context.decodeOptions as? ImageDecodeOptions) ?: CanonicalImageDecodeOptions()
    val accepted = options.acceptedInputFormats

    val format = if (accepted.size == 1) accepted.first() else detector(value)
    if (accepted.isNotEmpty() && format !in accepted) {
      error("Detected image format $format not in acceptedInputFormats=$accepted")
    }

    val decoder = decoders.decoderFor(format) ?: error("No image decoder for $format")
    val ir = decoder.decode(value, options, context)
    return Decoded(format, ir)
  }
}

/**
 * Dynamic image encode handler.
 *
 * Chooses output format as:
 * - `encodeOptions.outputFormat` when explicit, else
 * - the input format from [Decoded.format].
 *
 * Applies [ImageEncodeOptions.metadataPolicy] during encoding (not as a transform step).
 *
 * Reads [ImageEncodeOptions] from [TransmuteContext.encodeOptions].
 */
class ImageDynamicEncodeHandler(
  private val encoders: ImageEncoderRegistry = ImageRegistries.encoders,
  private val outputFormatSelector: ImageOutputFormatSelector = ImageOutputFormatSelector { decoded, options ->
    options.outputFormat.resolve(decoded.format)
  },
) : PipelineHandler<Decoded<ImageFormat, ImageIR>, EncodedBytes<ImageFormat, AnyFormatTag<ImageFormat>>> {

  override suspend fun handle(
    value: Decoded<ImageFormat, ImageIR>,
    context: TransmuteContext,
  ): EncodedBytes<ImageFormat, AnyFormatTag<ImageFormat>> {
    ImageRegistries.installDefaultsIfEmpty()

    val requested = (context.encodeOptions as? ImageEncodeOptions) ?: CanonicalImageEncodeOptions()
    val outFormat = outputFormatSelector.select(value, requested)
    val effective = requested.resolveFor(outFormat)

    val encoder = encoders.encoderFor(outFormat) ?: error("No image encoder for $outFormat")
    val stripped = when (effective.metadataPolicy) {
      dev.transmute.core.MetadataPolicy.PRESERVE -> value.ir
      dev.transmute.core.MetadataPolicy.STRIP_ALL -> value.ir.copy(metadata = ImageMetadata())
    }
    val bytes = encoder.encode(stripped, outFormat, effective, context)
    return EncodedBytes(formatTag = AnyFormatTag(outFormat), bytes = bytes)
  }
}

fun interface ImageOutputFormatSelector {
  fun select(decoded: Decoded<ImageFormat, ImageIR>, options: ImageEncodeOptions): ImageFormat
}

/**
 * Fixed-output image encode handler.
 *
 * Reads [ImageEncodeOptions] from [TransmuteContext.encodeOptions] and enforces that any
 * explicit `outputFormat` matches the fixed output format.
 */
class ImageFixedEncodeHandler<OUT : dev.transmute.core.ImageFormatTag>(
  private val output: OUT,
  private val encoders: ImageEncoderRegistry = ImageRegistries.encoders,
) : PipelineHandler<Decoded<ImageFormat, ImageIR>, EncodedBytes<ImageFormat, OUT>> {

  override suspend fun handle(value: Decoded<ImageFormat, ImageIR>, context: TransmuteContext): EncodedBytes<ImageFormat, OUT> {
    ImageRegistries.installDefaultsIfEmpty()

    val requested = (context.encodeOptions as? ImageEncodeOptions) ?: CanonicalImageEncodeOptions()
    when (val declared = requested.outputFormat) {
      OutputFormat.ORIGINAL -> Unit
      is OutputFormat.Exact -> require(declared.format == output.format) {
        "encodeOptions.outputFormat=${declared.format} conflicts with fixed output=${output.format}"
      }
    }

    val effective = requested.resolveFor(output.format)
    val encoder = encoders.encoderFor(output.format) ?: error("No image encoder for ${output.format}")

    val stripped = when (effective.metadataPolicy) {
      dev.transmute.core.MetadataPolicy.PRESERVE -> value.ir
      dev.transmute.core.MetadataPolicy.STRIP_ALL -> value.ir.copy(metadata = ImageMetadata())
    }
    val bytes = encoder.encode(stripped, output.format, effective, context)
    return EncodedBytes(formatTag = output, bytes = bytes)
  }
}

/**
 * Adds a decode step using a specific [ImageDecoder].
 *
 * The decoder is expected to support the resolved input format.
 * Format resolution uses:
 * - `acceptedInputFormats.single()` when provided, else
 * - [ImageDecoder.sniff] when available, else
 * - [ImageFormatDetector].
 */
fun <IN> PipelineBuilder<IN, ByteArray>.then(
  decoder: ImageDecoder,
  detector: (ByteArray) -> ImageFormat = ImageFormatDetector::detect,
): PipelineBuilder<IN, Decoded<ImageFormat, ImageIR>> = then { bytes, ctx ->
  val options = (ctx.decodeOptions as? ImageDecodeOptions) ?: CanonicalImageDecodeOptions()
  val accepted = options.acceptedInputFormats

  val format = when {
    accepted.size == 1 -> accepted.first()
    else -> decoder.sniff(bytes) ?: detector(bytes)
  }
  if (accepted.isNotEmpty() && format !in accepted) {
    error("Detected image format $format not in acceptedInputFormats=$accepted")
  }
  require(format in decoder.supportedFormats) {
    "Decoder ${decoder::class.simpleName} does not support format $format (supported=${decoder.supportedFormats})"
  }

  val ir = decoder.decode(bytes, options, ctx)
  Decoded(format, ir)
}

/**
 * Adds an encode step using a specific [ImageEncoder].
 *
 * Output selection:
 * - explicit `encodeOptions.outputFormat`, else
 * - the input format from [Decoded.format] (ORIGINAL).
 */
fun <IN> PipelineBuilder<IN, Decoded<ImageFormat, ImageIR>>.then(
  encoder: ImageEncoder,
): PipelineBuilder<IN, EncodedBytes<ImageFormat, AnyFormatTag<ImageFormat>>> = then { decoded, ctx ->
  val requested = (ctx.encodeOptions as? ImageEncodeOptions) ?: CanonicalImageEncodeOptions()
  val outFormat = requested.outputFormat.resolve(decoded.format)
  val effective = requested.resolveFor(outFormat)

  require(outFormat in encoder.supportedFormats) {
    "Encoder ${encoder::class.simpleName} does not support format $outFormat (supported=${encoder.supportedFormats})"
  }

  val stripped = when (effective.metadataPolicy) {
    dev.transmute.core.MetadataPolicy.PRESERVE -> decoded.ir
    dev.transmute.core.MetadataPolicy.STRIP_ALL -> decoded.ir.copy(metadata = ImageMetadata())
  }
  EncodedBytes(
    formatTag = AnyFormatTag(outFormat),
    bytes = encoder.encode(stripped, outFormat, effective, ctx),
  )
}

/**
 * Adds a fixed-output encode step using a specific [ImageEncoder] and [output] tag.
 */
fun <IN, OUT : dev.transmute.core.ImageFormatTag> PipelineBuilder<IN, Decoded<ImageFormat, ImageIR>>.then(
  encoder: ImageEncoder,
  output: OUT,
): PipelineBuilder<IN, EncodedBytes<ImageFormat, OUT>> = then { decoded, ctx ->
  val requested = (ctx.encodeOptions as? ImageEncodeOptions) ?: CanonicalImageEncodeOptions()
  when (val declared = requested.outputFormat) {
    OutputFormat.ORIGINAL -> Unit
    is OutputFormat.Exact -> require(declared.format == output.format) {
      "encodeOptions.outputFormat=${declared.format} conflicts with fixed output=${output.format}"
    }
  }
  val effective = requested.resolveFor(output.format)

  require(output.format in encoder.supportedFormats) {
    "Encoder ${encoder::class.simpleName} does not support format ${output.format} (supported=${encoder.supportedFormats})"
  }

  val stripped = when (effective.metadataPolicy) {
    dev.transmute.core.MetadataPolicy.PRESERVE -> decoded.ir
    dev.transmute.core.MetadataPolicy.STRIP_ALL -> decoded.ir.copy(metadata = ImageMetadata())
  }
  EncodedBytes(
    formatTag = output,
    bytes = encoder.encode(stripped, output.format, effective, ctx),
  )
}
