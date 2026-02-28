package dev.transmute.playground.shared

import kotlinx.serialization.Serializable

/**
 * Describes a plugin for the Plugin Manager UI.
 *
 * Returned by `GET /api/plugins` and `PUT /api/plugins/{key}`.
 */
@Serializable
data class PluginDescriptor(
    val key: String,
    val name: String,
    val description: String = "",
    val version: String? = null,
    val enabled: Boolean = true,
    val status: PluginStatusInfo? = null,
    val domains: Set<MediaDomainDto> = emptySet(),
    val features: List<FeatureDescriptor> = emptyList(),
    val options: List<OptionSchema> = emptyList(),
    val addedFormats: List<String> = emptyList(),
)

@Serializable
data class PluginStatusInfo(
    val available: Boolean,
    val reason: String? = null,
    val details: Map<String, String> = emptyMap(),
)

@Serializable
data class FeatureDescriptor(
    val id: String,
    val name: String,
    val description: String = "",
    val defaultEnabled: Boolean = true,
    val currentlyEnabled: Boolean = true,
)

/**
 * Describes a configurable option for a plugin or an encode format.
 */
@Serializable
data class OptionSchema(
    val id: String,
    val name: String,
    val type: ParameterType,
    val default: String? = null,
    val enumValues: List<String>? = null,
    val description: String = "",
)

/**
 * Request body for `PUT /api/plugins/{key}`.
 */
@Serializable
data class PluginUpdate(
    val enabled: Boolean? = null,
    val features: Map<String, Boolean>? = null,
)
