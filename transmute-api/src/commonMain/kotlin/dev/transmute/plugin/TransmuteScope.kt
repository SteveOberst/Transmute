package dev.transmute.plugin

import dev.transmute.audio.MutableAudioDecoderRegistry
import dev.transmute.audio.MutableAudioEncoderRegistry
import dev.transmute.image.MutableImageDecoderRegistry
import dev.transmute.image.MutableImageEncoderRegistry
import dev.transmute.video.MutableVideoDecoderRegistry
import dev.transmute.video.MutableVideoEncoderRegistry

/**
 * Provides [TransmutePlugin] implementations with access to the mutable
 * registries they can populate during [TransmutePlugin.install].
 *
 * Plugins register their decoders and encoders on the exposed registries.
 * The [services] registry allows plugins to share typed services with each
 * other, and [diagnostics] provides a structured way to report health info.
 *
 * Every plugin receives a [logger] scoped to its key, which can be configured
 * via the `configure { logging { } }` DSL block.
 */
class TransmuteScope(
  val imageDecoders: MutableImageDecoderRegistry,
  val imageEncoders: MutableImageEncoderRegistry,
  val audioDecoders: MutableAudioDecoderRegistry,
  val audioEncoders: MutableAudioEncoderRegistry,
  val videoDecoders: MutableVideoDecoderRegistry,
  val videoEncoders: MutableVideoEncoderRegistry,
  /** Type-safe service registry for cross-plugin collaboration. */
  val services: ServiceRegistry,
  /** Structured diagnostics channel for this plugin. */
  val diagnostics: PluginDiagnostics = PluginDiagnostics(PluginId("unknown")),
  /** Per-plugin logger, automatically tagged with the plugin's key. */
  val logger: PluginLogger = PluginLogger(PluginId("unknown")),
  /** Feature toggles set by the user via `configure { features { } }`. */
  val features: PluginFeaturesConfig = PluginFeaturesConfig(),
)
