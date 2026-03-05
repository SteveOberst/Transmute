@file:Suppress("unused")

package dev.transmute.model.structure.image

import dev.transmute.model.core.MediaStructure
import dev.transmute.model.structure.common.FtypData
import dev.transmute.model.structure.common.IsoBmffBoxTree
import dev.transmute.model.structure.common.toTree
import dev.transmute.model.structure.image.types.AvifRaw
import dev.transmute.model.structure.image.types.ftyp
import kotlinx.serialization.Serializable

/**
 * Structured, JSON-safe representation of an AVIF (AV1 Image File Format) file.
 *
 * Large box payloads (e.g. `mdat` encoded image data) are summarised by type and size.
 */
@Serializable
data class AvifStructure(
  /** Parsed `ftyp` box (brand + compatible brands). */
  val ftyp: FtypData?,
  /** Full recursive ISO BMFF box hierarchy (payload bytes excluded). */
  val boxes: List<IsoBmffBoxTree>,
) : MediaStructure

/**
 * Parse this [dev.transmute.model.structure.image.types.AvifRaw] into an [AvifStructure].
 */
fun AvifRaw.toStructure(): AvifStructure = AvifStructure(
  ftyp = ftyp,
  boxes = boxes.map { it.toTree() },
)
