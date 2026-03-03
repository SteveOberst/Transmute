package dev.transmute.gstreamer

import dev.transmute.audio.MutableAudioDecoderRegistry
import dev.transmute.audio.MutableAudioEncoderRegistry
import dev.transmute.plugin.PluginFeaturesConfig
import dev.transmute.video.MutableVideoDecoderRegistry
import dev.transmute.video.MutableVideoEncoderRegistry

internal actual fun isGStreamerAvailable(): Boolean = GStreamerResolver.available

internal actual fun installGstAudioCodecs(
    decoders: MutableAudioDecoderRegistry,
    encoders: MutableAudioEncoderRegistry,
) {
    if (!GStreamerResolver.available) return

    // Full codecs - decode + encode
    val aac = GstAacCodec()
    decoders.register(aac)
    encoders.register(aac)

    val m4a = GstM4aCodec()
    decoders.register(m4a)
    encoders.register(m4a)

    val opus = GstOpusCodec()
    decoders.register(opus)
    encoders.register(opus)

    // Encode-only - decode is handled natively by JFlac / JOrbis
    encoders.register(GstFlacEncoder())
    encoders.register(GstOggVorbisEncoder())
}

internal actual fun installGstVideoCodecs(
    decoders: MutableVideoDecoderRegistry,
    encoders: MutableVideoEncoderRegistry,
    features: PluginFeaturesConfig,
) {
    if (!GStreamerResolver.available) return

    val mp4 = GstMp4Codec()
    decoders.register(mp4)
    encoders.register(mp4)

    val mov = GstMovCodec()
    decoders.register(mov)
    encoders.register(mov)

    val webm = GstWebmCodec()
    decoders.register(webm)
    encoders.register(webm)

    // Only register AVI if the LegacyAvi feature is enabled
    if (features.isEnabled(GStreamerFeature.LegacyAvi)) {
        val avi = GstAviCodec()
        decoders.register(avi)
        encoders.register(avi)
    }

    val mkv = GstMkvCodec()
    decoders.register(mkv)
    encoders.register(mkv)
}

internal actual fun configureResolver(
    installation: GStreamerInstallation,
) {
    GStreamerResolver.installation = installation
    // Reset so the next availability check uses the new config
    GStreamerResolver.reset()
}

internal actual fun resolverDiagnostics(): String = GStreamerResolver.diagnosticMessage

internal actual fun resolvedInstallationInfo(): String {
    if (!GStreamerResolver.available) return ""
    val mode = when (GStreamerResolver.installation) {
        is GStreamerInstallation.Bundled -> "bundled"
        is GStreamerInstallation.System  -> "system"
        is GStreamerInstallation.Custom  -> "custom (${(GStreamerResolver.installation as GStreamerInstallation.Custom).home})"
    }
    return "$mode -> ${GStreamerResolver.gstLaunchPath}"
}
