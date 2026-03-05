@file:Suppress("unused")

package dev.transmute.model.metadata.xmp

import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.metadata.common.PayloadRef
import kotlinx.serialization.Serializable

/**
 * XMP (Extensible Metadata Platform) metadata as an XML document.
 *
 * This model aims for round-trip-friendly structure:
 * - Preserves element/attribute ordering.
 * - Preserves namespace declarations at the element where they appear.
 * - Preserves comments, processing instructions, and CDATA as distinct node types.
 *
 * Exact byte-for-byte round-tripping of the original packet is only guaranteed when
 * the writer chooses to copy from [original]; otherwise XML reserialization may
 * normalize whitespace/quoting/prefix choices.
 */
@Serializable
data class XmpMetadata(
  val document: XmpDocument,
  /** Reference to the original XMP packet bytes when available. */
  val original: PayloadRef? = null,
) : MediaMetadata

@Deprecated("Use document.root", ReplaceWith("document.root"))
val XmpMetadata.root: XmpElement get() = document.root

@Serializable
data class XmpDocument(
  /** Misc nodes that appear before the root element (PIs/comments). */
  val prolog: List<XmpMiscNode> = emptyList(),
  val root: XmpElement,
)

@Serializable
sealed class XmpNode {
  @Serializable
  data class Element(val element: XmpElement) : XmpNode()

  /** Parsed character data (entities already unescaped). */
  @Serializable
  data class Text(val content: String) : XmpNode()

  /** CDATA section content (not entity-decoded). */
  @Serializable
  data class CData(val content: String) : XmpNode()

  @Serializable
  data class Comment(val content: String) : XmpNode()

  @Serializable
  data class ProcessingInstruction(val target: String, val data: String) : XmpNode()
}

@Serializable
sealed class XmpMiscNode {
  @Serializable
  data class Comment(val content: String) : XmpMiscNode()

  @Serializable
  data class ProcessingInstruction(val target: String, val data: String) : XmpMiscNode()
}

@Serializable
data class XmpElement(
  val name: XmpQName,
  /** Namespace declarations that appear on this element (`xmlns` / `xmlns:prefix`). */
  val namespaceDeclarations: List<XmpNamespaceDecl> = emptyList(),
  /** Attributes in source order (excluding namespace declarations). */
  val attributes: List<XmpAttribute> = emptyList(),
  /** Child nodes in source order. */
  val children: List<XmpNode> = emptyList(),
)

@Serializable
data class XmpQName(
  /** Prefix as written (e.g. `rdf`), null for unprefixed. */
  val prefix: String? = null,
  /** Local name (e.g. `Description`). */
  val localName: String,
  /** Resolved namespace URI in-scope at the element/attribute, null if unknown. */
  val namespaceUri: String? = null,
)

@Serializable
data class XmpNamespaceDecl(
  /** Declared prefix, null for default namespace (`xmlns`). */
  val prefix: String? = null,
  val namespaceUri: String,
)

@Serializable
data class XmpAttribute(
  val name: XmpQName,
  val value: String,
)
