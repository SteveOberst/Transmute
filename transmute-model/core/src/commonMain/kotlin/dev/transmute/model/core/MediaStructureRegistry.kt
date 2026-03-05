package dev.transmute.model.core

/**
 * Dynamic registry that maps string typeIds to [kotlinx.serialization.KSerializer]
 * instances for [MediaStructure] implementations.
 *
 * This powers [MediaStructureSerializer]'s polymorphic dispatch. The wire
 * format uses a `type` field (the registered typeId) to select the correct
 * concrete serializer at runtime, without relying on `SerializersModule`.
 *
<<<<<<< Updated upstream
 * ## typeId conventions
 * - Built-in types: `"transmute.png"`, `"transmute.jpeg"`, `"transmute.wav"`, ...
 * - Plugin types: `"<plugin-id>.<format>"`, e.g. `"myplugin.customformat"`
 * - **Never** use class names: they change with refactors.
=======
 * See [TypedRegistry] for the generic machinery shared with [MediaMetadataRegistry].
>>>>>>> Stashed changes
 *
 * ```kotlin
 * MediaStructureRegistry.register<PngStructure>("transmute.png", PngStructure.serializer())
 * ```
 */
object MediaStructureRegistry : TypedRegistry<MediaStructure>("MediaStructureRegistry")
