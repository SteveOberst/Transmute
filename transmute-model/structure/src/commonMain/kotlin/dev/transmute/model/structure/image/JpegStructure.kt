@file:Suppress("unused")

package dev.transmute.model.structure.image

import dev.transmute.model.core.MediaStructure
import kotlinx.serialization.Serializable

/**
 * Structured, JSON-safe representation of a JPEG file.
 *
 * Segment payloads (entropy-coded image data) are excluded.
 * Dimensions, colour space, and optional JFIF metadata are captured.
 */
@Serializable
data class JpegStructure(
    /** Image dimensions and component info from the SOF segment. */
    val sofData: JpegSofData?,
    /** JFIF APP0 header (density, thumbnail dimensions), if present. */
    val jfifHeader: JpegJfifHeader?,
    /** List of COM segment text values. */
    val comments: List<String>,
    /** Total number of segments (markers) in the file. */
    val segmentCount: Int,
) : MediaStructure

/**
 * Parse this [JpegRaw] into a [JpegStructure].
 */
fun JpegRaw.toStructure(): JpegStructure =
    JpegStructure(
        sofData = sofData,
        jfifHeader = jfifHeader,
        comments = comments,
        segmentCount = segments.size,
    )
