package dev.transmute.gstreamer

import dev.transmute.AudioIrCodecRegistry
import dev.transmute.VideoIrCodecRegistry
import dev.transmute.audio.MutableAudioDecoderRegistry
import dev.transmute.audio.MutableAudioEncoderRegistry
import dev.transmute.plugin.PluginFeaturesConfig
import dev.transmute.video.MutableVideoDecoderRegistry
import dev.transmute.video.MutableVideoEncoderRegistry

data class CodecInstallResult(
  val registered: Int,
  val skipped: Int,
) {
  operator fun plus(other: CodecInstallResult): CodecInstallResult =
    CodecInstallResult(
      registered = registered + other.registered,
      skipped = skipped + other.skipped,
    )
}

/**
 * Installs the public GStreamer-backed codec registrations into the provided registries.
 *
 * This installer covers the plugin's audio and video registrations. Public
 * HEIF/HEIC/AVIF support is documented separately through the platform image
 * stack and the dedicated `libheif` plugin.
 *
 * On Desktop/JVM, GStreamer is invoked via subprocess (`gst-launch-1.0`).
 * On Android, GStreamer is invoked via JNI (`libgstreamer_bridge.so`).
 * On iOS, GStreamer is invoked via cinterop (`GStreamer.framework`).
 */
object GStreamerCodecInstaller {

  /** `true` when a usable GStreamer installation has been detected on this platform. */
  val available: Boolean get() = isGStreamerAvailable()

  /**
   * Register GStreamer audio codecs: AAC, M4A, Opus (full codec),
   * plus FLAC and OGG/Vorbis encoders.
   */
  fun installAudioCodecs(codecs: AudioIrCodecRegistry): CodecInstallResult =
    installGstAudioCodecs(codecs.decoders, codecs.encoders)

  fun installAudioCodecs(decoders: MutableAudioDecoderRegistry, encoders: MutableAudioEncoderRegistry): CodecInstallResult =
    installGstAudioCodecs(decoders, encoders)

  /**
   * Register GStreamer video codecs: MP4, MOV, WebM, AVI, MKV.
   *
   * All video codecs are registered; use the feature-aware overload for
   * fine-grained control (e.g. disabling AVI).
   */
  fun installVideoCodecs(codecs: VideoIrCodecRegistry): CodecInstallResult =
    installGstVideoCodecs(codecs.decoders, codecs.encoders, PluginFeaturesConfig())

  fun installVideoCodecs(decoders: MutableVideoDecoderRegistry, encoders: MutableVideoEncoderRegistry): CodecInstallResult =
    installGstVideoCodecs(decoders, encoders, PluginFeaturesConfig())

  /**
   * Register GStreamer video codecs with feature-toggle control.
   *
   * When [GStreamerFeature.LegacyAvi] is disabled in [features],
   * the AVI codec is not registered.
   */
  fun installVideoCodecs(codecs: VideoIrCodecRegistry, features: PluginFeaturesConfig): CodecInstallResult =
    installGstVideoCodecs(codecs.decoders, codecs.encoders, features)

  fun installVideoCodecs(decoders: MutableVideoDecoderRegistry, encoders: MutableVideoEncoderRegistry, features: PluginFeaturesConfig): CodecInstallResult =
    installGstVideoCodecs(decoders, encoders, features)
}

internal expect fun isGStreamerAvailable(): Boolean

internal expect fun installGstAudioCodecs(decoders: MutableAudioDecoderRegistry, encoders: MutableAudioEncoderRegistry): CodecInstallResult

internal expect fun installGstVideoCodecs(
  decoders: MutableVideoDecoderRegistry,
  encoders: MutableVideoEncoderRegistry,
  features: PluginFeaturesConfig,
): CodecInstallResult

/**
 * Apply GStreamer configuration to the platform-specific resolver.
 *
 * On Desktop/JVM this configures [GStreamerResolver] with the installation
 * mode (bundled, custom, or system). On Android/iOS this is a no-op
 * (the native SDKs handle discovery internally).
 */
internal expect fun configureResolver(installation: GStreamerInstallation)

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
 * Example: `"system -> /usr/bin/gst-launch-1.0"`
 *
 * Returns an empty string on platforms where GStreamer is not available
 * or where discovery is handled natively (Android / iOS).
 */
internal expect fun resolvedInstallationInfo(): String
