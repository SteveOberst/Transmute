package dev.transmute.video

import dev.transmute.core.Bytes
import dev.transmute.core.OutputFormat
import dev.transmute.core.TransmuteContext
import dev.transmute.core.pipeline.Decoded
import dev.transmute.core.pipeline.EncodedBytes
import dev.transmute.core.pipeline.PipelineHandler
import dev.transmute.core.pipeline.PipelineBuilder

/**
 * Video decode handler: detects format (unless constrained), validates accepted formats,
 * then decodes via the registry.
 *
 * Reads [VideoDecodeOptions] from [TransmuteContext.decodeOptions].
 */
class VideoDecodeHandler(
  private val detector: (Bytes) -> VideoFormat = VideoFormatDetector::detect,
  private val decoders: VideoDecoderRegistry = VideoRegistries.decoders,
) : PipelineHandler<Bytes, Decoded<VideoFormat, VideoIR>> {

  override suspend fun handle(value: Bytes, context: TransmuteContext): Decoded<VideoFormat, VideoIR> {
    VideoRegistries.installDefaultsIfEmpty()

    val options = (context.decodeOptions as? VideoDecodeOptions) ?: CanonicalVideoDecodeOptions()
    val accepted = options.acceptedInputFormats

    val format = if (accepted.size == 1) accepted.first() else detector(value)
    if (accepted.isNotEmpty() && format !in accepted) {
      error("Detected video format $format not in acceptedInputFormats=$accepted")
    }

    val decoder = decoders.decoderFor(format) ?: error("No video decoder for $format. Register a platform decoder.")
    val ir = decoder.decode(value, options, context)
    return Decoded(format, ir)
  }
}

/**
 * Dynamic video encode handler.
 *
 * Chooses output format as:
 * - explicit `encodeOptions.outputFormat`, else
 * - the input format from [Decoded.format].
 *
 * Applies [VideoEncodeOptions.metadataPolicy] during encoding (not as a transform step).
 *
 * Reads [VideoEncodeOptions] from [TransmuteContext.encodeOptions].
 */
class VideoDynamicEncodeHandler(
  private val encoders: VideoEncoderRegistry = VideoRegistries.encoders,
) : PipelineHandler<Decoded<VideoFormat, VideoIR>, EncodedBytes<VideoFormat>> {

  override suspend fun handle(
    value: Decoded<VideoFormat, VideoIR>,
    context: TransmuteContext,
  ): EncodedBytes<VideoFormat> {
    VideoRegistries.installDefaultsIfEmpty()

    val requested = (context.encodeOptions as? VideoEncodeOptions) ?: CanonicalVideoEncodeOptions()
    val outFormat = when (val declared = requested.outputFormat) {
      OutputFormat.ORIGINAL -> value.format
      is OutputFormat.Exact -> declared.format
    }

    val encoder = encoders.encoderFor(outFormat) ?: error("No video encoder for $outFormat. Register a platform encoder.")
    val stripped = when (requested.metadataPolicy) {
      dev.transmute.core.MetadataPolicy.PRESERVE -> value.ir
      dev.transmute.core.MetadataPolicy.STRIP_ALL -> value.ir.copy(metadata = VideoMetadata())
    }
    val bytes = encoder.encode(stripped, outFormat, requested, context)
    return EncodedBytes(format = outFormat, bytes = bytes)
  }
}

/**
 * Fixed-output video encode handler.
 *
 * Reads [VideoEncodeOptions] from [TransmuteContext.encodeOptions] and enforces that any
 * explicit `outputFormat` matches the fixed output format.
 */
class VideoFixedEncodeHandler<OUT : VideoFormat>(
  private val output: OUT,
  private val encoders: VideoEncoderRegistry = VideoRegistries.encoders,
) : PipelineHandler<Decoded<VideoFormat, VideoIR>, EncodedBytes<OUT>> {

  override suspend fun handle(value: Decoded<VideoFormat, VideoIR>, context: TransmuteContext): EncodedBytes<OUT> {
    VideoRegistries.installDefaultsIfEmpty()

    val requested = (context.encodeOptions as? VideoEncodeOptions) ?: CanonicalVideoEncodeOptions()
    when (val declared = requested.outputFormat) {
      OutputFormat.ORIGINAL -> Unit
      is OutputFormat.Exact -> require(declared.format == output) {
        "encodeOptions.outputFormat=${declared.format} conflicts with fixed output=$output"
      }
    }

    val encoder = encoders.encoderFor(output)
      ?: error("No video encoder for $output. Register a platform encoder.")
    val stripped = when (requested.metadataPolicy) {
      dev.transmute.core.MetadataPolicy.PRESERVE -> value.ir
      dev.transmute.core.MetadataPolicy.STRIP_ALL -> value.ir.copy(metadata = VideoMetadata())
    }
    val bytes = encoder.encode(stripped, output, requested, context)
    return EncodedBytes(format = output, bytes = bytes)
  }
}

