@file:Suppress("unused")

package dev.transmute.model.structure.audio

import dev.transmute.model.core.MediaStructure
import dev.transmute.model.structure.common.FtypData
import dev.transmute.model.structure.common.IsoBmffBoxSummary
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
    /** Summary of all top-level ISO BMFF boxes. */
    val boxes: List<IsoBmffBoxSummary>,
) : MediaStructure

/**
 * Parse this [M4aRaw] into an [M4aStructure].
 */
fun M4aRaw.toStructure(): M4aStructure =
    M4aStructure(
        ftyp = ftyp,
        boxes = boxes.map { box ->
            IsoBmffBoxSummary(
                type = box.type.value,
                dataSizeBytes = box.data.size.toLong(),
                childTypes = box.children.map { it.type.value },
            )
        },
    )
