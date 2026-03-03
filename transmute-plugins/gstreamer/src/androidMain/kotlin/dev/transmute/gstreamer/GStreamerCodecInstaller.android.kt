package dev.transmute.gstreamer

import dev.transmute.audio.MutableAudioDecoderRegistry
import dev.transmute.audio.MutableAudioEncoderRegistry
import dev.transmute.plugin.PluginFeaturesConfig
import dev.transmute.video.MutableVideoDecoderRegistry
import dev.transmute.video.MutableVideoEncoderRegistry

/**
 * Android GStreamer support via the `gstreamer_bridge` JNI library.
 *
 * GStreamer is loaded lazily when [GStreamerJni.available] is first
 * accessed. If the native library is not bundled with the APK (or
 * GStreamer init fails), all functions gracefully report GStreamer as
 * unavailable and skip codec registration.
 *
 * ### Embedding GStreamer in your Android app
 *
 * 1. Download the GStreamer Android SDK from
 *    `https://gstreamer.freedesktop.org/data/pkg/android/`.
 * 2. Set `GSTREAMER_ROOT_ANDROID` to the extracted SDK root.
 * 3. Build the project - the CMake script in `src/androidMain/cpp/`
 *    will compile `libgstreamer_bridge.so` for each ABI.
 *
 * **Note:** HEIF/HEIC/AVIF image codecs have been removed from GStreamer.
 * Android supports these natively via BitmapFactory. Install the `libheif`
 * plugin for additional desktop support.
 */

internal actual fun isGStreamerAvailable(): Boolean = GStreamerJni.available

internal actual fun installGstAudioCodecs(
    decoders: MutableAudioDecoderRegistry,
    encoders: MutableAudioEncoderRegistry,
) {
    if (!GStreamerJni.available) return

    val aac = GstAndroidAacCodec()
    decoders.register(aac)
    encoders.register(aac)

    val m4a = GstAndroidM4aCodec()
    decoders.register(m4a)
    encoders.register(m4a)

    val opus = GstAndroidOpusCodec()
    decoders.register(opus)
    encoders.register(opus)

    encoders.register(GstAndroidFlacEncoder())
    encoders.register(GstAndroidOggVorbisEncoder())
}

internal actual fun installGstVideoCodecs(
    decoders: MutableVideoDecoderRegistry,
    encoders: MutableVideoEncoderRegistry,
    features: PluginFeaturesConfig,
) {
    if (!GStreamerJni.available) return

    val mp4 = GstAndroidMp4Codec()
    decoders.register(mp4)
    encoders.register(mp4)

    val mov = GstAndroidMovCodec()
    decoders.register(mov)
    encoders.register(mov)

    val webm = GstAndroidWebmCodec()
    decoders.register(webm)
    encoders.register(webm)

    // Only register AVI if the LegacyAvi feature is enabled
    if (features.isEnabled(GStreamerFeature.LegacyAvi)) {
        val avi = GstAndroidAviCodec()
        decoders.register(avi)
        encoders.register(avi)
    }

    val mkv = GstAndroidMkvCodec()
    decoders.register(mkv)
    encoders.register(mkv)
}

internal actual fun configureResolver(
    installation: GStreamerInstallation,
) {
    // No-op on Android - GStreamer is bundled via JNI (libgstreamer_bridge.so)
}

internal actual fun resolverDiagnostics(): String = ""

internal actual fun resolvedInstallationInfo(): String = ""
