package dev.transmute.model.core

/**
 * Dynamic registry that maps string typeIds to [kotlinx.serialization.KSerializer]
 * instances for [MediaStructure] implementations.
 *
 * This powers [MediaStructureSerializer]'s polymorphic dispatch. The wire
 * format uses a `type` field (the registered typeId) to select the correct
 * concrete serializer at runtime, without relying on `SerializersModule`.
 *
 * See [TypedRegistry] for the generic machinery shared with [MediaMetadataRegistry].
 *
 * ```kotlin
 * MediaStructureRegistry.register<PngStructure>("transmute.png", PngStructure.serializer())
 * ```
 */
object MediaStructureRegistry : TypedRegistry<MediaStructure>("MediaStructureRegistry")
