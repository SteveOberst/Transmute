package dev.transmute.libheif

import dev.transmute.plugin.PluginFeature

/**
 * Strongly-typed feature toggles for the [LibHeif] plugin.
 *
 * Each constant represents a named capability that users can enable or
 * disable via the `configure { features { } }` DSL:
 *
 * ```kotlin
 * install(LibHeif) {
 *     disable(LibHeifFeature.ImageEncoding)
 * }
 * ```
 *
 * All features are **enabled by default**. Disable selectively:
 *
 * ```kotlin
 * install(LibHeif) {
 *     disable(LibHeifFeature.ImageEncoding)
 * }
 * ```
 */
object LibHeifFeature {

    /**
     * HEIF, HEIC, and AVIF image decoding via libheif.
     *
     * When enabled, a [LibHeifImageDecoder] is registered for all three
     * ISO BMFF-based image formats. The decoder converts to PNG via
     * `heif-dec` / `heif-convert`, then reads the PNG via ImageIO.
     */
    val ImageCodecs = PluginFeature(
        id = "image-codecs",
        description = "HEIF, HEIC, AVIF decode/encode via libheif",
        defaultEnabled = true,
    )

    /**
     * HEIF/HEIC/AVIF encoding via libheif (`heif-enc`).
     *
     * Disabling this feature still allows HEIF/AVIF *decoding* -- only
     * the encoder registration is skipped. Useful when only read access
     * to HEIF files is needed.
     */
    val ImageEncoding = PluginFeature(
        id = "image-encoding",
        description = "HEIF/HEIC/AVIF encoding via heif-enc",
        defaultEnabled = true,
    )

    /** All features supported by the libheif plugin. */
    val ALL: Set<PluginFeature> = setOf(
        ImageCodecs,
        ImageEncoding,
    )
}
