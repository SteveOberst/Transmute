package dev.transmute.model.core

/**
 * Dynamic registry that maps string typeIds to serializers for
 * [MediaMetadata] implementations.
 *
 * Parallel to [MediaStructureRegistry] - see [TypedRegistry] for the
 * shared generic machinery.
 *
 * ```kotlin
 * MediaMetadataRegistry.register<ExifMetadata>("transmute.exif", ExifMetadata.serializer())
 * ```
 */
object MediaMetadataRegistry : TypedRegistry<MediaMetadata>("MediaMetadataRegistry")
