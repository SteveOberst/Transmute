package dev.transmute.video

import dev.transmute.codec.OutputFormat
import dev.transmute.codec.pipeline.Decoded
import dev.transmute.codec.pipeline.EncodedBytes
import dev.transmute.codec.pipeline.PipelineBuilder
import dev.transmute.codec.pipeline.PipelineHandler
import dev.transmute.common.PipelineContext
import dev.transmute.io.TSource
import dev.transmute.model.core.Bytes

/**
 * Video decode handler: detects format (unless constrained), validates accepted formats,
 * then decodes via the registry.
 *
 * Reads [VideoDecodeOptions] from [PipelineContext.decodeOptions].
 */
class VideoDecodeHandler(
  private val detector: (Bytes) -> VideoFormat = VideoFormatDetector::detect,
  private val decoders: VideoDecoderRegistry = VideoRegistries.decoders,
) : PipelineHandler<TSource, Decoded<VideoFormat, VideoIR>> {

  override suspend fun handle(value: TSource, context: PipelineContext): Decoded<VideoFormat, VideoIR> {
    VideoRegistries.installDefaultsIfEmpty()

    val bytes = if (value is Bytes) value else Bytes(value.readAll())
    val options = (context.decodeOptions as? VideoDecodeOptions) ?: CanonicalVideoDecodeOptions()
    val accepted = options.acceptedInputFormats

    val format = if (accepted.size == 1) accepted.first() else detector(bytes)
    if (accepted.isNotEmpty() && format !in accepted) {
      error("Detected video format $format not in acceptedInputFormats=$accepted")
    }

    val decoder = decoders.decoderFor(format) ?: error("No video decoder for $format. Register a platform decoder.")
    val ir = decoder.decode(bytes, options, context)
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
 * Reads [VideoEncodeOptions] from [PipelineContext.encodeOptions].
 */
class VideoDynamicEncodeHandler(private val encoders: VideoEncoderRegistry = VideoRegistries.encoders) :
  PipelineHandler<Decoded<VideoFormat, VideoIR>, EncodedBytes<VideoFormat>> {

  override suspend fun handle(value: Decoded<VideoFormat, VideoIR>, context: PipelineContext): EncodedBytes<VideoFormat> {
    VideoRegistries.installDefaultsIfEmpty()

    val requested = (context.encodeOptions as? VideoEncodeOptions) ?: CanonicalVideoEncodeOptions()
    val outFormat = when (val declared = requested.outputFormat) {
      OutputFormat.ORIGINAL -> value.format
      is OutputFormat.Exact -> declared.format
    }

    val encoder = encoders.encoderFor(outFormat) ?: error("No video encoder for $outFormat. Register a platform encoder.")
    val stripped = when (requested.metadataPolicy) {
      dev.transmute.codec.MetadataPolicy.PRESERVE -> value.ir
      dev.transmute.codec.MetadataPolicy.STRIP_ALL -> value.ir.copy(metadata = VideoMetadata())
    }
    val bytes = encoder.encode(stripped, outFormat, requested, context)
    return EncodedBytes(format = outFormat, bytes = bytes)
  }
}

/**
 * Fixed-output video encode handler.
 *
 * Reads [VideoEncodeOptions] from [PipelineContext.encodeOptions] and enforces that any
 * explicit `outputFormat` matches the fixed output format.
 */
class VideoFixedEncodeHandler<OUT : VideoFormat>(
  private val output: OUT,
  private val encoders: VideoEncoderRegistry = VideoRegistries.encoders,
) : PipelineHandler<Decoded<VideoFormat, VideoIR>, EncodedBytes<OUT>> {

  override suspend fun handle(value: Decoded<VideoFormat, VideoIR>, context: PipelineContext): EncodedBytes<OUT> {
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
      dev.transmute.codec.MetadataPolicy.PRESERVE -> value.ir
      dev.transmute.codec.MetadataPolicy.STRIP_ALL -> value.ir.copy(metadata = VideoMetadata())
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
    else -> detector(raw)
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
fun <IN> PipelineBuilder<IN, Decoded<VideoFormat, VideoIR>>.then(encoder: VideoEncoder): PipelineBuilder<IN, EncodedBytes<VideoFormat>> =
  then { decoded, ctx ->
    val requested = (ctx.encodeOptions as? VideoEncodeOptions) ?: CanonicalVideoEncodeOptions()
    val outFormat = when (val declared = requested.outputFormat) {
      OutputFormat.ORIGINAL -> decoded.format
      is OutputFormat.Exact -> declared.format
    }

    require(outFormat in encoder.supportedFormats) {
      "Encoder ${encoder::class.simpleName} does not support format $outFormat (supported=${encoder.supportedFormats})"
    }

    val stripped = when (requested.metadataPolicy) {
      dev.transmute.codec.MetadataPolicy.PRESERVE -> decoded.ir
      dev.transmute.codec.MetadataPolicy.STRIP_ALL -> decoded.ir.copy(metadata = VideoMetadata())
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
    dev.transmute.codec.MetadataPolicy.PRESERVE -> decoded.ir
    dev.transmute.codec.MetadataPolicy.STRIP_ALL -> decoded.ir.copy(metadata = VideoMetadata())
  }
  EncodedBytes(format = output, bytes = encoder.encode(stripped, output, requested, ctx))
}
