@file:Suppress("unused")

package dev.transmute.model.structure.image

import dev.transmute.model.core.MediaStructure
import kotlinx.serialization.Serializable

/**
 * Structured, JSON-safe representation of a WebP file.
 *
 * Raw bitstream chunk data is excluded; image geometry and
 * compression format metadata are captured.
 */
@Serializable
data class WebpStructure(
    /** Image width in pixels (from the format-specific header). */
    val widthPixels: Int?,
    /** Image height in pixels (from the format-specific header). */
    val heightPixels: Int?,
    /** WebP compression format (Lossy, Lossless, or Extended). */
    val format: WebpFormat,
    /** `true` when the file contains an alpha channel. */
    val hasAlpha: Boolean,
    /** `true` when the file is an animated WebP. */
    val hasAnimation: Boolean,
    /** Number of RIFF chunks. */
    val chunkCount: Int,
) : MediaStructure

/**
 * Parse this [WebpRaw] into a [WebpStructure].
 */
fun WebpRaw.toStructure(): WebpStructure =
    WebpStructure(
        widthPixels = width?.value,
        heightPixels = height?.value,
        format = format,
        hasAlpha = hasAlpha,
        hasAnimation = hasAnimation,
        chunkCount = chunks.size,
    )