/**
 * Adds a decode step using a specific [VideoDecoder].
 *
 * Format resolution uses:
 * - `acceptedInputFormats.single()` when provided, else
 * - [VideoDecoder.sniff] when available, else
 * - [VideoFormatDetector].
 */
fun <IN> PipelineBuilder<IN, Bytes>.then(
  decoder: VideoDecoder,
  detector: (Bytes) -> VideoFormat = VideoFormatDetector::detect,
): PipelineBuilder<IN, Decoded<VideoFormat, VideoIR>> = then { raw, ctx ->
  val options = (ctx.decodeOptions as? VideoDecodeOptions) ?: CanonicalVideoDecodeOptions()
  val accepted = options.acceptedInputFormats

  val format = when {
    accepted.size == 1 -> accepted.first()
    else -> decoder.sniff(raw) ?: detector(raw)
  }
  if (accepted.isNotEmpty() && format !in accepted) {
    error("Detected video format $format not in acceptedInputFormats=$accepted")
  }
  require(format in decoder.supportedFormats) {
    "Decoder ${decoder::class.simpleName} does not support format $format (supported=${decoder.supportedFormats})"
  }

  val ir = decoder.decode(raw, options, ctx)
  Decoded(format, ir)
}

/**
 * Adds an encode step using a specific [VideoEncoder].
 *
 * Output selection:
 * - explicit `encodeOptions.outputFormat`, else
 * - the input format from [Decoded.format] (ORIGINAL).
 */
fun <IN> PipelineBuilder<IN, Decoded<VideoFormat, VideoIR>>.then(
  encoder: VideoEncoder,
): PipelineBuilder<IN, EncodedBytes<VideoFormat>> = then { decoded, ctx ->
  val requested = (ctx.encodeOptions as? VideoEncodeOptions) ?: CanonicalVideoEncodeOptions()
  val outFormat = when (val declared = requested.outputFormat) {
    OutputFormat.ORIGINAL -> decoded.format
    is OutputFormat.Exact -> declared.format
  }

  require(outFormat in encoder.supportedFormats) {
    "Encoder ${encoder::class.simpleName} does not support format $outFormat (supported=${encoder.supportedFormats})"
  }

  val stripped = when (requested.metadataPolicy) {
    dev.transmute.core.MetadataPolicy.PRESERVE -> decoded.ir
    dev.transmute.core.MetadataPolicy.STRIP_ALL -> decoded.ir.copy(metadata = VideoMetadata())
  }
  EncodedBytes(format = outFormat, bytes = encoder.encode(stripped, outFormat, requested, ctx))
}

/**
 * Adds a fixed-output encode step using a specific [VideoEncoder] and [output] tag.
 */
fun <IN, OUT : VideoFormat> PipelineBuilder<IN, Decoded<VideoFormat, VideoIR>>.then(
  encoder: VideoEncoder,
  output: OUT,
): PipelineBuilder<IN, EncodedBytes<OUT>> = then { decoded, ctx ->
  val requested = (ctx.encodeOptions as? VideoEncodeOptions) ?: CanonicalVideoEncodeOptions()
  when (val declared = requested.outputFormat) {
    OutputFormat.ORIGINAL -> Unit
    is OutputFormat.Exact -> require(declared.format == output) {
      "encodeOptions.outputFormat=${declared.format} conflicts with fixed output=$output"
    }
  }

  require(output in encoder.supportedFormats) {
    "Encoder ${encoder::class.simpleName} does not support format $output (supported=${encoder.supportedFormats})"
  }

  val stripped = when (requested.metadataPolicy) {
    dev.transmute.core.MetadataPolicy.PRESERVE -> decoded.ir
    dev.transmute.core.MetadataPolicy.STRIP_ALL -> decoded.ir.copy(metadata = VideoMetadata())
  }
  EncodedBytes(format = output, bytes = encoder.encode(stripped, output, requested, ctx))
}
