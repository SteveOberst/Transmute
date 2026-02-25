package dev.transmute.gstreamer

import dev.transmute.audio.AudioRegistries
import dev.transmute.audio.MutableAudioDecoderRegistry
import dev.transmute.audio.MutableAudioEncoderRegistry
import dev.transmute.image.ImageRegistries
import dev.transmute.image.MutableImageDecoderRegistry
import dev.transmute.image.MutableImageEncoderRegistry
import dev.transmute.video.MutableVideoDecoderRegistry
import dev.transmute.video.MutableVideoEncoderRegistry
import dev.transmute.video.VideoRegistries

/**
 * Installs GStreamer-backed codecs into the provided registries.
 *
 * On Desktop/JVM, GStreamer is invoked via subprocess (`gst-launch-1.0`).
 * On Android, GStreamer is invoked via JNI (`libgstreamer_bridge.so`).
 * On iOS, GStreamer is invoked via cinterop (`GStreamer.framework`).
 */
object GStreamerCodecInstaller {

    /** `true` when a usable GStreamer installation has been detected on this platform. */
    val available: Boolean get() = isGStreamerAvailable()

    /**
     * Register GStreamer as a supplementary codec provider for **all** registries.
     *
     * GStreamer codecs will be installed automatically during each registry's
     * `installDefaults()`, filling any codec gaps left by platform-native codecs.
     *
     * Call this **once** at application startup, before the first codec operation:
     * ```kotlin
     * GStreamerCodecInstaller.registerAsSupplementary()
     * ```
     */
    fun registerAsSupplementary() {
        AudioRegistries.addSupplementaryInstaller { d, e -> installGstAudioCodecs(d, e) }
        ImageRegistries.addSupplementaryInstaller { d, e -> installGstImageCodecs(d, e) }
        VideoRegistries.addSupplementaryInstaller { d, e -> installGstVideoCodecs(d, e) }
    }

    /**
     * Register GStreamer audio codecs: AAC, M4A, Opus (full codec),
     * plus FLAC and OGG/Vorbis encoders.
     */
    fun installAudioCodecs(
        decoders: MutableAudioDecoderRegistry,
        encoders: MutableAudioEncoderRegistry,
    ) = installGstAudioCodecs(decoders, encoders)

    /**
     * Register GStreamer image codecs: HEIF, HEIC, AVIF decode/encode.
     */
    fun installImageCodecs(
        decoders: MutableImageDecoderRegistry,
        encoders: MutableImageEncoderRegistry,
    ) = installGstImageCodecs(decoders, encoders)

    /**
     * Register GStreamer video codecs: MP4, MOV, WebM, AVI, MKV.
     */
    fun installVideoCodecs(
        decoders: MutableVideoDecoderRegistry,
        encoders: MutableVideoEncoderRegistry,
    ) = installGstVideoCodecs(decoders, encoders)
}

internal expect fun isGStreamerAvailable(): Boolean

internal expect fun installGstAudioCodecs(
    decoders: MutableAudioDecoderRegistry,
    encoders: MutableAudioEncoderRegistry,
)

internal expect fun installGstImageCodecs(
    decoders: MutableImageDecoderRegistry,
    encoders: MutableImageEncoderRegistry,
)

internal expect fun installGstVideoCodecs(
    decoders: MutableVideoDecoderRegistry,
    encoders: MutableVideoEncoderRegistry,
)
