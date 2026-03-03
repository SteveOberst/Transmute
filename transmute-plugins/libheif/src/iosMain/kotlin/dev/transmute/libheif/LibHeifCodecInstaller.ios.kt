package dev.transmute.libheif

import dev.transmute.image.MutableImageDecoderRegistry
import dev.transmute.image.MutableImageEncoderRegistry
import dev.transmute.plugin.PluginFeaturesConfig

/**
 * iOS no-op implementation.
 *
 * On iOS, HEIF/HEIC/AVIF are supported natively via `CoreGraphics`
 * / `ImageIO` and the platform `IosImageIoDecoder`. libheif is not needed.
 */

internal actual fun isLibHeifAvailable(): Boolean = false

internal actual fun installLibHeifImageCodecs(
    decoders: MutableImageDecoderRegistry,
    encoders: MutableImageEncoderRegistry,
    features: PluginFeaturesConfig,
) {
    // No-op: iOS handles HEIF/HEIC/AVIF natively
}

internal actual fun configureLibHeifResolver(
    installation: LibHeifInstallation,
) {
    // No-op on iOS
}

internal actual fun libHeifResolverDiagnostics(): String = ""

internal actual fun resolvedLibHeifInstallationInfo(): String = ""
