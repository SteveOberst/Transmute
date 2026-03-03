package dev.transmute

import dev.transmute.common.MediaDomain
import dev.transmute.model.core.MediaFormat
import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.core.MediaStructure
import dev.transmute.model.core.RawMediaStructure

/** Options controlling what [TransmuteInspect.inspect] includes in its result. */
data class InspectOptions(
  /** Include a parsed [MediaStructure] when a decoder is available. */
  val includeStructure: Boolean = true,

  /** Include a [RawMediaStructure] when a decoder is available. */
  val includeRawStructure: Boolean = false,

  /** Include decoded [MediaMetadata] blocks when decoders are available. */
  val includeMetadata: Boolean = true,
)

/**
 * High-level inspection result for a media file.
 *
 * This is a convenience wrapper around the lower-level codec APIs:
 * - format detection ([TransmuteInspect.detectFormat])
 * - structure decoding ([TransmuteCodec.decodeStructure]/[TransmuteCodec.decodeRawStructure])
 * - metadata decoding ([TransmuteCodec.decodeMetadata])
 */
data class MediaInspection(
  val domain: MediaDomain,
  val format: MediaFormat<*, *>,
  val sizeBytes: Long,
  val structure: MediaStructure? = null,
  val rawStructure: RawMediaStructure? = null,
  val metadata: List<MediaMetadata> = emptyList(),
)
