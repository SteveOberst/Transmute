@file:Suppress("unused")

package dev.transmute.model.structure.video

import dev.transmute.model.core.MediaStructure
import dev.transmute.model.structure.common.FtypData
import dev.transmute.model.structure.common.IsoBmffBoxSummary
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
    /** Summary of all top-level ISO BMFF boxes. */
    val boxes: List<IsoBmffBoxSummary>,
) : MediaStructure

/**
 * Parse this [Mp4Raw] into an [Mp4Structure].
 */
fun Mp4Raw.toStructure(): Mp4Structure =
    Mp4Structure(
        ftyp = ftyp,
        boxes = boxes.map { box ->
            IsoBmffBoxSummary(
                type = box.type.value,
                dataSizeBytes = box.data.size.toLong(),
                childTypes = box.children.map { it.type.value },
            )
        },
    )
