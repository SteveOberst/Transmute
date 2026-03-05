package dev.transmute.playground.shared

import kotlinx.serialization.Serializable

/**
 * Transform pipeline request.
 *
 * Sent as body of `POST /api/transform`.
 */
@Serializable
data class TransformRequest(
  val fileHandle: String,
  val outputFormat: String,
  val pipeline: List<TransformStep> = emptyList(),
  val encodeOptions: Map<String, String> = emptyMap(),
  val metadataPolicy: String = "PRESERVE",
)

@Serializable
data class TransformStep(val transformId: String, val parameters: Map<String, String?> = emptyMap())

/**
 * Transform result metadata.
 *
 * Returned by `POST /api/transform`.
 */
@Serializable
data class TransformResult(
  val resultHandle: String,
  val outputFormat: String,
  val fileSize: Long,
  val properties: Map<String, String> = emptyMap(),
  val generatedCode: String = "",
  val durationMs: Long = 0,
)
