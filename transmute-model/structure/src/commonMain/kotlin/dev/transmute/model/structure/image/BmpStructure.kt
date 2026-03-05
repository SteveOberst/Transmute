@file:Suppress("unused")

package dev.transmute.model.structure.image

import dev.transmute.model.core.MediaStructure
import dev.transmute.model.structure.image.types.BmpColorEntry
import dev.transmute.model.structure.image.types.BmpDibHeader
import dev.transmute.model.structure.image.types.BmpFileHeader
import dev.transmute.model.structure.image.types.BmpRaw
import kotlinx.serialization.Serializable

/**
 * Structured representation of a BMP file, mirroring the on-disk layout.
 *
 * ```
 * | BmpFileHeader (14 B) | BmpDibHeader (40+ B) | Colour Table | Pixel Data |
 * ```
 *
 * Pixel data is excluded to keep the structure serialisable;
 * [dev.transmute.model.structure.image.types.BmpRaw.pixelData] retains the raw pixels.
 */
@Serializable
data class BmpStructure(
  /** 14-byte file header (signature, file size, reserved fields, pixel-data offset). */
  val fileHeader: BmpFileHeader,
  /** DIB (info) header - `BITMAPINFOHEADER` or a larger variant (40-124 bytes). */
  val dibHeader: BmpDibHeader,
  /** Colour table entries - populated when bits-per-pixel <= 8; empty otherwise. */
  val colorTable: List<BmpColorEntry>,
) : MediaStructure

/**
 * Parse this [dev.transmute.model.structure.image.types.BmpRaw] into a [BmpStructure].
 */
fun BmpRaw.toStructure(): BmpStructure = BmpStructure(
  fileHeader = fileHeader,
  dibHeader = dibHeader,
  colorTable = colorTable,
)
