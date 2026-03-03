@file:Suppress("unused")

package dev.transmute.structure.image

import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.core.asBytes
import dev.transmute.model.metadata.exif.ExifMetadata
import dev.transmute.model.metadata.icc.IccProfileMetadata
import dev.transmute.model.metadata.xmp.XmpMetadata
import dev.transmute.model.structure.image.types.JpegRaw
import dev.transmute.model.structure.image.types.segments

// -- Public API ---------------------------------------------------------------

/**
 * Extract all recognised metadata blocks from a parsed [JpegRaw].
 *
 * Currently supports:
 * - **EXIF** (APP1 with `"Exif\0\0"` prefix) - full IFD hierarchy
 * - **XMP** (APP1 with `"http://ns.adobe.com/xap/1.0/\0"` prefix)
 * - **ICC Profile** (APP2 with `"ICC_PROFILE\0"` prefix)
 */
fun JpegRaw.extractMetadata(): List<MediaMetadata> = buildList {
    extractExif()?.let(::add)
    extractXmp()?.let(::add)
    extractIccProfile()?.let(::add)
}

// -- EXIF extraction ----------------------------------------------------------

private val EXIF_HEADER = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0x00, 0x00) // "Exif\0\0"

private fun JpegRaw.extractExif(): ExifMetadata? {
    val app1 = segments.firstOrNull { seg ->
        seg.marker.toInt() == 0xE1 && seg.data.size > 6 && seg.data.data.startsWith(EXIF_HEADER)
    } ?: return null

    val tiffBytes = app1.data.data.copyOfRange(6, app1.data.size).asBytes()
    if (tiffBytes.size < 8) return null

    return try {
        val reader = TiffStructureReader()
        val tiffRaw = reader.read(tiffBytes)
        tiffRawToExif(tiffRaw)
    } catch (_: Exception) {
        null
    }
}

// -- XMP extraction -----------------------------------------------------------

private val XMP_HEADER = "http://ns.adobe.com/xap/1.0/\u0000".encodeToByteArray()

private fun JpegRaw.extractXmp(): XmpMetadata? {
    val app1 = segments.firstOrNull { seg ->
        seg.marker.toInt() == 0xE1 && seg.data.size > XMP_HEADER.size &&
            seg.data.data.startsWith(XMP_HEADER)
    } ?: return null

    val xmlText = app1.data.data
        .copyOfRange(XMP_HEADER.size, app1.data.size)
        .decodeToString()
        .trim()

    return parseXmpText(xmlText)
}

// -- ICC Profile extraction ---------------------------------------------------

private val ICC_HEADER = "ICC_PROFILE\u0000".encodeToByteArray()

private fun JpegRaw.extractIccProfile(): IccProfileMetadata? {
    val app2Segments = segments.filter { seg ->
        seg.marker.toInt() == 0xE2 && seg.data.size > ICC_HEADER.size + 2 &&
            seg.data.data.startsWith(ICC_HEADER)
    }
    if (app2Segments.isEmpty()) return null

    val chunks = app2Segments
        .map { seg ->
            val seqNum = seg.data.data[ICC_HEADER.size].toInt() and 0xFF
            val payload = seg.data.data.copyOfRange(ICC_HEADER.size + 2, seg.data.size)
            seqNum to payload
        }
        .sortedBy { it.first }

    val profileData = chunks.fold(ByteArray(0)) { acc, (_, chunk) ->
        acc + chunk
    }

    if (profileData.size < 128) return null
    return parseIccProfile(profileData)
}
