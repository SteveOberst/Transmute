package dev.transmute.image

import dev.transmute.core.ConversionContext
import dev.transmute.core.MetadataPolicy
import dev.transmute.core.pipeline.Transform
import dev.transmute.core.pipeline.TransformId

/**
 * Applies [MetadataPolicy] to an [ImageIR].
 *
 * - [MetadataPolicy.PRESERVE]: passes the IR through unchanged.
 * - [MetadataPolicy.STRIP_ALL]: clears EXIF, XMP, and app metadata.
 */
class ImageMetadataTransform(
  private val policy: MetadataPolicy,
) : Transform {

  override val id: TransformId = TransformId("image-metadata")

  override suspend fun apply(ir: Any, context: ConversionContext): Any {
    if (ir !is ImageIR) return ir
    return when (policy) {
      MetadataPolicy.PRESERVE -> ir
      MetadataPolicy.STRIP_ALL -> ir.copy(metadata = ImageMetadata())
    }
  }
}
