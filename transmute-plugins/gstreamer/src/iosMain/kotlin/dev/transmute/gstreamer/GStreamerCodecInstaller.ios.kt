package dev.transmute.gstreamer

import dev.transmute.audio.MutableAudioDecoderRegistry
import dev.transmute.audio.MutableAudioEncoderRegistry
import dev.transmute.plugin.PluginFeaturesConfig
import dev.transmute.video.MutableVideoDecoderRegistry
import dev.transmute.video.MutableVideoEncoderRegistry

/**
 * iOS GStreamer support via `GStreamer.framework` cinterop.
 *
 * GStreamer is checked lazily when [GStreamerIosBridge.available] is first
 * accessed. If the framework is not linked into the app binary (or
 * GStreamer init fails), all functions gracefully report GStreamer as
 * unavailable and skip codec registration.
 *
 * ### Embedding GStreamer in your iOS app
 *
 * 1. Download the GStreamer iOS framework from
 *    `https://gstreamer.freedesktop.org/data/pkg/ios/`.
 * 2. Add `GStreamer.framework` to your Xcode project's
 *    **Frameworks, Libraries, and Embedded Content**.
 * 3. This module's cinterop binding enables Kotlin/Native access to
 *    the GStreamer C API.
 *
 * **Note:** HEIF/HEIC/AVIF image codecs have been removed from GStreamer.
 * iOS supports these natively via CoreGraphics/ImageIO. Install the `libheif`
 * plugin for additional desktop support.
 */

internal actual fun isGStreamerAvailable(): Boolean = GStreamerIosBridge.available

internal actual fun installGstAudioCodecs(
    decoders: MutableAudioDecoderRegistry,
    encoders: MutableAudioEncoderRegistry,
) {
    if (!GStreamerIosBridge.available) return

    val aac = GstIosAacCodec()
    decoders.register(aac)
    encoders.register(aac)

    val m4a = GstIosM4aCodec()
    decoders.register(m4a)
    encoders.register(m4a)

    val opus = GstIosOpusCodec()
    decoders.register(opus)
    encoders.register(opus)

    encoders.register(GstIosFlacEncoder())
    encoders.register(GstIosOggVorbisEncoder())
}

internal actual fun installGstVideoCodecs(
    decoders: MutableVideoDecoderRegistry,
    encoders: MutableVideoEncoderRegistry,
    features: PluginFeaturesConfig,
) {
    if (!GStreamerIosBridge.available) return

    val mp4 = GstIosMp4Codec()
    decoders.register(mp4)
    encoders.register(mp4)

    val mov = GstIosMovCodec()
    decoders.register(mov)
    encoders.register(mov)

    val webm = GstIosWebmCodec()
    decoders.register(webm)
    encoders.register(webm)

    // Only register AVI if the LegacyAvi feature is enabled
    if (features.isEnabled(GStreamerFeature.LegacyAvi)) {
        val avi = GstIosAviCodec()
        decoders.register(avi)
        encoders.register(avi)
    }

    val mkv = GstIosMkvCodec()
    decoders.register(mkv)
    encoders.register(mkv)
}

internal actual fun configureResolver(
    installation: GStreamerInstallation,
) {
    // No-op on iOS - GStreamer is bundled via cinterop (GStreamer.framework)
}

internal actual fun resolverDiagnostics(): String = ""

internal actual fun resolvedInstallationInfo(): String = ""
