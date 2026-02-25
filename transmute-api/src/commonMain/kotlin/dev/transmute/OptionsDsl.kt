package dev.transmute

import dev.transmute.model.core.MediaFormat
import dev.transmute.codec.MetadataPolicy
import dev.transmute.codec.OutputFormat

/**
 * Mutable view for domain-specific `encode { options { ... } }` blocks.
 *
 * This exists to keep option types immutable while still allowing a concise "mutation" DSL.
 */
interface EncodeOptionsMutator<F : MediaFormat<*, *>> {
  var metadataPolicy: MetadataPolicy
  var outputFormat: OutputFormat<F>
}

/**
 * Mutable view for domain-specific `decode { options { ... } }` blocks.
 *
 * The default decode pipelines can use [acceptedInputFormats] to skip format detection.
 */
interface DecodeOptionsMutator<F : MediaFormat<*, *>> {
  val acceptedInputFormats: MutableSet<F>
}

internal class DefaultEncodeOptionsMutator<F : MediaFormat<*, *>>(
  override var metadataPolicy: MetadataPolicy,
  override var outputFormat: OutputFormat<F>,
) : EncodeOptionsMutator<F>

internal class DefaultDecodeOptionsMutator<F : MediaFormat<*, *>>(
  base: Set<F>,
) : DecodeOptionsMutator<F> {
  override val acceptedInputFormats: MutableSet<F> = base.toMutableSet()
}

