package dev.transmute.playground.shared

import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.core.MediaStructure
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
  val structure: MediaStructure? = null,
  val metadata: List<MediaMetadata> = emptyList(),
)
