package dev.transmute.plugin

import dev.transmute.common.LogLevel
import dev.transmute.common.TransmuteLogger

/**
 * Base configuration that every plugin receives automatically.
 *
 * Provides a `configure { }` DSL block for cross-cutting concerns like
 * logging. Plugin-specific configuration is layered on top via the
 * plugin's own config type `C`.
 *
 * ```kotlin
 * install(GStreamer) {
 *     // Plugin-specific settings
 *     domains(MediaDomain.AUDIO or MediaDomain.VIDEO)
 *
 *     // Framework-level settings (available to all plugins)
 *     configure {
 *         logging {
 *             level(LogLevel.DEBUG)
 *             backend(myLogger)
 *         }
 *     }
 * }
 * ```
 */
class PluginConfigure {
    internal val loggerConfig = PluginLoggerConfig()
    internal val featuresConfig = PluginFeaturesConfig()

    /**
     * Configure the plugin's logger.
     *
     * ```kotlin
     * configure {
     *     logging {
     *         level(LogLevel.DEBUG)
     *         backend(PrintLogger)
     *     }
     * }
     * ```
     */
    fun logging(block: PluginLoggerConfig.() -> Unit) {
        loggerConfig.apply(block)
    }

    /**
     * Toggle named features on this plugin.
     *
     * ```kotlin
     * configure {
     *     features {
     *         enable(GStreamerFeature.AudioCodecs)
     *         disable(GStreamerFeature.LegacyAvi)
     *     }
     * }
     * ```
     */
    fun features(block: PluginFeaturesConfig.() -> Unit) {
        featuresConfig.apply(block)
    }

    // -- Shorthand feature toggles (skip nested features { } block) -----------

    /** Enable a [PluginFeature] directly without a nested `features { }` block. */
    fun enable(feature: PluginFeature) { featuresConfig.enable(feature) }

    /** Disable a [PluginFeature] directly without a nested `features { }` block. */
    fun disable(feature: PluginFeature) { featuresConfig.disable(feature) }

    /** Set a [PluginFeature] to the given state directly. */
    fun set(feature: PluginFeature, enabled: Boolean) { featuresConfig.set(feature, enabled) }

    /** Set a feature by raw string id directly. */
    fun set(featureId: String, enabled: Boolean) { featuresConfig.set(featureId, enabled) }
}
