package dev.transmute

import dev.transmute.image.CanonicalImageDecodeOptions
import dev.transmute.image.CanonicalImageEncodeOptions
import dev.transmute.image.ImageDecodeOptions
import dev.transmute.image.ImageEncodeOptions
import dev.transmute.image.ImageFormat
import dev.transmute.image.withMetadataPolicy

fun <IN, OUT> EncodeStage<IN, OUT, ImageEncodeOptions>.options(
  block: EncodeOptionsMutator<ImageFormat>.() -> Unit,
): EncodeStage<IN, OUT, ImageEncodeOptions> = apply {
  val mutator = DefaultEncodeOptionsMutator(options.metadataPolicy, options.outputFormat)
  mutator.block()

  options = when (val current = options) {
    is CanonicalImageEncodeOptions ->
      current.copy(metadataPolicy = mutator.metadataPolicy, outputFormat = mutator.outputFormat)

    else -> {
      val preservesFormat = mutator.outputFormat == current.outputFormat
      if (preservesFormat) current.withMetadataPolicy(mutator.metadataPolicy)
      else CanonicalImageEncodeOptions(metadataPolicy = mutator.metadataPolicy, outputFormat = mutator.outputFormat)
    }
  }
}

fun <IN, OUT> DecodeStage<IN, OUT, ImageDecodeOptions>.options(
  block: DecodeOptionsMutator<ImageFormat>.() -> Unit,
): DecodeStage<IN, OUT, ImageDecodeOptions> = apply {
  val mutator = DefaultDecodeOptionsMutator(options.acceptedInputFormats)
  mutator.block()

  options = when (val current = options) {
    is CanonicalImageDecodeOptions -> current.copy(acceptedInputFormats = mutator.acceptedInputFormats.toSet())
    else -> CanonicalImageDecodeOptions(acceptedInputFormats = mutator.acceptedInputFormats.toSet())
  }
}

