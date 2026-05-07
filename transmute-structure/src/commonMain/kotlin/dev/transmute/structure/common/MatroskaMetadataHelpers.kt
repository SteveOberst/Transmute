@file:Suppress("unused")

package dev.transmute.structure.common

import dev.transmute.model.core.LanguageTag
import dev.transmute.model.core.Utf8String
import dev.transmute.model.metadata.common.PayloadRef
import dev.transmute.model.metadata.matroska.MatroskaSimpleTag
import dev.transmute.model.metadata.matroska.MatroskaTag
import dev.transmute.model.metadata.matroska.MatroskaTagMetadata
import dev.transmute.model.metadata.matroska.MatroskaTargets
import dev.transmute.model.metadata.matroska.MatroskaUnknownElement
import dev.transmute.model.structure.common.EbmlElement
import dev.transmute.model.structure.video.types.MatroskaIds

// -- Matroska / WebM tag extraction helpers ---

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
  val unknownChildren = mutableListOf<MatroskaUnknownElement>()

  // Parse Targets (and preserve unknown children)
  val targetsEl = tagElement.children.firstOrNull { it.id == MatroskaIds.Targets }
  val targets = targetsEl?.let(::parseTargets)

  // Parse SimpleTags (recursively) + preserve unknown siblings
  val simpleTags = mutableListOf<MatroskaSimpleTag>()
  for (child in tagElement.children) {
    when (child.id) {
      MatroskaIds.Targets -> Unit
      MatroskaIds.SimpleTag -> parseSimpleTag(child)?.let(simpleTags::add)
      else -> unknownChildren.add(child.toUnknown())
    }
  }

  if (simpleTags.isEmpty()) return null
  return MatroskaTag(
    targets = targets,
    simpleTags = simpleTags,
    extra = unknownChildren,
  )
}

private fun parseTargets(targets: EbmlElement): MatroskaTargets {
  var targetTypeValue: ULong? = null
  var targetType: String? = null
  val trackUIDs = mutableListOf<ULong>()
  val editionUIDs = mutableListOf<ULong>()
  val chapterUIDs = mutableListOf<ULong>()
  val attachmentUIDs = mutableListOf<ULong>()
  val extra = mutableListOf<MatroskaUnknownElement>()

  for (child in targets.children) {
    when (child.id) {
      MatroskaIds.TargetTypeValue -> targetTypeValue = child.data.data.let(::readEbmlUIntBytes)
      MatroskaIds.TargetType -> targetType = child.data.data.decodeToString().trim('\u0000').takeIf { it.isNotEmpty() }
      MatroskaIds.TagTrackUID -> trackUIDs.add(readEbmlUIntBytes(child.data.data))
      MatroskaIds.TagEditionUID -> editionUIDs.add(readEbmlUIntBytes(child.data.data))
      MatroskaIds.TagChapterUID -> chapterUIDs.add(readEbmlUIntBytes(child.data.data))
      MatroskaIds.TagAttachmentUID -> attachmentUIDs.add(readEbmlUIntBytes(child.data.data))
      else -> extra.add(child.toUnknown())
    }
  }

  return MatroskaTargets(
    targetTypeValue = targetTypeValue,
    targetType = targetType?.let(::Utf8String),
    trackUIDs = trackUIDs,
    editionUIDs = editionUIDs,
    chapterUIDs = chapterUIDs,
    attachmentUIDs = attachmentUIDs,
    extra = extra,
  )
}

private fun parseSimpleTag(element: EbmlElement): MatroskaSimpleTag? {
  var name: String? = null
  var language: String? = null
  var default: Boolean? = null
  var tagString: String? = null
  var binarySize: Long? = null
  val children = mutableListOf<MatroskaSimpleTag>()
  val extra = mutableListOf<MatroskaUnknownElement>()

  for (child in element.children) {
    when (child.id) {
      MatroskaIds.TagName -> name = child.data.data.decodeToString().trim('\u0000')
      MatroskaIds.TagLanguage -> language = child.data.data.decodeToString().trim('\u0000').takeIf { it.isNotEmpty() && it != "und" }
      MatroskaIds.TagDefault -> default = readEbmlUIntBytes(child.data.data) != 0uL
      MatroskaIds.TagString -> tagString = child.data.data.decodeToString().trim('\u0000')
      MatroskaIds.TagBinary -> binarySize = child.data.data.size.toLong()
      MatroskaIds.SimpleTag -> parseSimpleTag(child)?.let(children::add)
      else -> extra.add(child.toUnknown())
    }
  }

  val n = name ?: return null
  return MatroskaSimpleTag(
    name = Utf8String(n),
    language = language?.let(::LanguageTag),
    default = default,
    value = tagString?.let(::Utf8String),
    binary = binarySize?.let { PayloadRef(sizeBytes = it.toULong()) },
    children = children,
    extra = extra,
  )
}

/** Read a big-endian unsigned integer from raw EBML data bytes. */
private fun readEbmlUIntBytes(bytes: ByteArray): ULong {
  var v = 0uL
  for (b in bytes) v = (v shl 8) or (b.toULong() and 0xFFuL)
  return v
}

private fun EbmlElement.toUnknown(): MatroskaUnknownElement {
  val childrenUnknown = children.map { it.toUnknown() }
  val payloadSize = if (children.isNotEmpty()) {
    // Master payload is the concatenation of child elements (including their EBML headers).
    children.sumOf { it.toBytes().size.toULong() }
  } else {
    data.size.toULong()
  }
  return MatroskaUnknownElement(
    id = id,
    payloadSizeBytes = payloadSize,
    payload = if (children.isEmpty()) PayloadRef(sizeBytes = data.size.toULong()) else null,
    children = childrenUnknown,
  )
}
