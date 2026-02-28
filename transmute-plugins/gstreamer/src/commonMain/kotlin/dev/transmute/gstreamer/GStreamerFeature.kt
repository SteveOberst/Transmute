package dev.transmute.gstreamer

import dev.transmute.plugin.PluginFeature

/**
 * Strongly-typed feature toggles for the [GStreamer] plugin.
 *
 * Each constant represents a named capability that users can enable or
 * disable via the `configure { features { } }` DSL:
 *
 * ```kotlin
 * install(GStreamer) {
 *     configure {
 *         features {
 *             enable(GStreamerFeature.AudioCodecs)
 *             disable(GStreamerFeature.LegacyAvi)
 *             disable(GStreamerFeature.ImageEncoding)
 *         }
 *     }
 * }
 * ```
 *
 * All features are **enabled by default**. Disable selectively:
 *
 * ```kotlin
 * install(GStreamer) {
 *     disable(GStreamerFeature.LegacyAvi)
 *     disable(GStreamerFeature.ImageEncoding)
 * }
 * ```
 */
object GStreamerFeature {

    /**
     * GStreamer audio codec support: AAC, M4A, Opus (full codec),
     * plus FLAC and OGG/Vorbis encoding.
     */
    val AudioCodecs = PluginFeature(
        id = "audio-codecs",
        description = "GStreamer audio codecs (AAC, M4A, Opus, FLAC/OGG encode)",
        defaultEnabled = true,
    )

    /**
     * GStreamer image codec support: HEIF, HEIC, AVIF decode and encode.
     */
    val ImageCodecs = PluginFeature(
        id = "image-codecs",
        description = "GStreamer image codecs (HEIF, HEIC, AVIF decode/encode)",
        defaultEnabled = true,
    )

    /**
     * HEIF/AVIF encoding via GStreamer (requires `x265enc` or `av1enc`
     * GStreamer elements to be installed).
     *
     * Disabling this feature still allows HEIF/AVIF *decoding* — only
     * the encoder registration is skipped. This is useful when the
     * required GStreamer encoder elements are not available.
     */
    val ImageEncoding = PluginFeature(
        id = "image-encoding",
        description = "HEIF/AVIF encoding via x265enc/av1enc",
        defaultEnabled = true,
    )

    /**
     * GStreamer video codec support: MP4, MOV, WebM, AVI, MKV.
     */
    val VideoCodecs = PluginFeature(
        id = "video-codecs",
        description = "GStreamer video codecs (MP4, MOV, WebM, AVI, MKV)",
        defaultEnabled = true,
    )

    /**
     * Legacy AVI container support.
     *
     * AVI is a dated container format with significant limitations.
     * Disable this feature to skip AVI codec registration while keeping
     * all other video codecs.
     */
    val LegacyAvi = PluginFeature(
        id = "legacy-avi",
        description = "Legacy AVI container support",
        defaultEnabled = true,
    )

    /** All features supported by the GStreamer plugin. */
    val ALL: Set<PluginFeature> = setOf(
        AudioCodecs,
        ImageCodecs,
        ImageEncoding,
        VideoCodecs,
        LegacyAvi,
    )
}
