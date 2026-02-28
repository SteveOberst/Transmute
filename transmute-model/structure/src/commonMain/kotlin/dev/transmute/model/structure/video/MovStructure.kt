@file:Suppress("unused")

package dev.transmute.model.structure.video

import dev.transmute.model.core.MediaStructure
import dev.transmute.model.structure.common.FtypData
import dev.transmute.model.structure.common.IsoBmffBoxSummary
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
    /** Summary of all top-level ISO BMFF boxes. */
    val boxes: List<IsoBmffBoxSummary>,
) : MediaStructure

/**
 * Parse this [MovRaw] into a [MovStructure].
 */
fun MovRaw.toStructure(): MovStructure =
    MovStructure(
        ftyp = ftyp,
        boxes = boxes.map { box ->
            IsoBmffBoxSummary(
                type = box.type.value,
                dataSizeBytes = box.data.size.toLong(),
                childTypes = box.children.map { it.type.value },
            )
        },
    )
