@file:Suppress("unused")

package dev.transmute.structure.image

import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.structure.image.types.HeifRaw
import dev.transmute.structure.common.findHeifExifBytes
import dev.transmute.structure.common.findHeifXmpBytes

/**
 * Extract metadata from a parsed [HeifRaw].
 *
 * Supports:
 * - **EXIF** - from an `Exif` item in the `meta` box, located via `iloc`
 * - **XMP**  - from a `mime` item (application/rdf+xml) located via `iloc`
 *
 * HEIF/HEIC files store metadata items in `mdat`, referenced through the
 * `meta > iloc` (item location) and `meta > iinf` (item information) boxes.
 */
fun HeifRaw.extractMetadata(): List<MediaMetadata> = buildList {
  extractExif()?.let(::add)
  extractXmp()?.let(::add)
}

// -- EXIF from iloc-referenced Exif item ---

private fun HeifRaw.extractExif(): MediaMetadata? {
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

private fun HeifRaw.extractXmp(): MediaMetadata? {
  val xmpBytes = findHeifXmpBytes(boxes) ?: return null
  val text = xmpBytes.decodeToString().trim()
  return parseXmpText(text)
}
