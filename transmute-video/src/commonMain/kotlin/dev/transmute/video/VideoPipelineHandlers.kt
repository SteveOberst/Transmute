package dev.transmute.video

import dev.transmute.core.AnyFormatTag
import dev.transmute.core.TransmuteContext
import dev.transmute.core.VideoFormat
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
  private val detector: (ByteArray) -> VideoFormat = VideoFormatDetector::detect,
  private val decoders: VideoDecoderRegistry = VideoRegistries.decoders,
) : PipelineHandler<ByteArray, Decoded<VideoFormat, VideoIR>> {

  override suspend fun handle(value: ByteArray, context: TransmuteContext): Decoded<VideoFormat, VideoIR> {
    VideoRegistries.installDefaultsIfEmpty()

    val options = (context.decodeOptions as? VideoDecodeOptions) ?: DefaultVideoDecodeOptions()
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
 * - `encodeOptions.outputFormat` if non-null, else
 * - the input format from [Decoded.format].
 *
 * Applies [VideoEncodeOptions.metadataPolicy] during encoding (not as a transform step).
 *
 * Reads [VideoEncodeOptions] from [TransmuteContext.encodeOptions].
 */
class VideoDynamicEncodeHandler(
  private val encoders: VideoEncoderRegistry = VideoRegistries.encoders,
) : PipelineHandler<Decoded<VideoFormat, VideoIR>, EncodedBytes<VideoFormat, AnyFormatTag<VideoFormat>>> {

  override suspend fun handle(
    value: Decoded<VideoFormat, VideoIR>,
    context: TransmuteContext,
  ): EncodedBytes<VideoFormat, AnyFormatTag<VideoFormat>> {
    VideoRegistries.installDefaultsIfEmpty()

    val requested = (context.encodeOptions as? VideoEncodeOptions) ?: DefaultVideoEncodeOptions()
    val outFormat = requested.outputFormat ?: value.format

    val encoder = encoders.encoderFor(outFormat) ?: error("No video encoder for $outFormat. Register a platform encoder.")
    val stripped = when (requested.metadataPolicy) {
      dev.transmute.core.MetadataPolicy.PRESERVE -> value.ir
      dev.transmute.core.MetadataPolicy.STRIP_ALL -> value.ir.copy(metadata = VideoMetadata())
    }
    val bytes = encoder.encode(stripped, outFormat, requested, context)
    return EncodedBytes(formatTag = AnyFormatTag(outFormat), bytes = bytes)
  }
}

/**
 * Fixed-output video encode handler.
 *
 * Reads [VideoEncodeOptions] from [TransmuteContext.encodeOptions] and enforces that any
 * non-null `outputFormat` matches the fixed output format.
 */
class VideoFixedEncodeHandler<OUT : dev.transmute.core.VideoFormatTag>(
  private val output: OUT,
  private val encoders: VideoEncoderRegistry = VideoRegistries.encoders,
) : PipelineHandler<Decoded<VideoFormat, VideoIR>, EncodedBytes<VideoFormat, OUT>> {

  override suspend fun handle(value: Decoded<VideoFormat, VideoIR>, context: TransmuteContext): EncodedBytes<VideoFormat, OUT> {
    VideoRegistries.installDefaultsIfEmpty()

    val requested = (context.encodeOptions as? VideoEncodeOptions) ?: DefaultVideoEncodeOptions()
    val declared = requested.outputFormat
    require(declared == null || declared == output.format) {
      "encodeOptions.outputFormat=$declared conflicts with fixed output=${output.format}"
    }

    val encoder = encoders.encoderFor(output.format)
      ?: error("No video encoder for ${output.format}. Register a platform encoder.")
    val stripped = when (requested.metadataPolicy) {
      dev.transmute.core.MetadataPolicy.PRESERVE -> value.ir
      dev.transmute.core.MetadataPolicy.STRIP_ALL -> value.ir.copy(metadata = VideoMetadata())
    }
    val bytes = encoder.encode(stripped, output.format, requested, context)
    return EncodedBytes(formatTag = output, bytes = bytes)
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
fun <IN> PipelineBuilder<IN, ByteArray>.then(
  decoder: VideoDecoder,
  detector: (ByteArray) -> VideoFormat = VideoFormatDetector::detect,
): PipelineBuilder<IN, Decoded<VideoFormat, VideoIR>> = then { bytes, ctx ->
  val options = (ctx.decodeOptions as? VideoDecodeOptions) ?: DefaultVideoDecodeOptions()
  val accepted = options.acceptedInputFormats

  val format = when {
    accepted.size == 1 -> accepted.first()
    else -> decoder.sniff(bytes) ?: detector(bytes)
  }
  if (accepted.isNotEmpty() && format !in accepted) {
    error("Detected video format $format not in acceptedInputFormats=$accepted")
  }
  require(format in decoder.supportedFormats) {
    "Decoder ${decoder::class.simpleName} does not support format $format (supported=${decoder.supportedFormats})"
  }

  val ir = decoder.decode(bytes, options, ctx)
  Decoded(format, ir)
}

/**
 * Adds an encode step using a specific [VideoEncoder].
 *
 * Output selection:
 * - `encodeOptions.outputFormat` when non-null, else
 * - the input format from [Decoded.format].
 */
fun <IN> PipelineBuilder<IN, Decoded<VideoFormat, VideoIR>>.then(
  encoder: VideoEncoder,
): PipelineBuilder<IN, EncodedBytes<VideoFormat, AnyFormatTag<VideoFormat>>> = then { decoded, ctx ->
  val requested = (ctx.encodeOptions as? VideoEncodeOptions) ?: DefaultVideoEncodeOptions()
  val outFormat = requested.outputFormat ?: decoded.format

  require(outFormat in encoder.supportedFormats) {
    "Encoder ${encoder::class.simpleName} does not support format $outFormat (supported=${encoder.supportedFormats})"
  }

  val stripped = when (requested.metadataPolicy) {
    dev.transmute.core.MetadataPolicy.PRESERVE -> decoded.ir
    dev.transmute.core.MetadataPolicy.STRIP_ALL -> decoded.ir.copy(metadata = VideoMetadata())
  }
  EncodedBytes(
    formatTag = AnyFormatTag(outFormat),
    bytes = encoder.encode(stripped, outFormat, requested, ctx),
  )
}

/**
 * Adds a fixed-output encode step using a specific [VideoEncoder] and [output] tag.
 */
fun <IN, OUT : dev.transmute.core.VideoFormatTag> PipelineBuilder<IN, Decoded<VideoFormat, VideoIR>>.then(
  encoder: VideoEncoder,
  output: OUT,
): PipelineBuilder<IN, EncodedBytes<VideoFormat, OUT>> = then { decoded, ctx ->
  val requested = (ctx.encodeOptions as? VideoEncodeOptions) ?: DefaultVideoEncodeOptions()
  val declared = requested.outputFormat
  require(declared == null || declared == output.format) {
    "encodeOptions.outputFormat=$declared conflicts with fixed output=${output.format}"
  }

  require(output.format in encoder.supportedFormats) {
    "Encoder ${encoder::class.simpleName} does not support format ${output.format} (supported=${encoder.supportedFormats})"
  }

  val stripped = when (requested.metadataPolicy) {
    dev.transmute.core.MetadataPolicy.PRESERVE -> decoded.ir
    dev.transmute.core.MetadataPolicy.STRIP_ALL -> decoded.ir.copy(metadata = VideoMetadata())
  }
  EncodedBytes(
    formatTag = output,
    bytes = encoder.encode(stripped, output.format, requested, ctx),
  )
}
