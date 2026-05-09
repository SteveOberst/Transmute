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
 * This installer registers the Android plugin's public audio and video codecs.
 * HEIF/HEIC/AVIF support is documented through the platform image stack and
 * the dedicated `libheif` plugin.
 */

internal actual fun isGStreamerAvailable(): Boolean = GStreamerJni.available

internal actual fun installGstAudioCodecs(decoders: MutableAudioDecoderRegistry, encoders: MutableAudioEncoderRegistry): CodecInstallResult {
  if (!GStreamerJni.available) return CodecInstallResult(registered = 0, skipped = 0)
  var registered = 0

  val aac = GstAndroidAacCodec()
  decoders.register(aac)
  registered++
  encoders.register(aac)
  registered++

  val m4a = GstAndroidM4aCodec()
  decoders.register(m4a)
  registered++
  encoders.register(m4a)
  registered++

  val opus = GstAndroidOpusCodec()
  decoders.register(opus)
  registered++
  encoders.register(opus)
  registered++

  encoders.register(GstAndroidFlacEncoder())
  registered++
  encoders.register(GstAndroidOggVorbisEncoder())
  registered++

  return CodecInstallResult(registered = registered, skipped = 0)
}

internal actual fun installGstVideoCodecs(
  decoders: MutableVideoDecoderRegistry,
  encoders: MutableVideoEncoderRegistry,
  features: PluginFeaturesConfig,
): CodecInstallResult {
  if (!GStreamerJni.available) return CodecInstallResult(registered = 0, skipped = 0)
  var registered = 0

  val mp4 = GstAndroidMp4Codec()
  decoders.register(mp4)
  registered++
  encoders.register(mp4)
  registered++

  val mov = GstAndroidMovCodec()
  decoders.register(mov)
  registered++
  encoders.register(mov)
  registered++

  val webm = GstAndroidWebmCodec()
  decoders.register(webm)
  registered++
  encoders.register(webm)
  registered++

  // Only register AVI if the LegacyAvi feature is enabled
  if (features.isEnabled(GStreamerFeature.LegacyAvi)) {
    val avi = GstAndroidAviCodec()
    decoders.register(avi)
    registered++
    encoders.register(avi)
    registered++
  }

  val mkv = GstAndroidMkvCodec()
  decoders.register(mkv)
  registered++
  encoders.register(mkv)
  registered++

  return CodecInstallResult(registered = registered, skipped = 0)
}

internal actual fun configureResolver(installation: GStreamerInstallation) {
  // No-op on Android - GStreamer is bundled via JNI (libgstreamer_bridge.so)
}

internal actual fun resolverDiagnostics(): String = ""

internal actual fun resolvedInstallationInfo(): String = ""
