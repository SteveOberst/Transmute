package dev.transmute.model.core

import kotlinx.serialization.Serializable

/**
 * JSON-safe, typed representation of a metadata block found inside a media file.
 *
 * Each concrete implementation models the on-disk hierarchy of a specific
 * metadata standard (EXIF, XMP, ID3v2, ICC, Vorbis Comments, ...) as typed
 * Kotlin data classes - the same approach used by [MediaStructure] for
 * container layout.
 *
 * Polymorphic serialization is handled by [MediaMetadataSerializer] using the
 * [MediaMetadataRegistry] dynamic dispatch table. The JSON wire format uses a
 * `type` + `value` envelope:
 * ```json
 * { "type": "transmute.exif", "value": { "byteOrder": "BIG_ENDIAN", ... } }
 * ```
 *
 * Every concrete implementation must:
 * 1. Be annotated with `@Serializable`.
 * 2. Be registered via [MediaMetadataRegistry.register] at startup.
 * 3. Never include a [Bytes] field with unbounded content - use size summaries.
 */
@Serializable(with = MediaMetadataSerializer::class)
interface MediaMetadata
