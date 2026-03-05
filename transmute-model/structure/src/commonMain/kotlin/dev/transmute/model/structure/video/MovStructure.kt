@file:Suppress("unused")

package dev.transmute.model.structure.video

import dev.transmute.model.core.MediaStructure
import dev.transmute.model.structure.common.FtypData
import dev.transmute.model.structure.common.IsoBmffBoxTree
import dev.transmute.model.structure.common.toTree
import dev.transmute.model.structure.video.types.MovRaw
import dev.transmute.model.structure.video.types.ftyp
import kotlinx.serialization.Serializable

/**
 * Structured, JSON-safe representation of a MOV (QuickTime / ISO BMFF) file.
 *
 * Large box payloads (e.g. `mdat` media data) are summarised by type and size.
 */
@Serializable
data class MovStructure(
  /** Parsed `ftyp` box (brand + compatible brands), if present. */
  val ftyp: FtypData?,
  /** Full recursive ISO BMFF box hierarchy (payload bytes excluded). */
  val boxes: List<IsoBmffBoxTree>,
) : MediaStructure

/**
 * Parse this [dev.transmute.model.structure.video.types.MovRaw] into a [MovStructure].
 */
fun MovRaw.toStructure(): MovStructure = MovStructure(
  ftyp = ftyp,
  boxes = boxes.map { it.toTree() },
)
