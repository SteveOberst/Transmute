package dev.transmute.libheif

import dev.transmute.image.MutableImageDecoderRegistry
import dev.transmute.image.MutableImageEncoderRegistry
import dev.transmute.plugin.PluginFeaturesConfig

/**
 * Android no-op implementation.
 *
 * On Android, HEIF/HEIC/AVIF are supported natively via `BitmapFactory`
 * and the platform `AndroidBitmapImageDecoder`. libheif is not needed.
 */

internal actual fun isLibHeifAvailable(): Boolean = false

internal actual fun installLibHeifImageCodecs(
    decoders: MutableImageDecoderRegistry,
    encoders: MutableImageEncoderRegistry,
    features: PluginFeaturesConfig,
) {
    // No-op: Android handles HEIF/HEIC/AVIF natively
}

internal actual fun configureLibHeifResolver(
    installation: LibHeifInstallation,
) {
    // No-op on Android
}

internal actual fun libHeifResolverDiagnostics(): String = ""

internal actual fun resolvedLibHeifInstallationInfo(): String = ""
