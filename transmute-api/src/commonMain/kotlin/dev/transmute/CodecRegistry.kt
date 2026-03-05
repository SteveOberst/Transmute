@file:Suppress("unused")

package dev.transmute

import dev.transmute.audio.AudioFormat
import dev.transmute.audio.MutableAudioDecoderRegistry
import dev.transmute.audio.MutableAudioEncoderRegistry
import dev.transmute.codec.MediaDecoder
import dev.transmute.image.ImageFormat
import dev.transmute.image.MutableImageDecoderRegistry
import dev.transmute.image.MutableImageEncoderRegistry
import dev.transmute.model.core.MediaFormat
import dev.transmute.model.core.MediaMetadata
import dev.transmute.model.core.MediaMetadataRegistry
import dev.transmute.model.core.MediaStructure
import dev.transmute.model.core.MediaStructureRegistry
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.core.RawMediaStructure
import dev.transmute.model.core.TypedRegistrationScope
import dev.transmute.video.MutableVideoDecoderRegistry
import dev.transmute.video.MutableVideoEncoderRegistry
import dev.transmute.video.VideoFormat

// -- Generic structure-decoder registry --------------------------------------

/**
 * Generic format-keyed registry for structure decoders.
 *
 * Used to register [MediaDecoder] implementations that produce structural models
 * ([RawMediaStructure] or [MediaStructure]) rather than pixel/sample IR.
 *
 * ```kotlin
 * scope.codecs.image.rawStructureDecoders.register(ImageFormat.Png, PngRawDecoder())
 * scope.codecs.image.structureDecoders.register(ImageFormat.Png, PngStructureDecoder())
 * ```
 */
class MutableDecoderRegistry<F : MediaFormat<*, *>, OUT> {
  private val byFormat = mutableMapOf<MediaFormat<*, *>, MediaDecoder<F, out OUT, NoDecodeOptions>>()

  /** Register [decoder] for the given [format]. A later registration overrides an earlier one. */
  fun register(format: F, decoder: MediaDecoder<F, out OUT, NoDecodeOptions>) {
    byFormat[format] = decoder
  }

  /** Return the decoder registered for [format], or `null` if none is registered. */
  operator fun get(format: MediaFormat<*, *>): MediaDecoder<F, out OUT, NoDecodeOptions>? = byFormat[format]

  /** All formats for which a decoder is registered. */
  val supportedFormats: Set<MediaFormat<*, *>> get() = byFormat.keys.toSet()
}

// -- Domain codec registries --------------------------------------------------

/**
 * Holds all mutable codec registries for image formats.
 *
 * Plugins receive this via [CodecRegistry.image] and can register
 * decoders, encoders, and structure decoders for image formats.
 */
class ImageCodecRegistry(
  /** IR (pixel-data) decoders for image formats. */
  val decoders: MutableImageDecoderRegistry = MutableImageDecoderRegistry(),
  /** IR (pixel-data) encoders for image formats. */
  val encoders: MutableImageEncoderRegistry = MutableImageEncoderRegistry(),
  /** Raw on-disk structure decoders - bytes -> [RawMediaStructure]. */
  val rawStructureDecoders: MutableDecoderRegistry<ImageFormat, RawMediaStructure> = MutableDecoderRegistry(),
  /** Developer-friendly structure decoders - bytes -> [MediaStructure]. */
  val structureDecoders: MutableDecoderRegistry<ImageFormat, MediaStructure> = MutableDecoderRegistry(),
  /** Metadata decoders - bytes -> list of [MediaMetadata]. */
  val metadataDecoders: MutableDecoderRegistry<ImageFormat, List<MediaMetadata>> = MutableDecoderRegistry(),
)

/**
 * Holds all mutable codec registries for audio formats.
 */
class AudioCodecRegistry(
  val decoders: MutableAudioDecoderRegistry = MutableAudioDecoderRegistry(),
  val encoders: MutableAudioEncoderRegistry = MutableAudioEncoderRegistry(),
  val rawStructureDecoders: MutableDecoderRegistry<AudioFormat, RawMediaStructure> = MutableDecoderRegistry(),
  val structureDecoders: MutableDecoderRegistry<AudioFormat, MediaStructure> = MutableDecoderRegistry(),
  /** Metadata decoders - bytes -> list of [MediaMetadata]. */
  val metadataDecoders: MutableDecoderRegistry<AudioFormat, List<MediaMetadata>> = MutableDecoderRegistry(),
)

/**
 * Holds all mutable codec registries for video formats.
 */
class VideoCodecRegistry(
  val decoders: MutableVideoDecoderRegistry = MutableVideoDecoderRegistry(),
  val encoders: MutableVideoEncoderRegistry = MutableVideoEncoderRegistry(),
  val rawStructureDecoders: MutableDecoderRegistry<VideoFormat, RawMediaStructure> = MutableDecoderRegistry(),
  val structureDecoders: MutableDecoderRegistry<VideoFormat, MediaStructure> = MutableDecoderRegistry(),
  /** Metadata decoders - bytes -> list of [MediaMetadata]. */
  val metadataDecoders: MutableDecoderRegistry<VideoFormat, List<MediaMetadata>> = MutableDecoderRegistry(),
)

/**
 * Top-level registry grouping all domain codec registries.
 *
 * Exposed on [dev.transmute.plugin.TransmuteScope] as [TransmuteScope.codecs].
 * Replaces the six separate flat decoder/encoder fields that existed previously.
 *
 * ```kotlin
 * // Plugin registration example:
 * class MyPlugin : TransmutePlugin<MyConfig> {
 *     override fun install(scope: TransmuteScope, config: MyConfig) {
 *         scope.codecs.image.decoders.register(MyImageDecoder())
 *         scope.codecs.image.rawStructureDecoders.register(ImageFormat.MyFormat, MyRawDecoder())
 *         scope.codecs.image.structureDecoders.register(ImageFormat.MyFormat, MyStructureDecoder())
 *         scope.mediaStructures.register("myplugin.myformat", MyFormatStructure.serializer())
 *     }
 * }
 * ```
 */
class CodecRegistry(
  val image: ImageCodecRegistry = ImageCodecRegistry(),
  val audio: AudioCodecRegistry = AudioCodecRegistry(),
  val video: VideoCodecRegistry = VideoCodecRegistry(),
)

// -- Registration scopes (for plugin API) -------------------------------------

/**
 * Plugin-facing scope for registering [MediaStructure] serialisation types.
 *
 * Delegates to [MediaStructureRegistry] via [TypedRegistrationScope].
 */
class MediaStructureRegistrationScope : TypedRegistrationScope<MediaStructure>(MediaStructureRegistry)

/**
 * Plugin-facing scope for registering [MediaMetadata] serialisation types.
 *
 * Delegates to [MediaMetadataRegistry] via [TypedRegistrationScope].
 */
class MediaMetadataRegistrationScope : TypedRegistrationScope<MediaMetadata>(MediaMetadataRegistry)
