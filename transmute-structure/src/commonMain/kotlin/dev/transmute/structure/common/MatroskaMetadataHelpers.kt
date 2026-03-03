@file:Suppress("unused")

package dev.transmute.structure.common

import dev.transmute.model.metadata.matroska.MatroskaSimpleTag
import dev.transmute.model.metadata.matroska.MatroskaTag
import dev.transmute.model.metadata.matroska.MatroskaTagMetadata
import dev.transmute.model.structure.common.EbmlElement
import dev.transmute.model.structure.video.types.MatroskaIds

// -- Matroska / WebM tag extraction helpers -----------------------------------

/**
 * Extract [MatroskaTagMetadata] from a list of top-level EBML elements.
 *
 * Navigates: `Segment > Tags > Tag*` and parses `Targets` + `SimpleTag`
 * children within each `Tag`.
 *
 * Returns `null` if no Tags element is found or it contains no tags.
 */
fun extractMatroskaTags(elements: List<EbmlElement>): MatroskaTagMetadata? {
    val segment = elements.firstOrNull { it.id == MatroskaIds.Segment } ?: return null
    val tagsElement = segment.children.firstOrNull { it.id == MatroskaIds.Tags } ?: return null
    val tagElements = tagsElement.children.filter { it.id == MatroskaIds.Tag }
    if (tagElements.isEmpty()) return null

    val tags = tagElements.mapNotNull { parseTag(it) }
    if (tags.isEmpty()) return null
    return MatroskaTagMetadata(tags = tags)
}

private fun parseTag(tagElement: EbmlElement): MatroskaTag? {
    // Parse Targets
    val targets = tagElement.children.firstOrNull { it.id == MatroskaIds.Targets }
    val targetTypeValue = targets?.children
        ?.firstOrNull { it.id == MatroskaIds.TargetTypeValue }
        ?.data?.data?.let { readEbmlUIntBytes(it) }?.toInt()
    val targetType = targets?.children
        ?.firstOrNull { it.id == MatroskaIds.TargetType }
        ?.data?.data?.decodeToString()?.trim('\u0000')

    // Parse Target UIDs
    val trackUIDs = targets?.children
        ?.filter { it.id == MatroskaIds.TagTrackUID }
        ?.mapNotNull { it.data.data.let(::readEbmlUIntBytes) }
        ?: emptyList()
    val editionUIDs = targets?.children
        ?.filter { it.id == MatroskaIds.TagEditionUID }
        ?.mapNotNull { it.data.data.let(::readEbmlUIntBytes) }
        ?: emptyList()
    val chapterUIDs = targets?.children
        ?.filter { it.id == MatroskaIds.TagChapterUID }
        ?.mapNotNull { it.data.data.let(::readEbmlUIntBytes) }
        ?: emptyList()
    val attachmentUIDs = targets?.children
        ?.filter { it.id == MatroskaIds.TagAttachmentUID }
        ?.mapNotNull { it.data.data.let(::readEbmlUIntBytes) }
        ?: emptyList()

    // Parse SimpleTags (recursively)
    val simpleTags = tagElement.children
        .filter { it.id == MatroskaIds.SimpleTag }
        .mapNotNull { parseSimpleTag(it) }

    if (simpleTags.isEmpty()) return null
    return MatroskaTag(
        targetTypeValue = targetTypeValue,
        targetType = targetType,
        trackUIDs = trackUIDs,
        editionUIDs = editionUIDs,
        chapterUIDs = chapterUIDs,
        attachmentUIDs = attachmentUIDs,
        simpleTags = simpleTags,
    )
}

private fun parseSimpleTag(element: EbmlElement): MatroskaSimpleTag? {
    val name = element.children
        .firstOrNull { it.id == MatroskaIds.TagName }
        ?.data?.data?.decodeToString()?.trim('\u0000')
        ?: return null

    val language = element.children
        .firstOrNull { it.id == MatroskaIds.TagLanguage }
        ?.data?.data?.decodeToString()?.trim('\u0000')
        ?.takeIf { it.isNotEmpty() && it != "und" }

    val default = element.children
        .firstOrNull { it.id == MatroskaIds.TagDefault }
        ?.data?.data?.let { readEbmlUIntBytes(it) != 0L }

    val tagString = element.children
        .firstOrNull { it.id == MatroskaIds.TagString }
        ?.data?.data?.decodeToString()?.trim('\u0000')

    val binarySize = element.children
        .firstOrNull { it.id == MatroskaIds.TagBinary }
        ?.data?.data?.size?.toLong()

    // Recursively parse nested SimpleTags
    val children = element.children
        .filter { it.id == MatroskaIds.SimpleTag }
        .mapNotNull { parseSimpleTag(it) }

    return MatroskaSimpleTag(
        name = name,
        language = language,
        default = default,
        value = tagString,
        binarySize = binarySize,
        children = children,
    )
}

/** Read a big-endian unsigned integer from raw EBML data bytes. */
private fun readEbmlUIntBytes(bytes: ByteArray): Long {
    var v = 0L
    for (b in bytes) v = (v shl 8) or (b.toLong() and 0xFF)
    return v
}
