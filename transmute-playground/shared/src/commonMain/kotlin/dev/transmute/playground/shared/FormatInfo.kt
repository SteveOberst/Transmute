package dev.transmute.playground.shared

import kotlinx.serialization.Serializable

/**
 * Describes a supported media format for the UI catalog.
 *
 * Returned by `GET /api/formats`.
 */
@Serializable
data class FormatInfo(
    val name: String,
    val domain: MediaDomainDto,
    val canDecode: Boolean,
    val canEncode: Boolean,
    val hasStructureReader: Boolean = false,
    /** `null` = platform-native, otherwise the plugin key that provides it. */
    val providedBy: String? = null,
    /** Dynamic encode option descriptors for the UI. */
    val encodeOptions: List<OptionSchema> = emptyList(),
)
