package dev.transmute.gstreamer

import dev.transmute.filesystem.TPath
import dev.transmute.plugin.HasPluginConfigure
import dev.transmute.plugin.PluginConfigure
import dev.transmute.plugin.PluginFeature
import dev.transmute.plugin.PluginId
import dev.transmute.plugin.TransmutePlugin
import dev.transmute.plugin.TransmuteScope
import dev.transmute.plugins.BuiltinPlugins

/**
 * Plugin configuration for the GStreamer codec integration.
 *
 * Controls which codec sets are enabled and how GStreamer is located on the system.
 * By default, the bundled GStreamer runtime is used and **all** codec features are
 * enabled. Selectively disable features using [enable]/[disable].
 *
 * ```kotlin
 * val transmute = Transmute {
 *     plugins {
 *         install(GStreamer) {
 *             // All features on by default; selectively disable:
 *             disable(GStreamerFeature.LegacyAvi)
 *             disable(GStreamerFeature.ImageEncoding)
 *
 *             // Optional: use a custom GStreamer installation
 *             installFrom(TPath.of("C:\\gstreamer\\1.0\\msvc_x86_64"))
 *
 *             // Optional: use the system PATH instead of the bundled runtime
 *             // useSystemInstallation()
 *
 *             timeout(60_000L)
 *
 *             // Per-plugin logging configuration
 *             configure {
 *                 logging {
 *                     level(LogLevel.DEBUG)
 *                 }
 *             }
 *         }
 *     }
 * }
 * ```
 */
class GStreamerPluginConfig : HasPluginConfigure {
    override val pluginConfigure = PluginConfigure()

    private var _installation: GStreamerInstallation = GStreamerInstallation.Bundled
    private var _timeoutMs: Long = 30_000L

    /** How GStreamer binaries are located. */
    val installation: GStreamerInstallation get() = _installation
    /** Subprocess timeout in milliseconds. */
    val timeoutMs: Long get() = _timeoutMs

    // -- Feature toggles (delegate to pluginConfigure) -------------------------

    /** Enable a [GStreamerFeature] for this installation. */
    fun enable(feature: PluginFeature) = pluginConfigure.enable(feature)

    /** Disable a [GStreamerFeature] for this installation. */
    fun disable(feature: PluginFeature) = pluginConfigure.disable(feature)

    /** Set a [GStreamerFeature] to the given enabled/disabled state. */
    fun set(feature: PluginFeature, enabled: Boolean) = pluginConfigure.set(feature, enabled)

    /** Set a feature by raw string id (fallback for dynamic/runtime usage). */
    fun set(featureId: String, enabled: Boolean) = pluginConfigure.set(featureId, enabled)

    // -- Installation -----------------------------------------------------------

    /**
     * Use a pre-existing GStreamer installation at [home].
     *
     * The resolver looks for `<home>/bin/gst-launch-1.0`.
     */
    fun installFrom(home: TPath) {
        _installation = GStreamerInstallation.Custom(home)
    }

    /**
     * Use a pre-existing GStreamer installation at [home] with
     * additional [searchPaths] for binaries.
     */
    fun installFrom(home: TPath, searchPaths: List<TPath>) {
        _installation = GStreamerInstallation.Custom(home, searchPaths)
    }

    /**
     * Locate GStreamer via the system PATH and platform defaults.
     *
     * Does not use bundled binaries. If GStreamer is not installed
     * on the system, codecs that require it will be unavailable.
     */
    fun useSystemInstallation() {
        _installation = GStreamerInstallation.System
    }

    /** Set the installation mode directly. */
    fun installation(mode: GStreamerInstallation) {
        _installation = mode
    }

    /** Set the subprocess timeout in milliseconds. Use `0` to disable. */
    fun timeout(ms: Long) { _timeoutMs = ms }

    /**
     * Configure cross-cutting plugin concerns (logging, etc.).
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
    fun configure(block: PluginConfigure.() -> Unit) {
        pluginConfigure.apply(block)
    }

    override fun toString(): String =
        "GStreamerPluginConfig(installation=$_installation, timeoutMs=$_timeoutMs)"
}

/**
 * [TransmutePlugin] that registers GStreamer-backed codecs into a
 * [Transmute][dev.transmute.transmute] instance’s per-instance registries.
 *
 * All features ([GStreamerFeature.AudioCodecs], [GStreamerFeature.VideoCodecs],
 * [GStreamerFeature.ImageCodecs], etc.) are **enabled by default**. Disable
 * individual features via the config DSL:
 *
 * ```kotlin
 * val transmute = Transmute {
 *     plugins {
 *         install(GStreamer) {
 *             disable(GStreamerFeature.LegacyAvi)
 *             disable(GStreamerFeature.ImageEncoding)
 *         }
 *     }
 * }
 * ```
 *
 * If GStreamer is not available on the current system, the plugin is a no-op.
 */
object GStreamer : TransmutePlugin<GStreamerPluginConfig> {

  override val key: PluginId = BuiltinPlugins.GStreamer
  override val features: Set<PluginFeature> = GStreamerFeature.ALL

  override fun createConfig(): GStreamerPluginConfig = GStreamerPluginConfig()

  override fun install(scope: TransmuteScope, config: GStreamerPluginConfig) {
    val logger = scope.logger
    val features = scope.features

    // Apply resolver configuration before checking availability
    configureResolver(config.installation)

    if (!GStreamerCodecInstaller.available) {
      val diag = resolverDiagnostics()
      if (diag.isNotBlank()) logger.info("GStreamer resolution trace:\n$diag")
      logger.warn("GStreamer is not available — skipping codec registration")
      return
    }

    // Log resolver diagnostics and resolved installation at INFO for visibility
    val diag = resolverDiagnostics()
    if (diag.isNotBlank()) logger.info("GStreamer resolution trace:\n$diag")
    val installInfo = resolvedInstallationInfo()
    if (installInfo.isNotBlank()) logger.info("GStreamer available [$installInfo]")

    if (features.isEnabled(GStreamerFeature.AudioCodecs)) {
      GStreamerCodecInstaller.installAudioCodecs(scope.audioDecoders, scope.audioEncoders)
      logger.info("Registered GStreamer audio codecs")
    } else {
      logger.debug("Audio codecs feature disabled — skipping")
    }

    if (features.isEnabled(GStreamerFeature.ImageCodecs)) {
      GStreamerCodecInstaller.installImageCodecs(
        scope.imageDecoders,
        scope.imageEncoders,
        features,
      )
      logger.info("Registered GStreamer image codecs")
    } else {
      logger.debug("Image codecs feature disabled — skipping")
    }

    if (features.isEnabled(GStreamerFeature.VideoCodecs)) {
      GStreamerCodecInstaller.installVideoCodecs(
        scope.videoDecoders,
        scope.videoEncoders,
        features,
      )
      logger.info("Registered GStreamer video codecs")
    } else {
      logger.debug("Video codecs feature disabled — skipping")
    }
  }
}