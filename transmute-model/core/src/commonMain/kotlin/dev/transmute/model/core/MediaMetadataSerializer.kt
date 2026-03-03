package dev.transmute.model.core

/**
 * Polymorphic serializer for [MediaMetadata] using a
 * `{ "type": "...", "value": { ... } }` JSON envelope.
 *
 * Parallel to [MediaStructureSerializer] - see [TypedEnvelopeSerializer]
 * for the shared generic machinery.
 */
object MediaMetadataSerializer : TypedEnvelopeSerializer<MediaMetadata>(
    "MediaMetadata",
    MediaMetadataRegistry,
)
