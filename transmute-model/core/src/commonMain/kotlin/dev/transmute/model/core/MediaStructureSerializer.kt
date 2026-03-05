package dev.transmute.model.core

/**
 * Polymorphic serializer for [MediaStructure] using a `{ "type": "...", "value": { ... } }`
 * JSON envelope.
 *
 * See [TypedEnvelopeSerializer] for the generic machinery shared with
 * [MediaMetadataSerializer].
 *
 * Concrete types must be registered in [MediaStructureRegistry] before this
 * serializer is used. Built-in types are registered automatically when a
 * [dev.transmute.Transmute] instance is built.
 */
object MediaStructureSerializer : TypedEnvelopeSerializer<MediaStructure>(
  "MediaStructure",
  MediaStructureRegistry,
)
