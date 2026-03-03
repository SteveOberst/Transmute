@file:Suppress("unused")

package dev.transmute.model.metadata.xmp

import dev.transmute.model.core.MediaMetadata
import kotlinx.serialization.Serializable

// -- Top-level model ----------------------------------------------------------

/**
 * XMP (Extensible Metadata Platform) metadata as a typed XML tree.
 *
 * Models the on-disk hierarchy faithfully:
 * - An XML document
 *   - `<x:xmpmeta>` wrapper
 *     - `<rdf:RDF>` root
 *       - `<rdf:Description>` blocks with namespaced properties
 *
 * This representation preserves the full XML structure - elements,
 * attributes, text nodes, and namespace bindings - so the consumer
 * sees exactly what was in the file.
 */
@Serializable
data class XmpMetadata(
    val root: XmpElement,
) : MediaMetadata

// -- XML tree nodes -----------------------------------------------------------

@Serializable
data class XmpElement(
    /** Namespace URI (e.g. `"http://ns.adobe.com/xap/1.0/"`), `null` for unnamespaced. */
    val namespace: String? = null,
    /** Local element name (e.g. `"Description"`, `"Creator"`). */
    val name: String,
    /** Attributes on this element. */
    val attributes: List<XmpAttribute> = emptyList(),
    /** Child nodes (elements and text). */
    val children: List<XmpNode> = emptyList(),
)

@Serializable
sealed class XmpNode {
    @Serializable
    data class Element(val element: XmpElement) : XmpNode()

    @Serializable
    data class Text(val content: String) : XmpNode()
}

@Serializable
data class XmpAttribute(
    val namespace: String? = null,
    val name: String,
    val value: String,
)
