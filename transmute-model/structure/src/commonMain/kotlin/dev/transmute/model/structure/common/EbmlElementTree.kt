@file:Suppress("unused")

package dev.transmute.model.structure.common

import dev.transmute.model.identify.EbmlId
import kotlinx.serialization.Serializable

/**
 * JSON-safe summary of an EBML element tree.
 *
 * EBML leaf payload bytes are not inlined; [dataSizeBytes] records their size.
 */
@Serializable
data class EbmlElementTree(
    val id: EbmlId,
    val dataSizeBytes: Long,
    val children: List<EbmlElementTree> = emptyList(),
)

fun EbmlElement.toTree(): EbmlElementTree =
    EbmlElementTree(
        id = id,
        dataSizeBytes = data.size.toLong(),
        children = children.map { it.toTree() },
    )
