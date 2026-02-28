package dev.transmute.gstreamer

import dev.transmute.audio.MutableAudioDecoderRegistry
import dev.transmute.audio.MutableAudioEncoderRegistry
import dev.transmute.audio.audioDecoders
import dev.transmute.audio.audioEncoders
import dev.transmute.audio.codecs.WavDecoder
import dev.transmute.audio.codecs.WavEncoder
import dev.transmute.audio.installPlatformAudioCodecs
import dev.transmute.common.MediaDomain
import dev.transmute.common.TransmuteContext
import dev.transmute.image.ImageFormat
import dev.transmute.image.MutableImageDecoderRegistry
import dev.transmute.image.MutableImageEncoderRegistry
import dev.transmute.image.codecs.bmp.BmpImageDecoder
import dev.transmute.image.codecs.bmp.BmpImageEncoder
import dev.transmute.image.imageDecoders
import dev.transmute.image.imageEncoders
import dev.transmute.image.installPlatformImageCodecs
import dev.transmute.video.MutableVideoDecoderRegistry
import dev.transmute.video.MutableVideoEncoderRegistry
import dev.transmute.video.installPlatformVideoCodecs
import dev.transmute.video.videoDecoders
import dev.transmute.video.videoEncoders

private const val GSTREAMER_CONFIG_KEY = "transmute.gstreamer.config"

/**
 * Enable GStreamer codecs in this [TransmuteContext].
 *
 * Creates combined registries containing both native platform codecs and
 * GStreamer additions for the enabled domains. By default, the bundled
 * GStreamer runtime is used — no separate installation required.
 *
 * ```kotlin
 * val ctx = TransmuteContext {
 *     gstreamer {
 *         domains(MediaDomain.AUDIO or MediaDomain.VIDEO)
 *         // or use convenience functions:
 *         audio(true)
 *         video(true)
 *         image(false)
 *     }
 * }
 * ```
 */
fun TransmuteContext.Builder.gstreamer(block: GStreamerConfig.Builder.() -> Unit = {}) {
    val config = GStreamerConfig.Builder().apply(block).build()
    extra(GSTREAMER_CONFIG_KEY, config)

    // Apply resolver configuration
    configureResolver(config.installation)

    if (MediaDomain.AUDIO in config.domains) {
        val decoders = MutableAudioDecoderRegistry()
        val encoders = MutableAudioEncoderRegistry()
        decoders.register(WavDecoder())
        encoders.register(WavEncoder())
        installPlatformAudioCodecs(decoders, encoders)
        if (GStreamerCodecInstaller.available) {
            GStreamerCodecInstaller.installAudioCodecs(decoders, encoders)
        }
        audioDecoders(decoders)
        audioEncoders(encoders)
    }

    if (MediaDomain.IMAGE in config.domains) {
        val decoders = MutableImageDecoderRegistry()
        val encoders = MutableImageEncoderRegistry()
        installPlatformImageCodecs(decoders, encoders)
        if (decoders.decoderFor(ImageFormat.Bmp) == null) {
            decoders.register(BmpImageDecoder())
        }
        if (encoders.encoderFor(ImageFormat.Bmp) == null) {
            encoders.register(BmpImageEncoder())
        }
        if (GStreamerCodecInstaller.available) {
            GStreamerCodecInstaller.installImageCodecs(decoders, encoders)
        }
        imageDecoders(decoders)
        imageEncoders(encoders)
    }

    if (MediaDomain.VIDEO in config.domains) {
        val decoders = MutableVideoDecoderRegistry()
        val encoders = MutableVideoEncoderRegistry()
        installPlatformVideoCodecs(decoders, encoders)
        if (GStreamerCodecInstaller.available) {
            GStreamerCodecInstaller.installVideoCodecs(decoders, encoders)
        }
        videoDecoders(decoders)
        videoEncoders(encoders)
    }
}

/**
 * GStreamer configuration stored in this context, or `null` if GStreamer
 * was not enabled via [gstreamer].
 */
val TransmuteContext.gstreamerConfig: GStreamerConfig?
    get() = service(GSTREAMER_CONFIG_KEY)
