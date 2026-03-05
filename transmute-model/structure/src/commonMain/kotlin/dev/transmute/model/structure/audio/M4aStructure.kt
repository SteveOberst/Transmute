@file:Suppress("unused")

package dev.transmute.model.structure.audio

import dev.transmute.model.core.MediaStructure
import dev.transmute.model.structure.audio.types.M4aRaw
import dev.transmute.model.structure.audio.types.ftyp
import dev.transmute.model.structure.common.FtypData
import dev.transmute.model.structure.common.IsoBmffBoxTree
import dev.transmute.model.structure.common.toTree
import kotlinx.serialization.Serializable

/**
 * Structured, JSON-safe representation of an M4A (audio-only ISO BMFF) file.
 *
 * Large box payloads (e.g. `mdat` audio data) are summarised by type and size.
 */
@Serializable
data class M4aStructure(
  /** Parsed `ftyp` box (brand + compatible brands). */
  val ftyp: FtypData?,
  /** Full recursive ISO BMFF box hierarchy (payload bytes excluded). */
  val boxes: List<IsoBmffBoxTree>,
) : MediaStructure

/**
 * Parse this [dev.transmute.model.structure.audio.types.M4aRaw] into an [M4aStructure].
 */
fun M4aRaw.toStructure(): M4aStructure = M4aStructure(
  ftyp = ftyp,
  boxes = boxes.map { it.toTree() },
)
