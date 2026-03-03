@file:Suppress("unused")

package dev.transmute.model.metadata.matroska

import dev.transmute.model.core.MediaMetadata
import kotlinx.serialization.Serializable

/**
 * Matroska/WebM tag metadata extracted from the EBML `Tags` element.
 *
 * Matroska files embed metadata tags inside a hierarchical structure:
 * ```
 * Tags
 *   +- Tag
 *        +- Targets (scope: track, chapter, edition, attachment)
 *        +- SimpleTag*
 *              +- TagName    (UTF-8 string)
 *              +- TagString  (UTF-8 string value)
 *              +- TagBinary  (binary data - reported as byte size only)
 * ```
 *
 * Well-known tag names include `TITLE`, `ARTIST`, `ALBUM`, `DATE_RELEASED`,
 * `GENRE`, `COMMENT`, `ENCODER`, `DESCRIPTION`, `COMPOSER`, etc.
 */
@Serializable
data class MatroskaTagMetadata(
    /** All tags found in the file, in document order. */
    val tags: List<MatroskaTag>,
) : MediaMetadata

/**
 * A single Matroska `Tag` element containing target scope and one or
 * more simple name/value pairs.
 *
 * Matches the Matroska spec hierarchy:
 * ```
 * Tag
 *   +- Targets
 *   |    +- TargetTypeValue
 *   |    +- TargetType
 *   |    +- TagTrackUID*
 *   |    +- TagEditionUID*
 *   |    +- TagChapterUID*
 *   |    +- TagAttachmentUID*
 *   +- SimpleTag*
 * ```
 */
@Serializable
data class MatroskaTag(
    /** Target type value (50 = album, 30 = track, etc.). Null if unspecified. */
    val targetTypeValue: Int? = null,
    /** Target type string (e.g. "ALBUM", "TRACK"). Null if unspecified. */
    val targetType: String? = null,
    /** Track UIDs this tag applies to. Empty if it applies to the whole segment. */
    val trackUIDs: List<Long> = emptyList(),
    /** Edition UIDs this tag applies to. */
    val editionUIDs: List<Long> = emptyList(),
    /** Chapter UIDs this tag applies to. */
    val chapterUIDs: List<Long> = emptyList(),
    /** Attachment UIDs this tag applies to. */
    val attachmentUIDs: List<Long> = emptyList(),
    /** Simple tags within this Tag element. */
    val simpleTags: List<MatroskaSimpleTag>,
)

/**
 * A single Matroska `SimpleTag` - a key/value pair that can be nested.
 *
 * Per the spec, SimpleTags can contain child SimpleTags to form
 * a hierarchical metadata tree:
 * ```
 * SimpleTag
 *   +- TagName
 *   +- TagLanguage
 *   +- TagDefault
 *   +- TagString
 *   +- TagBinary
 *   +- SimpleTag* (nested children)
 * ```
 */
@Serializable
data class MatroskaSimpleTag(
    /** Tag name (e.g. `"TITLE"`, `"ARTIST"`). */
    val name: String,
    /** Tag language (BCP-47 or ISO-639-2). Null if default/undetermined. */
    val language: String? = null,
    /** Whether this is the default language tag. Null if unspecified. */
    val default: Boolean? = null,
    /** UTF-8 string value. Null if the tag is binary-only. */
    val value: String? = null,
    /** Size in bytes of binary data if present. Null if the tag is text-only. */
    val binarySize: Long? = null,
    /** Nested child SimpleTags. Empty if this is a leaf tag. */
    val children: List<MatroskaSimpleTag> = emptyList(),
)
