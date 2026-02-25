package dev.transmute.gstreamer

/**
 * Configuration for the optional GStreamer codec integration.
 *
 * Controls which media domains (audio, video, image) should have
 * GStreamer-backed codecs registered. All domains are enabled by default.
 *
 * ```kotlin
 * val ctx = TransmuteContext {
 *     gstreamer {
 *         audio = true
 *         video = true
 *         image = true
 *     }
 * }
 * ```
 */
class GStreamerConfig(
    /** Register GStreamer-backed audio codecs (AAC, M4A, Opus, FLAC encode, OGG encode). */
    val audio: Boolean = true,
    /** Register GStreamer-backed video codecs (MP4, MOV, WebM, AVI, MKV). */
    val video: Boolean = true,
    /** Register GStreamer-backed image codecs (HEIF, HEIC, AVIF). */
    val image: Boolean = true,
) {
    /** DSL builder for [GStreamerConfig]. */
    class Builder {
        var audio: Boolean = true
        var video: Boolean = true
        var image: Boolean = true
        fun build() = GStreamerConfig(audio = audio, video = video, image = image)
    }

    override fun toString(): String =
        "GStreamerConfig(audio=$audio, video=$video, image=$image)"
}
