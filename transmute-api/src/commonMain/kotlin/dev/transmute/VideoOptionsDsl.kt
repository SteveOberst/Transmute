package dev.transmute

import dev.transmute.video.CanonicalVideoDecodeOptions
import dev.transmute.video.CanonicalVideoEncodeOptions
import dev.transmute.video.VideoDecodeOptions
import dev.transmute.video.VideoEncodeOptions
import dev.transmute.video.VideoFormat

fun <IN, OUT> EncodeStage<IN, OUT, VideoEncodeOptions>.options(
  block: EncodeOptionsMutator<VideoFormat>.() -> Unit,
): EncodeStage<IN, OUT, VideoEncodeOptions> = apply {
  val mutator = DefaultEncodeOptionsMutator(options.metadataPolicy, options.outputFormat)
  mutator.block()

  options = when (val current = options) {
    is CanonicalVideoEncodeOptions ->
      current.copy(metadataPolicy = mutator.metadataPolicy, outputFormat = mutator.outputFormat)

    else -> CanonicalVideoEncodeOptions(metadataPolicy = mutator.metadataPolicy, outputFormat = mutator.outputFormat)
  }
}

fun <IN, OUT> DecodeStage<IN, OUT, VideoDecodeOptions>.options(
  block: DecodeOptionsMutator<VideoFormat>.() -> Unit,
): DecodeStage<IN, OUT, VideoDecodeOptions> = apply {
  val mutator = DefaultDecodeOptionsMutator(options.acceptedInputFormats)
  mutator.block()

  options = when (val current = options) {
    is CanonicalVideoDecodeOptions -> current.copy(acceptedInputFormats = mutator.acceptedInputFormats.toSet())
    else -> CanonicalVideoDecodeOptions(acceptedInputFormats = mutator.acceptedInputFormats.toSet())
  }
}

