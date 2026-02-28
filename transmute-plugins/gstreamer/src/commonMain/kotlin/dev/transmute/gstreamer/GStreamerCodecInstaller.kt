package dev.transmute.gstreamer

import dev.transmute.audio.AudioRegistries
import dev.transmute.audio.MutableAudioDecoderRegistry
import dev.transmute.audio.MutableAudioEncoderRegistry
import dev.transmute.image.ImageRegistries
import dev.transmute.image.MutableImageDecoderRegistry
import dev.transmute.image.MutableImageEncoderRegistry
import dev.transmute.plugin.PluginFeaturesConfig
import dev.transmute.video.MutableVideoDecoderRegistry
import dev.transmute.video.MutableVideoEncoderRegistry
import dev.transmute.video.VideoRegistries

/**
 * Installs GStreamer-backed codecs into the provided registries.
 *
 * On Desktop/JVM, GStreamer is invoked via subprocess (`gst-launch-1.0`).
 * On Android, GStreamer is invoked via JNI (`libgstreamer_bridge.so`).
 * On iOS, GStreamer is invoked via cinterop (`GStreamer.framework`).
 */
object GStreamerCodecInstaller {

    /** `true` when a usable GStreamer installation has been detected on this platform. */
    val available: Boolean get() = isGStreamerAvailable()

    /**
     * Register GStreamer as a supplementary codec provider for **all** registries.
     *
     * GStreamer codecs will be installed automatically during each registry's
     * `installDefaults()`, filling any codec gaps left by platform-native codecs.
     *
     * Call this **once** at application startup, before the first codec operation:
     * ```kotlin
     * GStreamerCodecInstaller.registerAsSupplementary()
     * ```
     */
    fun registerAsSupplementary() {
        AudioRegistries.addSupplementaryInstaller { d, e -> installGstAudioCodecs(d, e) }
        ImageRegistries.addSupplementaryInstaller { d, e -> installGstImageCodecs(d, e, PluginFeaturesConfig()) }
        VideoRegistries.addSupplementaryInstaller { d, e -> installGstVideoCodecs(d, e, PluginFeaturesConfig()) }
    }

    /**
     * Register GStreamer audio codecs: AAC, M4A, Opus (full codec),
     * plus FLAC and OGG/Vorbis encoders.
     */
    fun installAudioCodecs(
        decoders: MutableAudioDecoderRegistry,
        encoders: MutableAudioEncoderRegistry,
    ) = installGstAudioCodecs(decoders, encoders)

    /**
     * Register GStreamer image codecs: HEIF, HEIC, AVIF decode/encode.
     *
     * All image codecs are registered; use the feature-aware overload for
     * fine-grained control over encoding.
     */
    fun installImageCodecs(
        decoders: MutableImageDecoderRegistry,
        encoders: MutableImageEncoderRegistry,
    ) = installGstImageCodecs(decoders, encoders, PluginFeaturesConfig())

    /**
     * Register GStreamer image codecs with feature-toggle control.
     *
     * When [GStreamerFeature.ImageEncoding] is disabled in [features],
     * only decoders are registered — the HEIF/AVIF encoder is skipped.
     */
    fun installImageCodecs(
        decoders: MutableImageDecoderRegistry,
        encoders: MutableImageEncoderRegistry,
        features: PluginFeaturesConfig,
    ) = installGstImageCodecs(decoders, encoders, features)

    /**
     * Register GStreamer video codecs: MP4, MOV, WebM, AVI, MKV.
     *
     * All video codecs are registered; use the feature-aware overload for
     * fine-grained control (e.g. disabling AVI).
     */
    fun installVideoCodecs(
        decoders: MutableVideoDecoderRegistry,
        encoders: MutableVideoEncoderRegistry,
    ) = installGstVideoCodecs(decoders, encoders, PluginFeaturesConfig())

    /**
     * Register GStreamer video codecs with feature-toggle control.
     *
     * When [GStreamerFeature.LegacyAvi] is disabled in [features],
     * the AVI codec is not registered.
     */
    fun installVideoCodecs(
        decoders: MutableVideoDecoderRegistry,
        encoders: MutableVideoEncoderRegistry,
        features: PluginFeaturesConfig,
    ) = installGstVideoCodecs(decoders, encoders, features)
}

internal expect fun isGStreamerAvailable(): Boolean

internal expect fun installGstAudioCodecs(
    decoders: MutableAudioDecoderRegistry,
    encoders: MutableAudioEncoderRegistry,
)

internal expect fun installGstImageCodecs(
    decoders: MutableImageDecoderRegistry,
    encoders: MutableImageEncoderRegistry,
    features: PluginFeaturesConfig,
)

internal expect fun installGstVideoCodecs(
    decoders: MutableVideoDecoderRegistry,
    encoders: MutableVideoEncoderRegistry,
    features: PluginFeaturesConfig,
)

/**
 * Apply GStreamer configuration to the platform-specific resolver.
 *
 * On Desktop/JVM this configures [GStreamerResolver] with the installation
 * mode (bundled, custom, or system). On Android/iOS this is a no-op
 * (the native SDKs handle discovery internally).
 */
internal expect fun configureResolver(
    installation: GStreamerInstallation,
)

/**
 * Returns diagnostic information from the GStreamer resolver.
 *
 * On Desktop/JVM this returns detail about the resolution attempt
 * (paths searched, what was found/not found). On Android/iOS this
 * returns an empty string (discovery is handled by the native SDKs).
 */
internal expect fun resolverDiagnostics(): String

/**
 * Returns a short human-readable description of the resolved GStreamer
 * installation, suitable for an INFO-level log line.
 *
 * Example: `"system → /usr/bin/gst-launch-1.0"`
 *
 * Returns an empty string on platforms where GStreamer is not available
 * or where discovery is handled natively (Android / iOS).
 */
internal expect fun resolvedInstallationInfo(): String
