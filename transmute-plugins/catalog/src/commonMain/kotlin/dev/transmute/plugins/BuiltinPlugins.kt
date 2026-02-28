package dev.transmute.plugins

import dev.transmute.plugin.PluginId
import dev.transmute.plugin.TransmutePlugin

/**
 * Central catalog of all Transmute first-party (built-in) plugins.
 *
 * Import `transmute-plugins:catalog` as a single dependency to access every
 * official plugin without hunting individual module coordinates:
 *
 * ```kotlin
 * val transmute = Transmute {
 *     plugins {
 *         // GStreamer-backed audio, video, and image codecs
 *         install(BuiltinPlugins.GStreamer) {
 *             disable(GStreamerFeature.LegacyAvi)
 *         }
 *     }
 * }
 * ```
 *
 * The catalog itself does not configure plugins — it merely provides
 * a named reference to each [TransmutePlugin] object. All configuration
 * is done via the install DSL as usual.
 */
object BuiltinPlugins {

    /**
     * GStreamer-backed codec plugin.
     *
     * Provides audio (AAC, M4A, Opus, FLAC, OGG), video (MP4, MOV, WebM,
     * AVI, MKV), and image (HEIF, HEIC, AVIF) support via GStreamer.
     *
     * All features are enabled by default. Selectively disable via features:
     * ```kotlin
     * install(BuiltinPlugins.GStreamer) {
     *     disable(GStreamerFeature.LegacyAvi)
     *     disable(GStreamerFeature.ImageEncoding)
     * }
     * ```
     */
    val GStreamer = PluginId("dev.transmute.gstreamer")

    /**
     * All first-party Transmute plugins, in recommended install order.
     *
     * Useful for introspection, building plugin management UIs, or
     * iterating all available plugins at runtime.
     *
     * Note: due to generic type erasure, configuring plugins via this list
     * requires casting. Prefer referencing individual plugin objects
     * (e.g. [GStreamer]) in install blocks.
     */
    val all: List<PluginId> = listOf(
        GStreamer,
    )
}
