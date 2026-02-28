package dev.transmute.playground.shared

import kotlinx.serialization.Serializable

/**
 * Inspection result for an uploaded file.
 *
 * Returned by `POST /api/inspect/{handle}`.
 */
@Serializable
data class InspectResult(
    val domain: MediaDomainDto,
    val format: String,
    val fileSize: Long,
    val properties: Map<String, String> = emptyMap(),
    val structure: StructureNode? = null,
    val decodedBy: String? = null,
)

/**
 * Recursive tree node for file structure visualization (FTYP boxes, PNG chunks, etc.).
 */
@Serializable
data class StructureNode(
    val name: String,
    val type: String = "",
    val offset: Long = 0,
    val size: Long = 0,
    val properties: Map<String, String> = emptyMap(),
    val children: List<StructureNode> = emptyList(),
)
