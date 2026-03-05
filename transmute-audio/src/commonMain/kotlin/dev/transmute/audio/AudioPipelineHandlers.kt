package dev.transmute.audio

import dev.transmute.codec.OutputFormat
import dev.transmute.codec.pipeline.Decoded
import dev.transmute.codec.pipeline.EncodedBytes
import dev.transmute.codec.pipeline.PipelineBuilder
import dev.transmute.codec.pipeline.PipelineHandler
import dev.transmute.common.PipelineContext
import dev.transmute.io.TSource
import dev.transmute.model.core.Bytes

/**
 * Audio decode handler: detects format (unless constrained), validates accepted formats,
 * then decodes via the registry.
 *
 * Reads [AudioDecodeOptions] from [PipelineContext.decodeOptions].
 */
class AudioDecodeHandler(
  private val detector: (Bytes) -> AudioFormat = AudioFormatDetector::detect,
  private val decoders: AudioDecoderRegistry = AudioRegistries.decoders,
) : PipelineHandler<TSource, Decoded<AudioFormat, AudioIR>> {

  override suspend fun handle(value: TSource, context: PipelineContext): Decoded<AudioFormat, AudioIR> {
    AudioRegistries.installDefaultsIfEmpty()

    val bytes = if (value is Bytes) value else Bytes(value.readAll())
    val options = (context.decodeOptions as? AudioDecodeOptions) ?: CanonicalAudioDecodeOptions()
    val accepted = options.acceptedInputFormats

    val format = if (accepted.size == 1) accepted.first() else detector(bytes)
    if (accepted.isNotEmpty() && format !in accepted) {
      error("Detected audio format $format not in acceptedInputFormats=$accepted")
    }

    val decoder = decoders.decoderFor(format) ?: error("No audio decoder for $format")
    val ir = decoder.decode(bytes, options, context)
    return Decoded(format, ir)
  }
}

/**
 * Dynamic audio encode handler.
 *
 * Chooses output format as:
 * - explicit `encodeOptions.outputFormat`, else
 * - the input format if encodable, otherwise WAV.
 *
 * Applies [AudioEncodeOptions.metadataPolicy] during encoding (not as a transform step).
 *
 * Reads [AudioEncodeOptions] from [PipelineContext.encodeOptions].
 */
class AudioDynamicEncodeHandler(private val encoders: AudioEncoderRegistry = AudioRegistries.encoders) :
  PipelineHandler<Decoded<AudioFormat, AudioIR>, EncodedBytes<AudioFormat>> {

  override suspend fun handle(value: Decoded<AudioFormat, AudioIR>, context: PipelineContext): EncodedBytes<AudioFormat> {
    AudioRegistries.installDefaultsIfEmpty()

    val requested = (context.encodeOptions as? AudioEncodeOptions) ?: CanonicalAudioEncodeOptions()
    val outFormat = when (val declared = requested.outputFormat) {
      OutputFormat.ORIGINAL -> {
        val inFormat = value.format
        if (encoders.encoderFor(inFormat) != null) inFormat else AudioFormat.Wav
      }
      is OutputFormat.Exact -> declared.format
    }

    val encoder = encoders.encoderFor(outFormat) ?: error("No audio encoder for $outFormat")
    val stripped = when (requested.metadataPolicy) {
      dev.transmute.codec.MetadataPolicy.PRESERVE -> value.ir
      dev.transmute.codec.MetadataPolicy.STRIP_ALL -> value.ir.copy(metadata = AudioMetadata())
    }
    val bytes = encoder.encode(stripped, outFormat, requested, context)
    return EncodedBytes(format = outFormat, bytes = bytes)
  }
}

/**
 * Fixed-output audio encode handler.
 *
 * Reads [AudioEncodeOptions] from [PipelineContext.encodeOptions] and enforces that any
 * explicit `outputFormat` matches the fixed output format.
 */
