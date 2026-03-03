@file:Suppress("unused")

package dev.transmute.model.structure.video

import dev.transmute.model.core.MediaStructure
import dev.transmute.model.structure.common.EbmlElementTree
import dev.transmute.model.structure.common.toTree
import dev.transmute.model.structure.video.types.EbmlHeaderData
import dev.transmute.model.structure.video.types.WebmRaw
import dev.transmute.model.structure.video.types.headerData
import kotlinx.serialization.Serializable

/**
 * Structured representation of a WebM file, following the EBML container layout.
 *
 * WebM is a constrained EBML profile (VP8/VP9/AV1 video + Vorbis/Opus audio).
 * The on-disk layout is identical to Matroska; the DocType `"webm"` distinguishes it.
 *
 * ```
 * EBML Header  (DocType = "webm")
 * Segment
 *   SeekHead -> Info -> Tracks -> Cues -> Cluster*
 * ```
 *
 * The full EBML element tree is preserved; Cluster frame data is excluded by
 * the reader (Cluster children are not descended into).
 */
@Serializable
data class WebmStructure(
    /** Parsed EBML header metadata (DocType, version, read-version). */
    val headerData: EbmlHeaderData?,
    /** All top-level EBML elements in file order (EBML Header + Segment + ...). Payload bytes excluded. */
    val elements: List<EbmlElementTree>,
) : MediaStructure

/**
 * Parse this [dev.transmute.model.structure.video.types.WebmRaw] into a [WebmStructure].
 */
fun WebmRaw.toStructure(): WebmStructure =
    WebmStructure(
        headerData = headerData,
        elements = elements.map { it.toTree() },
    )
