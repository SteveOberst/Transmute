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
 * This installer registers the iOS plugin's public audio and video codecs.
 * HEIF/HEIC/AVIF support is documented through the platform image stack and
 * the dedicated `libheif` plugin.
 */

internal actual fun isGStreamerAvailable(): Boolean = GStreamerIosBridge.available

internal actual fun installGstAudioCodecs(
    decoders: MutableAudioDecoderRegistry,
    encoders: MutableAudioEncoderRegistry,
): CodecInstallResult {
    if (!GStreamerIosBridge.available) return CodecInstallResult(registered = 0, skipped = 0)
    var registered = 0
    var skipped = 0

    val aac = GstIosAacCodec()
    if (decoders.register(aac)) registered++ else skipped++
    if (encoders.register(aac)) registered++ else skipped++

    val m4a = GstIosM4aCodec()
    if (decoders.register(m4a)) registered++ else skipped++
    if (encoders.register(m4a)) registered++ else skipped++

    val opus = GstIosOpusCodec()
    if (decoders.register(opus)) registered++ else skipped++
    if (encoders.register(opus)) registered++ else skipped++

    if (encoders.register(GstIosFlacEncoder())) registered++ else skipped++
    if (encoders.register(GstIosOggVorbisEncoder())) registered++ else skipped++

    return CodecInstallResult(registered = registered, skipped = skipped)
}

internal actual fun installGstVideoCodecs(
    decoders: MutableVideoDecoderRegistry,
    encoders: MutableVideoEncoderRegistry,
    features: PluginFeaturesConfig,
): CodecInstallResult {
    if (!GStreamerIosBridge.available) return CodecInstallResult(registered = 0, skipped = 0)
    var registered = 0
    var skipped = 0

    val mp4 = GstIosMp4Codec()
    if (decoders.register(mp4)) registered++ else skipped++
    if (encoders.register(mp4)) registered++ else skipped++

    val mov = GstIosMovCodec()
    if (decoders.register(mov)) registered++ else skipped++
    if (encoders.register(mov)) registered++ else skipped++

    val webm = GstIosWebmCodec()
    if (decoders.register(webm)) registered++ else skipped++
    if (encoders.register(webm)) registered++ else skipped++

    // Only register AVI if the LegacyAvi feature is enabled
    if (features.isEnabled(GStreamerFeature.LegacyAvi)) {
        val avi = GstIosAviCodec()
        if (decoders.register(avi)) registered++ else skipped++
        if (encoders.register(avi)) registered++ else skipped++
    }

    val mkv = GstIosMkvCodec()
    if (decoders.register(mkv)) registered++ else skipped++
    if (encoders.register(mkv)) registered++ else skipped++

    return CodecInstallResult(registered = registered, skipped = skipped)
}

internal actual fun configureResolver(
    installation: GStreamerInstallation,
) {
    // No-op on iOS - GStreamer is bundled via cinterop (GStreamer.framework)
}

internal actual fun resolverDiagnostics(): String = ""

internal actual fun resolvedInstallationInfo(): String = ""
