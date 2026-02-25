package dev.transmute.gstreamer

import dev.transmute.audio.MutableAudioDecoderRegistry
import dev.transmute.audio.MutableAudioEncoderRegistry
import dev.transmute.image.MutableImageDecoderRegistry
import dev.transmute.image.MutableImageEncoderRegistry
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
 * 3. Build the project — the CMake script in `src/androidMain/cpp/`
 *    will compile `libgstreamer_bridge.so` for each ABI.
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

internal actual fun installGstImageCodecs(
    decoders: MutableImageDecoderRegistry,
    encoders: MutableImageEncoderRegistry,
) {
    if (!GStreamerJni.available) return

    decoders.register(GstAndroidImageDecoder())

    if (GStreamerJni.hasElement("x265enc") || GStreamerJni.hasElement("av1enc")) {
        encoders.register(GstAndroidImageEncoder())
    }
}

internal actual fun installGstVideoCodecs(
    decoders: MutableVideoDecoderRegistry,
    encoders: MutableVideoEncoderRegistry,
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

    val avi = GstAndroidAviCodec()
    decoders.register(avi)
    encoders.register(avi)

    val mkv = GstAndroidMkvCodec()
    decoders.register(mkv)
    encoders.register(mkv)
}
