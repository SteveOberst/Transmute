package dev.transmute.gstreamer

import dev.transmute.audio.MutableAudioDecoderRegistry
import dev.transmute.audio.MutableAudioEncoderRegistry
import dev.transmute.plugin.PluginFeaturesConfig
import dev.transmute.video.MutableVideoDecoderRegistry
import dev.transmute.video.MutableVideoEncoderRegistry

internal actual fun isGStreamerAvailable(): Boolean = GStreamerResolver.available

internal actual fun installGstAudioCodecs(decoders: MutableAudioDecoderRegistry, encoders: MutableAudioEncoderRegistry): CodecInstallResult {
  if (!GStreamerResolver.available) return CodecInstallResult(registered = 0, skipped = 0)
  var registered = 0

  // Full codecs - decode + encode
  val aac = GstAacCodec()
  decoders.register(aac)
  registered++
  encoders.register(aac)
  registered++

  val m4a = GstM4aCodec()
  decoders.register(m4a)
  registered++
  encoders.register(m4a)
  registered++

  val opus = GstOpusCodec()
  decoders.register(opus)
  registered++
  encoders.register(opus)
  registered++

  // Encode-only - decode is handled natively by JFlac / JOrbis
  encoders.register(GstFlacEncoder())
  registered++
  encoders.register(GstOggVorbisEncoder())
  registered++

  return CodecInstallResult(registered = registered, skipped = 0)
}

internal actual fun installGstVideoCodecs(
  decoders: MutableVideoDecoderRegistry,
  encoders: MutableVideoEncoderRegistry,
  features: PluginFeaturesConfig,
): CodecInstallResult {
  if (!GStreamerResolver.available) return CodecInstallResult(registered = 0, skipped = 0)
  var registered = 0

  val mp4 = GstMp4Codec()
  decoders.register(mp4)
  registered++
  encoders.register(mp4)
  registered++

  val mov = GstMovCodec()
  decoders.register(mov)
  registered++
  encoders.register(mov)
  registered++

  val webm = GstWebmCodec()
  decoders.register(webm)
  registered++
  encoders.register(webm)
  registered++

  // Only register AVI if the LegacyAvi feature is enabled
  if (features.isEnabled(GStreamerFeature.LegacyAvi)) {
    val avi = GstAviCodec()
    decoders.register(avi)
    registered++
    encoders.register(avi)
    registered++
  }

  val mkv = GstMkvCodec()
  decoders.register(mkv)
  registered++
  encoders.register(mkv)
  registered++

  return CodecInstallResult(registered = registered, skipped = 0)
}

internal actual fun configureResolver(installation: GStreamerInstallation) {
  GStreamerResolver.installation = installation
  // Reset so the next availability check uses the new config
  GStreamerResolver.reset()
}

internal actual fun resolverDiagnostics(): String = GStreamerResolver.diagnosticMessage

internal actual fun resolvedInstallationInfo(): String {
  if (!GStreamerResolver.available) return ""
  val mode = when (GStreamerResolver.installation) {
    is GStreamerInstallation.Bundled -> "bundled"
    is GStreamerInstallation.System -> "system"
    is GStreamerInstallation.Custom -> "custom (${(GStreamerResolver.installation as GStreamerInstallation.Custom).home})"
  }
  return "$mode -> ${GStreamerResolver.gstLaunchPath}"
}
