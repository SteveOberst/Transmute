@file:Suppress("unused")

package dev.transmute.model.structure.video

import dev.transmute.model.core.MediaStructure
import kotlinx.serialization.Serializable

/**
 * Structured, JSON-safe representation of a WebM (EBML/Matroska) file.
 *
 * EBML element payloads are excluded; key EBML header metadata
 * and element counts are captured.
 */
@Serializable
data class WebmStructure(
    /** Parsed EBML header data (docType, version). */
    val headerData: EbmlHeaderData?,
    /** Total number of top-level EBML elements. */
    val elementCount: Int,
) : MediaStructure

/**
 * Parse this [WebmRaw] into a [WebmStructure].
 */
fun WebmRaw.toStructure(): WebmStructure =
    WebmStructure(
        headerData = headerData,
        elementCount = elements.size,
    )
