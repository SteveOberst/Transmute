package dev.transmute.libheif

import dev.transmute.image.MutableImageDecoderRegistry
import dev.transmute.image.MutableImageEncoderRegistry
import dev.transmute.plugin.PluginFeaturesConfig

internal actual fun isLibHeifAvailable(): Boolean = LibHeifResolver.available

internal actual fun installLibHeifImageCodecs(
    decoders: MutableImageDecoderRegistry,
    encoders: MutableImageEncoderRegistry,
    features: PluginFeaturesConfig,
) {
    if (!LibHeifResolver.available) return

    decoders.register(LibHeifImageDecoder())

    // Only register encoder if the ImageEncoding feature is enabled
    // AND heif-enc is actually available
    if (features.isEnabled(LibHeifFeature.ImageEncoding) && LibHeifResolver.encoderAvailable) {
        encoders.register(LibHeifImageEncoder())
    }
}

internal actual fun configureLibHeifResolver(
    installation: LibHeifInstallation,
) {
    LibHeifResolver.installation = installation
    // Reset so the next availability check uses the new config
    LibHeifResolver.reset()
}

internal actual fun libHeifResolverDiagnostics(): String = LibHeifResolver.diagnosticMessage

internal actual fun resolvedLibHeifInstallationInfo(): String {
    if (!LibHeifResolver.available) return ""
    val mode = when (LibHeifResolver.installation) {
        is LibHeifInstallation.Bundled -> "bundled"
        is LibHeifInstallation.System  -> "system"
        is LibHeifInstallation.Custom  -> "custom (${(LibHeifResolver.installation as LibHeifInstallation.Custom).home})"
    }
    val encoder = if (LibHeifResolver.encoderAvailable) " + ${LibHeifResolver.encoderPath}" else " (decode only)"
    return "$mode -> ${LibHeifResolver.decoderPath}$encoder"
}
