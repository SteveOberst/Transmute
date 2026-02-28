@file:Suppress("unused")

package dev.transmute.model.structure.video

import dev.transmute.model.core.MediaStructure
import kotlinx.serialization.Serializable

/**
 * Structured, JSON-safe representation of an MKV (Matroska) file.
 *
 * EBML element payloads are excluded; key EBML header metadata
 * and element counts are captured.
 */
@Serializable
data class MkvStructure(
    /** Parsed EBML header data (docType, version). */
    val headerData: EbmlHeaderData?,
    /** Total number of top-level EBML elements. */
    val elementCount: Int,
) : MediaStructure

/**
 * Parse this [MkvRaw] into a [MkvStructure].
 */
fun MkvRaw.toStructure(): MkvStructure =
    MkvStructure(
        headerData = headerData,
        elementCount = elements.size,
    )
