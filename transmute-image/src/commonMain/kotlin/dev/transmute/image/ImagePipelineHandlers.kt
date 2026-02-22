package dev.transmute.image

import dev.transmute.core.Bytes
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
  private val detector: (Bytes) -> ImageFormat = ImageFormatDetector::detect,
  private val decoders: ImageDecoderRegistry = ImageRegistries.decoders,
) : PipelineHandler<Bytes, Decoded<ImageFormat, ImageIR>> {

  override suspend fun handle(value: Bytes, context: TransmuteContext): Decoded<ImageFormat, ImageIR> {
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
) : PipelineHandler<Decoded<ImageFormat, ImageIR>, EncodedBytes<ImageFormat>> {

  override suspend fun handle(
    value: Decoded<ImageFormat, ImageIR>,
    context: TransmuteContext,
  ): EncodedBytes<ImageFormat> {
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
    return EncodedBytes(format = outFormat, bytes = bytes)
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
class ImageFixedEncodeHandler<OUT : ImageFormat>(
  private val output: OUT,
  private val encoders: ImageEncoderRegistry = ImageRegistries.encoders,
) : PipelineHandler<Decoded<ImageFormat, ImageIR>, EncodedBytes<OUT>> {

  override suspend fun handle(value: Decoded<ImageFormat, ImageIR>, context: TransmuteContext): EncodedBytes<OUT> {
    ImageRegistries.installDefaultsIfEmpty()

    val requested = (context.encodeOptions as? ImageEncodeOptions) ?: CanonicalImageEncodeOptions()
    when (val declared = requested.outputFormat) {
      OutputFormat.ORIGINAL -> Unit
      is OutputFormat.Exact -> require(declared.format == output) {
        "encodeOptions.outputFormat=${declared.format} conflicts with fixed output=$output"
      }
    }

    val effective = requested.resolveFor(output)
    val encoder = encoders.encoderFor(output) ?: error("No image encoder for $output")

    val stripped = when (effective.metadataPolicy) {
      dev.transmute.core.MetadataPolicy.PRESERVE -> value.ir
      dev.transmute.core.MetadataPolicy.STRIP_ALL -> value.ir.copy(metadata = ImageMetadata())
    }
    val bytes = encoder.encode(stripped, output, effective, context)
    return EncodedBytes(format = output, bytes = bytes)
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
fun <IN> PipelineBuilder<IN, Bytes>.then(
  decoder: ImageDecoder,
  detector: (Bytes) -> ImageFormat = ImageFormatDetector::detect,
): PipelineBuilder<IN, Decoded<ImageFormat, ImageIR>> = then { raw, ctx ->
  val options = (ctx.decodeOptions as? ImageDecodeOptions) ?: CanonicalImageDecodeOptions()
  val accepted = options.acceptedInputFormats

  val format = when {
    accepted.size == 1 -> accepted.first()
    else -> decoder.sniff(raw) ?: detector(raw)
  }
  if (accepted.isNotEmpty() && format !in accepted) {
    error("Detected image format $format not in acceptedInputFormats=$accepted")
  }
  require(format in decoder.supportedFormats) {
    "Decoder ${decoder::class.simpleName} does not support format $format (supported=${decoder.supportedFormats})"
  }

  val ir = decoder.decode(raw, options, ctx)
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
): PipelineBuilder<IN, EncodedBytes<ImageFormat>> = then { decoded, ctx ->
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
  EncodedBytes(format = outFormat, bytes = encoder.encode(stripped, outFormat, effective, ctx))
}

/**
 * Adds a fixed-output encode step using a specific [ImageEncoder] and [output] format.
 */
fun <IN, OUT : ImageFormat> PipelineBuilder<IN, Decoded<ImageFormat, ImageIR>>.then(
  encoder: ImageEncoder,
  output: OUT,
): PipelineBuilder<IN, EncodedBytes<OUT>> = then { decoded, ctx ->
  val requested = (ctx.encodeOptions as? ImageEncodeOptions) ?: CanonicalImageEncodeOptions()
  when (val declared = requested.outputFormat) {
    OutputFormat.ORIGINAL -> Unit
    is OutputFormat.Exact -> require(declared.format == output) {
      "encodeOptions.outputFormat=${declared.format} conflicts with fixed output=$output"
    }
  }
  val effective = requested.resolveFor(output)

  require(output in encoder.supportedFormats) {
    "Encoder ${encoder::class.simpleName} does not support format $output (supported=${encoder.supportedFormats})"
  }

  val stripped = when (effective.metadataPolicy) {
    dev.transmute.core.MetadataPolicy.PRESERVE -> decoded.ir
    dev.transmute.core.MetadataPolicy.STRIP_ALL -> decoded.ir.copy(metadata = ImageMetadata())
  }
  EncodedBytes(format = output, bytes = encoder.encode(stripped, output, effective, ctx))
}
