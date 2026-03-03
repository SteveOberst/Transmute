@file:Suppress("unused")

package dev.transmute.structure.image

import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.metadata.png.PngTextChunkType
import dev.transmute.model.metadata.png.PngTextEntry
import dev.transmute.model.metadata.png.PngTextMetadata
import dev.transmute.model.structure.image.types.PngRaw
import dev.transmute.model.structure.image.types.iccp
import dev.transmute.model.structure.image.types.itxtChunks
import dev.transmute.model.structure.image.types.textChunks
import dev.transmute.model.structure.image.types.ztxtChunks
import dev.transmute.structure.util.inflateBytes

/**
 * Extract metadata from a parsed [dev.transmute.model.structure.image.types.PngRaw].
 *
 * Supports:
 * - **EXIF** - `eXIf` chunk (registered 2017) containing a raw TIFF byte stream
 * - **ICC Profile** - `iCCP` chunk (compressed ICC profile via zlib / deflate)
 * - **XMP** - `iTXt` chunk with keyword `"XML:com.adobe.xmp"` (per XMP spec)
 * - **PNG Text** - aggregated `tEXt` / `zTXt` / `iTXt` entries
 */
fun PngRaw.extractMetadata(): List<MediaMetadata> = buildList {
    extractExif()?.let(::add)
    extractIcc()?.let(::add)
    extractXmp()?.let(::add)
    extractText()?.let(::add)
}

// -- EXIF via eXIf chunk ------------------------------------------------------

private fun PngRaw.extractExif(): MediaMetadata? {
    val chunk = chunks.firstOrNull { it.type.value == "eXIf" } ?: return null
    val data = chunk.data
    if (data.size < 8) return null
    return try {
        val reader = TiffStructureReader()
        val tiffRaw = reader.read(data)
        tiffRawToExif(tiffRaw)
    } catch (_: Exception) {
        null
    }
}

// -- ICC via iCCP chunk -------------------------------------------------------

/**
 * The iCCP chunk stores a zlib-compressed ICC profile. The profile data after the
 * compression method byte is deflate-compressed. We inflate it and then delegate
 * to [parseIccProfile].
 */
private fun PngRaw.extractIcc(): MediaMetadata? {
    val iccp = iccp ?: return null
    val compressed = iccp.compressedProfile.data
    if (compressed.isEmpty()) return null
    return try {
        val decompressed = inflateBytes(compressed)
        if (decompressed.size < 128) return null
        parseIccProfile(decompressed)
    } catch (_: Exception) {
        null
    }
}

// -- XMP via iTXt chunk -------------------------------------------------------

private fun PngRaw.extractXmp(): MediaMetadata? {
    val xmpChunk = itxtChunks.firstOrNull { it.keyword == "XML:com.adobe.xmp" }
        ?: return null
    return parseXmpText(xmpChunk.text)
}

// -- PNG text metadata (tEXt / zTXt / iTXt) ----------------------------------

private fun PngRaw.extractText(): PngTextMetadata? {
    val entries = buildList {
        for (t in textChunks) {
            add(PngTextEntry(
                keyword = t.keyword,
                text = t.text,
                chunkType = PngTextChunkType.TEXT,
            ))
        }
        for (z in ztxtChunks) {
            // zTXt is deflate-compressed Latin-1. Inflate and decode the text.
            val text = try {
                inflateBytes(z.compressedText.data).decodeToString()
            } catch (_: Exception) {
                "[compressed: ${z.compressedText.size} bytes]"
            }
            add(PngTextEntry(
                keyword = z.keyword,
                text = text,
                chunkType = PngTextChunkType.ZTXT,
            ))
        }
        for (i in itxtChunks) {
            // Skip the XMP entry - already extracted as XmpMetadata
            if (i.keyword == "XML:com.adobe.xmp") continue
            add(PngTextEntry(
                keyword = i.keyword,
                text = i.text,
                chunkType = PngTextChunkType.ITXT,
                language = i.languageTag.ifEmpty { null },
                translatedKeyword = i.translatedKeyword.ifEmpty { null },
            ))
        }
    }
    return if (entries.isEmpty()) null else PngTextMetadata(entries)
}
