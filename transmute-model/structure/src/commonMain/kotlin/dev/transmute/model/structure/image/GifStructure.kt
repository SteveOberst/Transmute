@file:Suppress("unused")

package dev.transmute.model.structure.image

import dev.transmute.model.core.MediaStructure
import kotlinx.serialization.Serializable

/**
 * Structured, JSON-safe representation of a GIF file.
 *
 * Raw image block data is excluded; animation control metadata and
 * image geometry from the logical screen descriptor are captured.
 */
@Serializable
data class GifStructure(
    /** GIF version (87a or 89a). */
    val version: GifVersion,
    /** Canvas width in pixels. */
    val widthPixels: Int,
    /** Canvas height in pixels. */
    val heightPixels: Int,
    /** Number of image frames (image descriptor blocks). */
    val frameCount: Int,
    /** `true` when the file contains more than one frame. */
    val isAnimated: Boolean,
    /** Per-frame graphic control extensions (delay, disposal, transparency). */
    val graphicControls: List<GifGraphicControl>,
) : MediaStructure

/**
 * Parse this [GifRaw] into a [GifStructure].
 */
fun GifRaw.toStructure(): GifStructure =
    GifStructure(
        version = version,
        widthPixels = width.value,
        heightPixels = height.value,
        frameCount = frameCount,
        isAnimated = isAnimated,
        graphicControls = graphicControlExtensions,
    )
