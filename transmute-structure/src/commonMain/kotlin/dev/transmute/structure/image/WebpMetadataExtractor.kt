@file:Suppress("unused")

package dev.transmute.structure.image

import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.core.asBytes
import dev.transmute.model.structure.image.types.WebpRaw
import dev.transmute.model.structure.image.types.chunks

/**
 * Extract metadata from a parsed [WebpRaw].
 *
 * WebP stores metadata as RIFF sub-chunks:
 * - `EXIF` - raw TIFF byte stream (same as JPEG APP1, minus the `"Exif\0\0"` header)
 * - `XMP ` - raw XMP XML text
 * - `ICCP` - raw ICC profile bytes
 */
fun WebpRaw.extractMetadata(): List<MediaMetadata> = buildList {
    extractExif()?.let(::add)
    extractXmp()?.let(::add)
    extractIcc()?.let(::add)
}

// -- EXIF ---------------------------------------------------------------------

private fun WebpRaw.extractExif(): MediaMetadata? {
    val chunk = chunks.firstOrNull { it.id.value == "EXIF" } ?: return null
    var data = chunk.data
    // Some encoders prepend "Exif\0\0" like in JPEG - strip it if present
    val exifHeader = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0x00, 0x00)
    val raw = if (data.data.startsWith(exifHeader) && data.size > 6)
        data.data.copyOfRange(6, data.size).asBytes()
    else data
    if (raw.size < 8) return null
    return try {
        val reader = TiffStructureReader()
        val tiffRaw = reader.read(raw)
        tiffRawToExif(tiffRaw)
    } catch (_: Exception) {
        null
    }
}

// -- XMP ----------------------------------------------------------------------

private fun WebpRaw.extractXmp(): MediaMetadata? {
    val chunk = chunks.firstOrNull { it.id.value == "XMP " } ?: return null
    val text = chunk.data.data.decodeToString().trim()
    return parseXmpText(text)
}

// -- ICC ----------------------------------------------------------------------

private fun WebpRaw.extractIcc(): MediaMetadata? {
    val chunk = chunks.firstOrNull { it.id.value == "ICCP" } ?: return null
    if (chunk.data.size < 128) return null
    return try {
        parseIccProfile(chunk.data.data)
    } catch (_: Exception) {
        null
    }
}
