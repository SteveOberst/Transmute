@file:Suppress("unused")

package dev.transmute.model.structure.video

import dev.transmute.model.core.MediaStructure
import dev.transmute.model.structure.common.FtypData
import dev.transmute.model.structure.common.IsoBmffBoxTree
import dev.transmute.model.structure.common.toTree
import dev.transmute.model.structure.video.types.Mp4Raw
import dev.transmute.model.structure.video.types.ftyp
import kotlinx.serialization.Serializable

/**
 * Structured, JSON-safe representation of an MP4 (ISO BMFF) file.
 *
 * Large box payloads (e.g. `mdat` media data) are summarised by type and size.
 */
@Serializable
data class Mp4Structure(
  /** Parsed `ftyp` box (brand + compatible brands). */
  val ftyp: FtypData?,
  /** Full recursive ISO BMFF box hierarchy (payload bytes excluded). */
  val boxes: List<IsoBmffBoxTree>,
) : MediaStructure

/**
 * Parse this [dev.transmute.model.structure.video.types.Mp4Raw] into an [Mp4Structure].
 */
fun Mp4Raw.toStructure(): Mp4Structure = Mp4Structure(
  ftyp = ftyp,
  boxes = boxes.map { it.toTree() },
)
