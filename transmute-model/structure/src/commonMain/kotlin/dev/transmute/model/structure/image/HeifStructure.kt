@file:Suppress("unused")

package dev.transmute.model.structure.image

import dev.transmute.model.core.MediaStructure
import dev.transmute.model.structure.common.FtypData
import dev.transmute.model.structure.common.IsoBmffBoxSummary
import kotlinx.serialization.Serializable

/**
 * Structured, JSON-safe representation of a HEIF (High Efficiency Image File) file.
 *
 * Large box payloads (e.g. `mdat` encoded image data) are summarised by type and size.
 */
@Serializable
data class HeifStructure(
    /** Parsed `ftyp` box (brand + compatible brands). */
    val ftyp: FtypData?,
    /** Summary of all top-level ISO BMFF boxes. */
    val boxes: List<IsoBmffBoxSummary>,
) : MediaStructure

/**
 * Parse this [HeifRaw] into a [HeifStructure].
 */
fun HeifRaw.toStructure(): HeifStructure =
    HeifStructure(
        ftyp = ftyp,
        boxes = boxes.map { box ->
            IsoBmffBoxSummary(
                type = box.type.value,
                dataSizeBytes = box.data.size.toLong(),
                childTypes = box.children.map { it.type.value },
            )
        },
    )
