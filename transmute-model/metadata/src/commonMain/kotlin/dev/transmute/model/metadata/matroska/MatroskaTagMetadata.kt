@file:Suppress("unused")

package dev.transmute.model.metadata.matroska

import dev.transmute.model.core.LanguageTag
import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.core.Utf8String
import dev.transmute.model.identify.EbmlId
import dev.transmute.model.metadata.common.PayloadRef
import kotlinx.serialization.Serializable

/**
 * Matroska/WebM tag metadata extracted from the EBML `Tags` element.
 *
 * Models the on-disk hierarchy:
 * `Tags` -> `Tag*` -> (`Targets`?, `SimpleTag*`, other/unknown elements).
 *
 * EBML element payload bytes are not embedded; unknown elements are preserved as
 * [MatroskaUnknownElement] with payload size summaries, and [PayloadRef] where available.
 */
@Serializable
data class MatroskaTagMetadata(
  /** All Tag elements found, in document order. */
  val tags: List<MatroskaTag>,
  /** Unknown EBML elements under Tags (rare, but preserved). */
  val extra: List<MatroskaUnknownElement> = emptyList(),
  /** Reference to the original Tags element payload when available. */
  val original: PayloadRef? = null,
) : MediaMetadata

@Serializable
data class MatroskaTag(
  val targets: MatroskaTargets? = null,
  /** SimpleTags within this Tag element (preserves order). */
  val simpleTags: List<MatroskaSimpleTag> = emptyList(),
  /** Unknown EBML children of Tag. */
  val extra: List<MatroskaUnknownElement> = emptyList(),
)

@Serializable
data class MatroskaTargets(
  /** Unsigned EBML integer. */
  val targetTypeValue: ULong? = null,
  /** Target type string (e.g. "ALBUM", "TRACK"). */
  val targetType: Utf8String? = null,
  /** Track UIDs this tag applies to. Empty if it applies to the whole segment. */
  val trackUIDs: List<ULong> = emptyList(),
  val editionUIDs: List<ULong> = emptyList(),
  val chapterUIDs: List<ULong> = emptyList(),
  val attachmentUIDs: List<ULong> = emptyList(),
  /** Unknown EBML children of Targets. */
  val extra: List<MatroskaUnknownElement> = emptyList(),
)

/**
 * A single Matroska `SimpleTag` - a key/value pair that can be nested.
 */
@Serializable
data class MatroskaSimpleTag(
  /** TagName (required). */
  val name: Utf8String,
  /** TagLanguage (optional). */
  val language: LanguageTag? = null,
  /** TagDefault (optional). */
  val default: Boolean? = null,
  /** TagString (optional). */
  val value: Utf8String? = null,
  /** TagBinary (optional). */
  val binary: PayloadRef? = null,
  /** Nested child SimpleTags (preserves order). */
  val children: List<MatroskaSimpleTag> = emptyList(),
  /** Unknown EBML children of SimpleTag. */
  val extra: List<MatroskaUnknownElement> = emptyList(),
)

@Serializable
data class MatroskaUnknownElement(
  val id: EbmlId,
  /** Payload size in bytes (excludes the EBML id+size headers). */
  val payloadSizeBytes: ULong,
  /** Present for leaf elements when a stable slice can be referenced. */
  val payload: PayloadRef? = null,
  /** Unknown master elements preserve their child tree. */
  val children: List<MatroskaUnknownElement> = emptyList(),
)

