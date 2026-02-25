@file:Suppress("unused")

package dev.transmute.model.metadata

import dev.transmute.model.core.ModelExtension

/**
 * Flags describing properties of a metadata entry.
 */
enum class MetadataFlag {
    /** Value was computed or estimated rather than read directly. */
    Estimated,

    /** Value may be truncated or incomplete. */
    Truncated,

    /** Value comes from a deprecated tag or obsolete source. */
    Deprecated,

    /** Value encoding or meaning is uncertain. */
    Ambiguous,
}

/**
 * A single raw metadata entry as read from the file.
 */
data class MetadataEntry(
    val key: MetadataKey,
    val value: MetadataValue,
    val flags: Set<MetadataFlag> = emptySet(),
)

/**
 * A field within a [MetadataSet], enriched with source provenance.
 */
data class MetadataField(
    val key: MetadataKey,
    val value: MetadataValue,
    val source: MetadataSource,
    val flags: Set<MetadataFlag> = emptySet(),
)

/**
 * A group of metadata entries from a single source/schema.
 */
data class MetadataSet(
    val source: MetadataSource,
    val entries: List<MetadataEntry> = emptyList(),
)

/**
 * Complete metadata bundle aggregating all metadata sets
 * found in a media file.
 */
data class MetadataBundle(
    val sets: List<MetadataSet> = emptyList(),
    val extensions: List<ModelExtension> = emptyList(),
) {
    /** All fields flattened across all sets, preserving order. */
    val allFields: List<MetadataField>
        get() = sets.flatMap { set ->
            set.entries.map { entry ->
                MetadataField(
                    key = entry.key,
                    value = entry.value,
                    source = set.source,
                    flags = entry.flags,
                )
            }
        }

    /** Find the first value for a given key across all sets. */
    fun findFirst(key: MetadataKey): MetadataValue? =
        sets.firstNotNullOfOrNull { set ->
            set.entries.firstOrNull { it.key == key }?.value
        }
}
