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
 *
 * ## Licensing
 *
 * libheif itself is licensed under **LGPL-3.0**.
 * The codec plugins bundled with libheif carry their own licenses:
 *
 * | Codec       | Format      | Operation | License             | Notes                               |
 * |---|---|---|---|---|
 * | libde265    | HEIF / HEIC | Decode    | LGPL-3.0            | Free to use                         |
 * | x265        | HEIF / HEIC | Encode    | GPL-2.0 / commercial | See note below                     |
 * | libaom      | AVIF        | Both      | BSD-2-Clause        | Free to use                         |
 * | libdav1d    | AVIF        | Decode    | BSD-2-Clause        | Free to use                         |
 * | rav1e       | AVIF        | Encode    | BSD-2-Clause        | Free to use                         |
 *
 * ### x265 / HEIC encoding and GPL
 *
 * x265 is dual-licensed: **GPL-2.0** (open source) or a paid commercial license.
 *
 * GPL-2.0 is a *copyleft* license. If you distribute software that links to or
 * bundles x265 (directly or via libheif), the GPL requires you to:
 *  - Release the full source code of your application under GPL-2.0-compatible terms, and
 *  - Include the GPL-2.0 license text in your distribution.
 *
 * This applies to most uses of [ImageEncoding] with HEIF/HEIC format.
 * AVIF *encoding* (via libaom / rav1e) and all *decoding* operations are
 * unaffected -- they use permissive-licensed codecs.
 *
 * **If you cannot comply with GPL-2.0**, you have two options:
 *  1. Purchase a commercial x265 license: https://www.x265.org/license-comparison/
 *  2. Disable [ImageEncoding] (decoding remains fully available under LGPL/BSD).
 *
 * Reference: https://www.videolan.org/developers/x265.html
 */
object LibHeifFeature {

  /**
   * HEIF, HEIC, and AVIF image decoding via libheif.
   *
   * When enabled, a [LibHeifImageDecoder] is registered for all three
   * ISO BMFF-based image formats. The decoder converts to PNG via
   * `heif-dec` / `heif-convert`, then reads the PNG via ImageIO.
   *
   * **Licensing:** decoding uses libde265 (LGPL-3.0) for HEIF/HEIC and
   * libaom / libdav1d (BSD-2-Clause) for AVIF. No GPL components are required
   * for decoding.
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
   *
   * **Licensing -- important:** HEIF/HEIC *encoding* uses x265 by default,
   * which is licensed under **GPL-2.0** (or a paid commercial license).
   * Distributing an application that bundles x265 requires GPL-2.0 compliance
   * unless you hold a commercial x265 license.
   * AVIF encoding (via libaom / rav1e) is BSD-licensed and unaffected.
   *
   * See the [LibHeifFeature] class KDoc for the full licensing breakdown.
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
