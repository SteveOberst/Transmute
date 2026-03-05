package dev.transmute.playground.shared

import kotlinx.serialization.Serializable

/**
 * Describes a transform operation for the UI catalog.
 *
 * Returned by `GET /api/transforms`.
 */
@Serializable
data class TransformInfo(
  val id: String,
  val domain: MediaDomainDto,
  val description: String,
  val parameters: List<ParameterSchema> = emptyList(),
)

/**
 * Schema for a single parameter - drives dynamic UI rendering.
 */
@Serializable
data class ParameterSchema(
  val name: String,
  val type: ParameterType,
  val required: Boolean = false,
  val default: String? = null,
  val min: String? = null,
  val max: String? = null,
  val enumValues: List<String>? = null,
  val description: String = "",
)
