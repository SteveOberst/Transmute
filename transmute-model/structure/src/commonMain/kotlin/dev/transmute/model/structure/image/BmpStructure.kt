@file:Suppress("unused")

package dev.transmute.model.structure.image

import dev.transmute.model.core.MediaStructure
import kotlinx.serialization.Serializable

/**
 * Structured, JSON-safe representation of a BMP file.
 *
 * Raw pixel data is excluded; image geometry and encoding parameters
 * from the file and DIB headers are captured.
 */
@Serializable
data class BmpStructure(
    /** Image width in pixels (absolute value). */
    val widthPixels: Int,
    /** Image height in pixels (absolute value). */
    val heightPixels: Int,
    /** Bits per pixel (colour depth). */
    val bitsPerPixel: Int,
    /** Compression method. */
    val compression: BmpCompression?,
    /** `true` if pixels are stored top-down (negative height in DIB header). */
    val isTopDown: Boolean,
    /** Total file size in bytes from the file header. */
    val fileSizeBytes: Long,
) : MediaStructure

/**
 * Parse this [BmpRaw] into a [BmpStructure].
 */
fun BmpRaw.toStructure(): BmpStructure =
    BmpStructure(
        widthPixels = width.value,
        heightPixels = height.value,
        bitsPerPixel = bitsPerPixel,
        compression = compression,
        isTopDown = isTopDown,
        fileSizeBytes = fileHeader.fileSize.toLong(),
    )
