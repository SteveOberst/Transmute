package dev.transmute.gstreamer

import dev.transmute.audio.MutableAudioDecoderRegistry
import dev.transmute.audio.MutableAudioEncoderRegistry
import dev.transmute.image.MutableImageDecoderRegistry
import dev.transmute.image.MutableImageEncoderRegistry
import dev.transmute.video.MutableVideoDecoderRegistry
import dev.transmute.video.MutableVideoEncoderRegistry

internal actual fun isGStreamerAvailable(): Boolean = GStreamerResolver.available

internal actual fun installGstAudioCodecs(
    decoders: MutableAudioDecoderRegistry,
    encoders: MutableAudioEncoderRegistry,
) {
    if (!GStreamerResolver.available) return

    // Full codecs — decode + encode
    val aac = GstAacCodec()
    decoders.register(aac)
    encoders.register(aac)

    val m4a = GstM4aCodec()
    decoders.register(m4a)
    encoders.register(m4a)

    val opus = GstOpusCodec()
    decoders.register(opus)
    encoders.register(opus)

    // Encode-only — decode is handled natively by JFlac / JOrbis
    encoders.register(GstFlacEncoder())
    encoders.register(GstOggVorbisEncoder())
}

internal actual fun installGstImageCodecs(
    decoders: MutableImageDecoderRegistry,
    encoders: MutableImageEncoderRegistry,
) {
    if (!GStreamerResolver.available) return

    decoders.register(GstImageDecoder())

    // Only register encoder if required GStreamer elements are present
    if (GStreamerResolver.hasElement("x265enc") || GStreamerResolver.hasElement("av1enc")) {
        encoders.register(GstImageEncoder())
    }
}

internal actual fun installGstVideoCodecs(
    decoders: MutableVideoDecoderRegistry,
    encoders: MutableVideoEncoderRegistry,
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

    val avi = GstAviCodec()
    decoders.register(avi)
    encoders.register(avi)

    val mkv = GstMkvCodec()
    decoders.register(mkv)
    encoders.register(mkv)
}
