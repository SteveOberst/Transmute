@file:Suppress("unused")

package dev.transmute.model.structure.video

import dev.transmute.model.core.MediaStructure
import kotlinx.serialization.Serializable

/**
 * Structured, JSON-safe representation of an AVI (RIFF-based) file.
 *
 * Movie data (`movi` LIST) is excluded; header metadata from the
 * `avih` chunk is captured.
 */
@Serializable
data class AviStructure(
    /** Parsed AVI main header (`avih` chunk). */
    val mainHeader: AviMainHeader?,
    /** Number of logical streams (video + audio + …). */
    val streamCount: Int,
) : MediaStructure

/**
 * Parse this [AviRaw] into an [AviStructure].
 */
fun AviRaw.toStructure(): AviStructure =
    AviStructure(
        mainHeader = mainHeader,
        streamCount = streamCount,
    )
