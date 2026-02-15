package dev.transmute.video

import dev.transmute.core.ConversionContext
import dev.transmute.core.MetadataPolicy
import dev.transmute.core.pipeline.Transform
import dev.transmute.core.pipeline.TransformId

/**
 * Applies [MetadataPolicy] to a [VideoIR].
 *
 * - [MetadataPolicy.PRESERVE]: passes the IR through unchanged.
 * - [MetadataPolicy.STRIP_ALL]: clears all metadata fields.
 */
class VideoMetadataTransform(
  private val policy: MetadataPolicy,
) : Transform {

  override val id: TransformId = TransformId("video-metadata")

  override suspend fun apply(ir: Any, context: ConversionContext): Any {
    if (ir !is VideoIR) return ir
    return when (policy) {
      MetadataPolicy.PRESERVE -> ir
      MetadataPolicy.STRIP_ALL -> ir.copy(metadata = VideoMetadata())
    }
  }
}
