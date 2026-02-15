package dev.transmute.audio

import dev.transmute.core.ConversionContext
import dev.transmute.core.MetadataPolicy
import dev.transmute.core.pipeline.Transform
import dev.transmute.core.pipeline.TransformId

/**
 * Applies [MetadataPolicy] to an [AudioIR].
 *
 * - [MetadataPolicy.PRESERVE]: passes the IR through unchanged.
 * - [MetadataPolicy.STRIP_ALL]: clears all metadata fields.
 */
class AudioMetadataTransform(
  private val policy: MetadataPolicy,
) : Transform {

  override val id: TransformId = TransformId("audio-metadata")

  override suspend fun apply(ir: Any, context: ConversionContext): Any {
    if (ir !is AudioIR) return ir
    return when (policy) {
      MetadataPolicy.PRESERVE -> ir
      MetadataPolicy.STRIP_ALL -> ir.copy(metadata = AudioMetadata())
    }
  }
}
