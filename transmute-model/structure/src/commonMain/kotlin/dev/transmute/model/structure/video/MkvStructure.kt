@file:Suppress("unused")

package dev.transmute.model.structure.video

import dev.transmute.model.core.MediaStructure
import dev.transmute.model.structure.common.EbmlElementTree
import dev.transmute.model.structure.common.toTree
import dev.transmute.model.structure.video.types.EbmlHeaderData
import dev.transmute.model.structure.video.types.MkvRaw
import dev.transmute.model.structure.video.types.headerData
import kotlinx.serialization.Serializable

/**
 * Structured representation of a Matroska (MKV) file, following the EBML container layout.
 *
 * ```
 * EBML Header (0x1A45DFA3)
 *   DocType = "matroska", DocTypeVersion, DocTypeReadVersion, ...
 * Segment (0x18538067)
 *   SeekHead -> Info -> Tracks -> Cues -> Attachments -> Chapters -> Tags -> Cluster*
 * ```
 *
 * The full EBML element tree is preserved; heavy Cluster data (audio/video
 * frame payloads) is naturally excluded by the reader which does not descend
 * into Cluster children.
 */
@Serializable
data class MkvStructure(
  /** Parsed EBML header metadata (DocType, version, read-version). */
  val headerData: EbmlHeaderData?,
  /** All top-level EBML elements in file order (EBML Header + Segment + ...). Payload bytes excluded. */
  val elements: List<EbmlElementTree>,
) : MediaStructure

/**
 * Parse this [dev.transmute.model.structure.video.types.MkvRaw] into a [MkvStructure].
 */
fun MkvRaw.toStructure(): MkvStructure = MkvStructure(
  headerData = headerData,
  elements = elements.map { it.toTree() },
)
