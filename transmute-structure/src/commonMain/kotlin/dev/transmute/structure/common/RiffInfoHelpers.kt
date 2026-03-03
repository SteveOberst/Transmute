@file:Suppress("unused")

package dev.transmute.structure.common

import dev.transmute.model.metadata.riff.RiffInfoEntry
import dev.transmute.model.structure.common.RiffChunk

// -- Well-known RIFF INFO tag ID -> human-readable name ------------------------

internal val RIFF_INFO_TAG_NAMES: Map<String, String> = mapOf(
    "IARL" to "Archival Location",
    "IART" to "Artist",
    "ICMS" to "Commissioned",
    "ICMT" to "Comment",
    "ICOP" to "Copyright",
    "ICRD" to "Creation Date",
    "ICRP" to "Cropped",
    "IDIM" to "Dimensions",
    "IDPI" to "Dots Per Inch",
    "IENG" to "Engineer",
    "IGNR" to "Genre",
    "IKEY" to "Keywords",
    "ILGT" to "Lightness",
    "IMED" to "Medium",
    "INAM" to "Title",
    "IPLT" to "Palette Setting",
    "IPRD" to "Product",
    "ISBJ" to "Subject",
    "ISFT" to "Software",
    "ISHP" to "Sharpness",
    "ISRC" to "Source",
    "ISRF" to "Source Form",
    "ITCH" to "Technician",
    "ITRK" to "Track Number",
)

// -- Shared RIFF INFO entry extraction ----------------------------------------

/**
 * Extract RIFF INFO entries from a `LIST` chunk with form type `INFO`.
 *
 * Each child chunk of the INFO list is treated as a text entry:
 * the chunk ID is the tag and the data payload is the text value
 * (null-terminated, trimmed).
 */
internal fun extractRiffInfoEntries(infoList: RiffChunk): List<RiffInfoEntry> {
    return infoList.children.mapNotNull { child ->
        val tag = child.id.value
        val text = child.data.data
            .decodeToString()
            .trimEnd('\u0000', ' ')
        if (text.isEmpty()) return@mapNotNull null
        RiffInfoEntry(
            tag = tag,
            name = RIFF_INFO_TAG_NAMES[tag],
            value = text,
        )
    }
}
