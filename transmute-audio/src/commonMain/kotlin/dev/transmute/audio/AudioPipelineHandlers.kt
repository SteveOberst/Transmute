package dev.transmute.audio

import dev.transmute.core.AnyFormatTag
import dev.transmute.core.AudioFormat
import dev.transmute.core.OutputFormat
import dev.transmute.core.TransmuteContext
import dev.transmute.core.pipeline.Decoded
import dev.transmute.core.pipeline.EncodedBytes
import dev.transmute.core.pipeline.PipelineHandler
import dev.transmute.core.pipeline.PipelineBuilder

/**
 * Audio decode handler: detects format (unless constrained), validates accepted formats,
 * then decodes via the registry.
 *
 * Reads [AudioDecodeOptions] from [TransmuteContext.decodeOptions].
 */
class AudioDecodeHandler(
  private val detector: (ByteArray) -> AudioFormat = AudioFormatDetector::detect,
  private val decoders: AudioDecoderRegistry = AudioRegistries.decoders,
) : PipelineHandler<ByteArray, Decoded<AudioFormat, AudioIR>> {

  override suspend fun handle(value: ByteArray, context: TransmuteContext): Decoded<AudioFormat, AudioIR> {
    AudioRegistries.installDefaultsIfEmpty()

    val options = (context.decodeOptions as? AudioDecodeOptions) ?: CanonicalAudioDecodeOptions()
    val accepted = options.acceptedInputFormats

    val format = if (accepted.size == 1) accepted.first() else detector(value)
    if (accepted.isNotEmpty() && format !in accepted) {
      error("Detected audio format $format not in acceptedInputFormats=$accepted")
    }

    val decoder = decoders.decoderFor(format) ?: error("No audio decoder for $format")
    val ir = decoder.decode(value, options, context)
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
 * Reads [AudioEncodeOptions] from [TransmuteContext.encodeOptions].
 */
class AudioDynamicEncodeHandler(
  private val encoders: AudioEncoderRegistry = AudioRegistries.encoders,
) : PipelineHandler<Decoded<AudioFormat, AudioIR>, EncodedBytes<AudioFormat, AnyFormatTag<AudioFormat>>> {

  override suspend fun handle(
    value: Decoded<AudioFormat, AudioIR>,
    context: TransmuteContext,
  ): EncodedBytes<AudioFormat, AnyFormatTag<AudioFormat>> {
    AudioRegistries.installDefaultsIfEmpty()

    val requested = (context.encodeOptions as? AudioEncodeOptions) ?: CanonicalAudioEncodeOptions()
    val outFormat = when (val declared = requested.outputFormat) {
      OutputFormat.ORIGINAL -> {
        val inFormat = value.format
        if (encoders.encoderFor(inFormat) != null) inFormat else AudioFormat.WAV
      }
      is OutputFormat.Exact -> declared.format
    }

    val encoder = encoders.encoderFor(outFormat) ?: error("No audio encoder for $outFormat")
    val stripped = when (requested.metadataPolicy) {
      dev.transmute.core.MetadataPolicy.PRESERVE -> value.ir
      dev.transmute.core.MetadataPolicy.STRIP_ALL -> value.ir.copy(metadata = AudioMetadata())
    }
    val bytes = encoder.encode(stripped, outFormat, requested, context)
    return EncodedBytes(formatTag = AnyFormatTag(outFormat), bytes = bytes)
  }
}

/**
 * Fixed-output audio encode handler.
 *
 * Reads [AudioEncodeOptions] from [TransmuteContext.encodeOptions] and enforces that any
 * explicit `outputFormat` matches the fixed output format.
 */
class AudioFixedEncodeHandler<OUT : dev.transmute.core.AudioFormatTag>(
  private val output: OUT,
  private val encoders: AudioEncoderRegistry = AudioRegistries.encoders,
) : PipelineHandler<Decoded<AudioFormat, AudioIR>, EncodedBytes<AudioFormat, OUT>> {

  override suspend fun handle(value: Decoded<AudioFormat, AudioIR>, context: TransmuteContext): EncodedBytes<AudioFormat, OUT> {
    AudioRegistries.installDefaultsIfEmpty()

    val requested = (context.encodeOptions as? AudioEncodeOptions) ?: CanonicalAudioEncodeOptions()
    when (val declared = requested.outputFormat) {
      OutputFormat.ORIGINAL -> Unit
      is OutputFormat.Exact -> require(declared.format == output.format) {
        "encodeOptions.outputFormat=${declared.format} conflicts with fixed output=${output.format}"
      }
    }

    val encoder = encoders.encoderFor(output.format) ?: error("No audio encoder for ${output.format}")
    val stripped = when (requested.metadataPolicy) {
      dev.transmute.core.MetadataPolicy.PRESERVE -> value.ir
      dev.transmute.core.MetadataPolicy.STRIP_ALL -> value.ir.copy(metadata = AudioMetadata())
    }
    val bytes = encoder.encode(stripped, output.format, requested, context)
    return EncodedBytes(formatTag = output, bytes = bytes)
  }
}

/**
 * Adds a decode step using a specific [AudioDecoder].
 *
 * Format resolution uses:
 * - `acceptedInputFormats.single()` when provided, else
 * - [AudioDecoder.sniff] when available, else
 * - [AudioFormatDetector].
 */
fun <IN> PipelineBuilder<IN, ByteArray>.then(
  decoder: AudioDecoder,
  detector: (ByteArray) -> AudioFormat = AudioFormatDetector::detect,
): PipelineBuilder<IN, Decoded<AudioFormat, AudioIR>> = then { bytes, ctx ->
  val options = (ctx.decodeOptions as? AudioDecodeOptions) ?: CanonicalAudioDecodeOptions()
  val accepted = options.acceptedInputFormats

  val format = when {
    accepted.size == 1 -> accepted.first()
    else -> decoder.sniff(bytes) ?: detector(bytes)
  }
  if (accepted.isNotEmpty() && format !in accepted) {
    error("Detected audio format $format not in acceptedInputFormats=$accepted")
  }
  require(format in decoder.supportedFormats) {
    "Decoder ${decoder::class.simpleName} does not support format $format (supported=${decoder.supportedFormats})"
  }

  val ir = decoder.decode(bytes, options, ctx)
  Decoded(format, ir)
}

/**
 * Adds an encode step using a specific [AudioEncoder].
 *
 * Output selection:
 * - explicit `encodeOptions.outputFormat`, else
 * - the input format if encodable, otherwise WAV.
 */
fun <IN> PipelineBuilder<IN, Decoded<AudioFormat, AudioIR>>.then(
  encoder: AudioEncoder,
): PipelineBuilder<IN, EncodedBytes<AudioFormat, AnyFormatTag<AudioFormat>>> = then { decoded, ctx ->
  val requested = (ctx.encodeOptions as? AudioEncodeOptions) ?: CanonicalAudioEncodeOptions()
  val outFormat = when (val declared = requested.outputFormat) {
    OutputFormat.ORIGINAL -> {
      val inFormat = decoded.format
      if (inFormat in encoder.supportedFormats) inFormat else AudioFormat.WAV
    }
    is OutputFormat.Exact -> declared.format
  }

  require(outFormat in encoder.supportedFormats) {
    "Encoder ${encoder::class.simpleName} does not support format $outFormat (supported=${encoder.supportedFormats})"
  }

  val stripped = when (requested.metadataPolicy) {
    dev.transmute.core.MetadataPolicy.PRESERVE -> decoded.ir
    dev.transmute.core.MetadataPolicy.STRIP_ALL -> decoded.ir.copy(metadata = AudioMetadata())
  }
  EncodedBytes(
    formatTag = AnyFormatTag(outFormat),
    bytes = encoder.encode(stripped, outFormat, requested, ctx),
  )
}

/**
 * Adds a fixed-output encode step using a specific [AudioEncoder] and [output] tag.
 */
fun <IN, OUT : dev.transmute.core.AudioFormatTag> PipelineBuilder<IN, Decoded<AudioFormat, AudioIR>>.then(
  encoder: AudioEncoder,
  output: OUT,
): PipelineBuilder<IN, EncodedBytes<AudioFormat, OUT>> = then { decoded, ctx ->
  val requested = (ctx.encodeOptions as? AudioEncodeOptions) ?: CanonicalAudioEncodeOptions()
  when (val declared = requested.outputFormat) {
    OutputFormat.ORIGINAL -> Unit
    is OutputFormat.Exact -> require(declared.format == output.format) {
      "encodeOptions.outputFormat=${declared.format} conflicts with fixed output=${output.format}"
    }
  }

  require(output.format in encoder.supportedFormats) {
    "Encoder ${encoder::class.simpleName} does not support format ${output.format} (supported=${encoder.supportedFormats})"
  }

  val stripped = when (requested.metadataPolicy) {
    dev.transmute.core.MetadataPolicy.PRESERVE -> decoded.ir
    dev.transmute.core.MetadataPolicy.STRIP_ALL -> decoded.ir.copy(metadata = AudioMetadata())
  }
  EncodedBytes(
    formatTag = output,
    bytes = encoder.encode(stripped, output.format, requested, ctx),
  )
}
