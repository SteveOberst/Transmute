@file:Suppress("unused")

package dev.transmute.structure.image

import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.structure.image.types.TiffRaw

/**
 * Extract metadata from a parsed [TiffRaw].
 *
 * A TIFF file **is** essentially EXIF data - its IFD hierarchy maps
 * directly to an [ExifMetadata][dev.transmute.model.metadata.exif.ExifMetadata] model
 * via the shared [tiffRawToExif] helper.
 */
fun TiffRaw.extractMetadata(): List<MediaMetadata> = buildList {
    try {
        add(tiffRawToExif(this@extractMetadata))
    } catch (_: Exception) {
        // Skip malformed IFDs
    }
}
