@file:Suppress("unused")

package dev.transmute.model.structure.common

import kotlinx.serialization.Serializable

/**
 * JSON-safe, recursive RIFF chunk tree.
 *
 * Preserves container hierarchy (`RIFF` / `LIST`) while intentionally omitting
 * raw payload bytes (e.g. `data` in WAV or `movi` in AVI).
 */
@Serializable
data class RiffChunkTree(
  /** 4-character chunk ID (e.g. `RIFF`, `fmt `, `data`, `LIST`). */
  val id: String,
  /** RIFF/LIST form type (e.g. `WAVE`, `AVI `, `hdrl`) for container chunks. */
  val formType: String? = null,
  /** The RIFF `size` field (payload size, excludes the 8-byte chunk header). */
  val sizeBytes: Long,
  /** Leaf payload size (0 for containers). */
  val dataSizeBytes: Long,
  /** Nested child chunks (empty for leaves). */
  val children: List<RiffChunkTree> = emptyList(),
)

/** Convert a parsed [RiffChunk] into a JSON-safe tree representation. */
fun RiffChunk.toTree(): RiffChunkTree = RiffChunkTree(
  id = id.value,
  formType = formType?.value,
  sizeBytes = size.toLong(),
  dataSizeBytes = data.size.toLong(),
  children = children.map { it.toTree() },
)
