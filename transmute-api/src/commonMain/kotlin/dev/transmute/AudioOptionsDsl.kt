package dev.transmute

import dev.transmute.audio.AudioDecodeOptions
import dev.transmute.audio.AudioEncodeOptions
import dev.transmute.audio.AudioFormat
import dev.transmute.audio.CanonicalAudioDecodeOptions
import dev.transmute.audio.CanonicalAudioEncodeOptions

fun <IN, OUT> EncodeStage<IN, OUT, AudioEncodeOptions>.options(
  block: EncodeOptionsMutator<AudioFormat>.() -> Unit,
): EncodeStage<IN, OUT, AudioEncodeOptions> = apply {
  val mutator = DefaultEncodeOptionsMutator(options.metadataPolicy, options.outputFormat)
  mutator.block()

  options = when (val current = options) {
    is CanonicalAudioEncodeOptions ->
      current.copy(metadataPolicy = mutator.metadataPolicy, outputFormat = mutator.outputFormat)

    else -> CanonicalAudioEncodeOptions(metadataPolicy = mutator.metadataPolicy, outputFormat = mutator.outputFormat)
  }
}

fun <IN, OUT> DecodeStage<IN, OUT, AudioDecodeOptions>.options(
  block: DecodeOptionsMutator<AudioFormat>.() -> Unit,
): DecodeStage<IN, OUT, AudioDecodeOptions> = apply {
  val mutator = DefaultDecodeOptionsMutator(options.acceptedInputFormats)
  mutator.block()

  options = when (val current = options) {
    is CanonicalAudioDecodeOptions -> current.copy(acceptedInputFormats = mutator.acceptedInputFormats.toSet())
    else -> CanonicalAudioDecodeOptions(acceptedInputFormats = mutator.acceptedInputFormats.toSet())
  }
}
