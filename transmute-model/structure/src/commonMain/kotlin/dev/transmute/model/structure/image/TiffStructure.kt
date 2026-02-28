@file:Suppress("unused")

package dev.transmute.model.structure.image

import dev.transmute.model.core.MediaStructure
import kotlinx.serialization.Serializable

/**
 * Structured, JSON-safe representation of a TIFF file.
 *
 * Raw IFD entry values and strip/tile data are excluded; image geometry
 * and compression info extracted from IFD 0 are captured.
 */
@Serializable
data class TiffStructure(
    /** Image width from IFD 0 tag 256 (ImageWidth), in pixels. */
    val widthPixels: Int?,
    /** Image height from IFD 0 tag 257 (ImageLength), in pixels. */
    val heightPixels: Int?,
    /** Bits per sample per channel from IFD 0 tag 258 (BitsPerSample). */
    val bitsPerSample: List<Int>,
    /** Image compression code from IFD 0 tag 259 (Compression). */
    val compression: Int?,
    /** Number of Image File Directories in the file. */
    val ifdCount: Int,
) : MediaStructure

/**
 * Parse this [TiffRaw] into a [TiffStructure].
 */
fun TiffRaw.toStructure(): TiffStructure =
    TiffStructure(
        widthPixels = width?.value,
        heightPixels = height?.value,
        bitsPerSample = bitsPerSample,
        compression = compression,
        ifdCount = ifds.size,
    )