class AudioFixedEncodeHandler<OUT : AudioFormat>(
  private val output: OUT,
  private val encoders: AudioEncoderRegistry = AudioRegistries.encoders,
) : PipelineHandler<Decoded<AudioFormat, AudioIR>, EncodedBytes<OUT>> {

  override suspend fun handle(value: Decoded<AudioFormat, AudioIR>, context: PipelineContext): EncodedBytes<OUT> {
    AudioRegistries.installDefaultsIfEmpty()

    val requested = (context.encodeOptions as? AudioEncodeOptions) ?: CanonicalAudioEncodeOptions()
    when (val declared = requested.outputFormat) {
      OutputFormat.ORIGINAL -> Unit
      is OutputFormat.Exact -> require(declared.format == output) {
        "encodeOptions.outputFormat=${declared.format} conflicts with fixed output=$output"
      }
    }

    val encoder = encoders.encoderFor(output) ?: error("No audio encoder for $output")
    val stripped = when (requested.metadataPolicy) {
      dev.transmute.codec.MetadataPolicy.PRESERVE -> value.ir
      dev.transmute.codec.MetadataPolicy.STRIP_ALL -> value.ir.copy(metadata = AudioMetadata())
    }
    val bytes = encoder.encode(stripped, output, requested, context)
    return EncodedBytes(format = output, bytes = bytes)
  }
}

/**
 * Adds a decode step using a specific [AudioDecoder].
 *
 * Format resolution uses:
 * - `acceptedInputFormats.single()` when provided, else
 * - [AudioFormatDetector].
 */
fun <IN> PipelineBuilder<IN, Bytes>.then(
  decoder: AudioDecoder,
  detector: (Bytes) -> AudioFormat = AudioFormatDetector::detect,
): PipelineBuilder<IN, Decoded<AudioFormat, AudioIR>> = then { raw, ctx ->
  val options = (ctx.decodeOptions as? AudioDecodeOptions) ?: CanonicalAudioDecodeOptions()
  val accepted = options.acceptedInputFormats

  val format = when {
    accepted.size == 1 -> accepted.first()
    else -> detector(raw)
  }
  if (accepted.isNotEmpty() && format !in accepted) {
    error("Detected audio format $format not in acceptedInputFormats=$accepted")
  }
  require(format in decoder.supportedFormats) {
    "Decoder ${decoder::class.simpleName} does not support format $format (supported=${decoder.supportedFormats})"
  }

  val ir = decoder.decode(raw, options, ctx)
  Decoded(format, ir)
}

/**
 * Adds an encode step using a specific [AudioEncoder].
 *
 * Output selection:
 * - explicit `encodeOptions.outputFormat`, else
 * - the input format if encodable, otherwise WAV.
 */
fun <IN> PipelineBuilder<IN, Decoded<AudioFormat, AudioIR>>.then(encoder: AudioEncoder): PipelineBuilder<IN, EncodedBytes<AudioFormat>> =
  then { decoded, ctx ->
    val requested = (ctx.encodeOptions as? AudioEncodeOptions) ?: CanonicalAudioEncodeOptions()
    val outFormat = when (val declared = requested.outputFormat) {
      OutputFormat.ORIGINAL -> {
        val inFormat = decoded.format
        if (inFormat in encoder.supportedFormats) inFormat else AudioFormat.Wav
      }
      is OutputFormat.Exact -> declared.format
    }

    require(outFormat in encoder.supportedFormats) {
      "Encoder ${encoder::class.simpleName} does not support format $outFormat (supported=${encoder.supportedFormats})"
    }

    val stripped = when (requested.metadataPolicy) {
      dev.transmute.codec.MetadataPolicy.PRESERVE -> decoded.ir
      dev.transmute.codec.MetadataPolicy.STRIP_ALL -> decoded.ir.copy(metadata = AudioMetadata())
    }
    EncodedBytes(format = outFormat, bytes = encoder.encode(stripped, outFormat, requested, ctx))
  }

/**
 * Adds a fixed-output encode step using a specific [AudioEncoder] and [output] tag.
 */
fun <IN, OUT : AudioFormat> PipelineBuilder<IN, Decoded<AudioFormat, AudioIR>>.then(
  encoder: AudioEncoder,
  output: OUT,
): PipelineBuilder<IN, EncodedBytes<OUT>> = then { decoded, ctx ->
  val requested = (ctx.encodeOptions as? AudioEncodeOptions) ?: CanonicalAudioEncodeOptions()
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
    dev.transmute.codec.MetadataPolicy.STRIP_ALL -> decoded.ir.copy(metadata = AudioMetadata())
  }
  EncodedBytes(format = output, bytes = encoder.encode(stripped, output, requested, ctx))
}
