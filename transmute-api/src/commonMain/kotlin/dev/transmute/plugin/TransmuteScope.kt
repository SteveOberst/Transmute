package dev.transmute.plugin

import dev.transmute.CodecRegistry
import dev.transmute.MediaMetadataRegistrationScope
import dev.transmute.MediaStructureRegistrationScope

/**
 * Provides [TransmutePlugin] implementations with access to the mutable
 * registries they can populate during [TransmutePlugin.install].
 *
 * Plugins register their decoders, encoders, and structure decoders via
 * [codecs]. The [services] registry allows plugins to share typed services
 * with each other, and [diagnostics] provides a structured way to report
 * health info.
 *
 * ```kotlin
 * class MyPlugin : TransmutePlugin<MyConfig> {
 *     override fun install(scope: TransmuteScope, config: MyConfig) {
 *         scope.codecs.image.encoders.register(HwAccelEncoder())
 *         scope.codecs.image.rawStructureDecoders.register(ImageFormat.MyFormat, MyRawDecoder())
 *         scope.mediaStructures.register("myplugin.myformat", MyFormatStructure.serializer())
 *         scope.mediaMetadata.register("myplugin.mymetadata", MyMetadata.serializer())
 *     }
 * }
 * ```
 */
class TransmuteScope(
    /** All codec registries (IR decoders/encoders + structure decoders + metadata decoders) grouped by domain. */
    val codecs: CodecRegistry,
    /** Type-safe service registry for cross-plugin collaboration. */
    val services: ServiceRegistry,
    /** Structured diagnostics channel for this plugin. */
    val diagnostics: PluginDiagnostics = PluginDiagnostics(PluginId("unknown")),
    /** Per-plugin logger, automatically tagged with the plugin's key. */
    val logger: PluginLogger = PluginLogger(PluginId("unknown")),
    /** Feature toggles set by the user via `configure { features { } }`. */
    val features: PluginFeaturesConfig = PluginFeaturesConfig(),
    /** Scoped access to [dev.transmute.model.core.MediaStructureRegistry] for JSON-structure type registration. */
    val mediaStructures: MediaStructureRegistrationScope = MediaStructureRegistrationScope(),
    /** Scoped access to [dev.transmute.model.core.MediaMetadataRegistry] for JSON-metadata type registration. */
    val mediaMetadata: MediaMetadataRegistrationScope = MediaMetadataRegistrationScope(),
)
