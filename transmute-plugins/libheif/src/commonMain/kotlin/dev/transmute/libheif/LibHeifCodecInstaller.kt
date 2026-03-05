package dev.transmute.libheif

import dev.transmute.image.MutableImageDecoderRegistry
import dev.transmute.image.MutableImageEncoderRegistry
import dev.transmute.plugin.PluginFeaturesConfig

/**
 * Installs libheif-backed image codecs into the provided registries.
 *
 * On Desktop/JVM, libheif is invoked via subprocess (`heif-dec`/`heif-enc`).
 * On Android and iOS this is a no-op -- those platforms support HEIF/HEIC/AVIF
 * natively through their respective platform image I/O APIs.
 */
object LibHeifCodecInstaller {

  /** `true` when a usable libheif installation has been detected on this platform. */
  val available: Boolean get() = isLibHeifAvailable()

  /**
   * Register libheif image codecs: HEIF, HEIC, AVIF decode and optionally encode.
   *
   * When [LibHeifFeature.ImageEncoding] is disabled in [features],
   * only the decoder is registered -- the HEIF/AVIF encoder is skipped.
   */
  fun installImageCodecs(
    decoders: MutableImageDecoderRegistry,
    encoders: MutableImageEncoderRegistry,
    features: PluginFeaturesConfig = PluginFeaturesConfig(),
  ) = installLibHeifImageCodecs(decoders, encoders, features)
}

/**
 * Platform-specific: returns `true` when libheif tools are available.
 *
 * On Desktop/JVM this checks for `heif-dec`/`heif-convert` and `heif-enc`
 * binaries. On Android/iOS this always returns `false` (native platform
 * codecs are used instead).
 */
internal expect fun isLibHeifAvailable(): Boolean

/**
 * Platform-specific: registers libheif image codecs.
 *
 * On Desktop/JVM this registers [LibHeifImageDecoder] and optionally
 * [LibHeifImageEncoder]. On Android/iOS this is a no-op.
 */
internal expect fun installLibHeifImageCodecs(
  decoders: MutableImageDecoderRegistry,
  encoders: MutableImageEncoderRegistry,
  features: PluginFeaturesConfig,
)

/**
 * Apply libheif configuration to the platform-specific resolver.
 *
 * On Desktop/JVM this configures [LibHeifResolver] with the installation
 * mode (bundled, custom, or system). On Android/iOS this is a no-op.
 */
internal expect fun configureLibHeifResolver(installation: LibHeifInstallation)

/**
 * Returns diagnostic information from the libheif resolver.
 *
 * On Desktop/JVM this returns detail about the resolution attempt
 * (paths searched, what was found/not found). On Android/iOS this
 * returns an empty string.
 */
internal expect fun libHeifResolverDiagnostics(): String

/**
 * Returns a short human-readable description of the resolved libheif
 * installation, suitable for an INFO-level log line.
 *
 * Example: `"system -> /usr/bin/heif-dec"`
 *
 * Returns an empty string on platforms where libheif is not available
 * or not applicable (Android / iOS).
 */
internal expect fun resolvedLibHeifInstallationInfo(): String
