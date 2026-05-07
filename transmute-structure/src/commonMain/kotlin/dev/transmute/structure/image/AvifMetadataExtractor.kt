@file:Suppress("unused")

package dev.transmute.structure.image

import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.structure.image.types.AvifRaw
import dev.transmute.structure.common.findHeifExifBytes
import dev.transmute.structure.common.findHeifXmpBytes

/**
 * Extract metadata from a parsed [AvifRaw].
 *
 * Supports:
 * - **EXIF** - from an `Exif` item in the `meta` box, located via `iloc`
 * - **XMP**  - from a `mime` item (application/rdf+xml) located via `iloc`
 *
 * AVIF files share the same ISO BMFF container structure as HEIF and store
 * metadata items referenced through the `meta > iloc` and `meta > iinf` boxes.
 */
fun AvifRaw.extractMetadata(): List<MediaMetadata> = buildList {
  extractExif()?.let(::add)
  extractXmp()?.let(::add)
}

// -- EXIF from iloc-referenced Exif item ---

private fun AvifRaw.extractExif(): MediaMetadata? {
  val tiffBytes = findHeifExifBytes(boxes) ?: return null
  if (tiffBytes.size < 8) return null
  return try {
    val reader = TiffStructureReader()
    val tiffRaw = reader.read(dev.transmute.model.core.Bytes(tiffBytes))
    tiffRawToExif(tiffRaw)
  } catch (_: Exception) {
    null
  }
}

// -- XMP from iloc-referenced mime item ---

private fun AvifRaw.extractXmp(): MediaMetadata? {
  val xmpBytes = findHeifXmpBytes(boxes) ?: return null
  val text = xmpBytes.decodeToString().trim()
  return parseXmpText(text)
}
