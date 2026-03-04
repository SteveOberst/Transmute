package dev.transmute.libheif

import dev.transmute.filesystem.TPath
import dev.transmute.plugin.HasPluginConfigure
import dev.transmute.plugin.PluginConfigure
import dev.transmute.plugin.PluginFeature
import dev.transmute.plugin.PluginId
import dev.transmute.plugin.TransmutePlugin
import dev.transmute.plugin.TransmuteScope
import dev.transmute.plugins.BuiltinPlugins

/**
 * Plugin configuration for libheif-based HEIF/HEIC/AVIF image codec support.
 *
 * Controls which codec features are enabled and how the libheif CLI tools
 * (`heif-dec`/`heif-convert` and `heif-enc`) are located on the system.
 * By default, the bundled libheif tools are used and **all** features are
 * enabled.
 *
 * ```kotlin
 * val transmute = Transmute {
 *     plugins {
 *         install(LibHeif) {
 *             // All features on by default; selectively disable:
 *             disable(LibHeifFeature.ImageEncoding)
 *
 *             // Optional: use a custom libheif installation
 *             installFrom(TPath.of("/opt/libheif"))
 *
 *             // Optional: use the system PATH instead of the bundled runtime
 *             // useSystemInstallation()
 *
 *             timeout(30_000L)
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
class LibHeifPluginConfig : HasPluginConfigure {
    override val pluginConfigure = PluginConfigure()

    private var _installation: LibHeifInstallation = LibHeifInstallation.Bundled
    private var _timeoutMs: Long = 30_000L

    /** How libheif binaries are located. */
    val installation: LibHeifInstallation get() = _installation
    /** Subprocess timeout in milliseconds. */
    val timeoutMs: Long get() = _timeoutMs

    // -- Feature toggles (delegate to pluginConfigure) -------------------------

    /** Enable a [LibHeifFeature] for this installation. */
    fun enable(feature: PluginFeature) = pluginConfigure.enable(feature)

    /** Disable a [LibHeifFeature] for this installation. */
    fun disable(feature: PluginFeature) = pluginConfigure.disable(feature)

    /** Set a [LibHeifFeature] to the given enabled/disabled state. */
    fun set(feature: PluginFeature, enabled: Boolean) = pluginConfigure.set(feature, enabled)

    /** Set a feature by raw string id (fallback for dynamic/runtime usage). */
    fun set(featureId: String, enabled: Boolean) = pluginConfigure.set(featureId, enabled)

    // -- Installation -----------------------------------------------------------

    /**
     * Use a pre-existing libheif installation at [home].
     *
     * The resolver looks for `<home>/bin/heif-dec` (or `heif-convert`)
     * and `<home>/bin/heif-enc`.
     */
    fun installFrom(home: TPath) {
        _installation = LibHeifInstallation.Custom(home)
    }

    /**
     * Use a pre-existing libheif installation at [home] with
     * additional [searchPaths] for binaries.
     */
    fun installFrom(home: TPath, searchPaths: List<TPath>) {
        _installation = LibHeifInstallation.Custom(home, searchPaths)
    }

    /**
     * Locate libheif via the system PATH and platform defaults.
     *
     * Does not use bundled binaries. If libheif is not installed
     * on the system, HEIF/HEIC/AVIF codecs will be unavailable.
     */
    fun useSystemInstallation() {
        _installation = LibHeifInstallation.System
    }

    /** Set the installation mode directly. */
    fun installation(mode: LibHeifInstallation) {
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
        "LibHeifPluginConfig(installation=$_installation, timeoutMs=$_timeoutMs)"
}

/**
 * [TransmutePlugin] that registers libheif-backed image codecs for
 * HEIF, HEIC, and AVIF into a [Transmute][dev.transmute.transmute]
 * instance's per-instance registries.
 *
 * On **Desktop/JVM**, libheif is invoked via its CLI tools (`heif-dec` /
 * `heif-convert` for decoding and `heif-enc` for encoding) as subprocesses.
 * Both Windows and Linux are supported.
 *
 * On **Android** and **iOS**, this plugin is a no-op because those platforms
 * support HEIF/HEIC/AVIF natively through their platform image I/O APIs
 * (Android's `BitmapFactory`, iOS's `CoreGraphics`/`ImageIO`).
 *
 * All features ([LibHeifFeature.ImageCodecs], [LibHeifFeature.ImageEncoding])
 * are **enabled by default**. Disable individual features via the config DSL:
 *
 * ```kotlin
 * val transmute = Transmute {
 *     plugins {
 *         install(LibHeif) {
 *             disable(LibHeifFeature.ImageEncoding)
 *         }
 *     }
 * }
 * ```
 *
 * If libheif tools are not available on the current system, the plugin is a no-op.
 */
object LibHeif : TransmutePlugin<LibHeifPluginConfig> {

    override val key: PluginId = BuiltinPlugins.LibHeif
    override val displayName: String = "LibHeif"
    override val description: String =
        "libheif-based image codec backend - HEIF/HEIC/AVIF decode/encode via heif-dec/heif-enc (desktop); no-op on Android/iOS."
    override val features: Set<PluginFeature> = LibHeifFeature.ALL

    override fun createConfig(): LibHeifPluginConfig = LibHeifPluginConfig()

    override fun install(scope: TransmuteScope, config: LibHeifPluginConfig) {
        val logger = scope.logger
        val features = scope.features

        // Apply resolver configuration before checking availability
        configureLibHeifResolver(config.installation)

        if (!LibHeifCodecInstaller.available) {
            val diag = libHeifResolverDiagnostics()
            if (diag.isNotBlank()) logger.warn("libheif resolution trace:\n$diag")
            logger.warn("libheif is not available -- skipping HEIF/HEIC/AVIF codec registration")
            return
        }

        // Log resolver diagnostics and resolved installation at INFO for visibility
        val diag = libHeifResolverDiagnostics()
        if (diag.isNotBlank()) logger.info("libheif resolution trace:\n$diag")
        val installInfo = resolvedLibHeifInstallationInfo()
        if (installInfo.isNotBlank()) logger.info("libheif available [$installInfo]")

        if (features.isEnabled(LibHeifFeature.ImageCodecs)) {
            LibHeifCodecInstaller.installImageCodecs(
                scope.codecs.image.decoders,
                scope.codecs.image.encoders,
                features,
            )
            logger.info("Registered libheif image codecs (HEIF, HEIC, AVIF)")
        } else {
            logger.debug("Image codecs feature disabled -- skipping")
        }
    }
}
