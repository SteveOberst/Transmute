package dev.transmute.image

import dev.transmute.core.MediaFormat

/**
 * Typed image formats supported by Transmute.
 *
 * This replaces the old `enum class ImageFormat` with typed singleton objects,
 * enabling fixed-output transmuters without auxiliary type tags.
 */
sealed interface ImageFormat : MediaFormat<ImageDecodeOptions, ImageEncodeOptions> {

  data object Jpeg : ImageFormat { override val mimeType: String = "image/jpeg"; override val extension: String = "jpg" }
  data object Png : ImageFormat { override val mimeType: String = "image/png"; override val extension: String = "png" }
  data object Webp : ImageFormat { override val mimeType: String = "image/webp"; override val extension: String = "webp" }
  data object Heif : ImageFormat { override val mimeType: String = "image/heif"; override val extension: String = "heif" }
  data object Heic : ImageFormat { override val mimeType: String = "image/heic"; override val extension: String = "heic" }
  data object Avif : ImageFormat { override val mimeType: String = "image/avif"; override val extension: String = "avif" }
  data object Gif : ImageFormat { override val mimeType: String = "image/gif"; override val extension: String = "gif" }
  data object Bmp : ImageFormat { override val mimeType: String = "image/bmp"; override val extension: String = "bmp" }
  data object Tiff : ImageFormat { override val mimeType: String = "image/tiff"; override val extension: String = "tiff" }

  data object Unknown : ImageFormat { override val mimeType: String = "application/octet-stream"; override val extension: String = "bin" }

  companion object {
    val all: Set<ImageFormat> = setOf(Jpeg, Png, Webp, Heif, Heic, Avif, Gif, Bmp, Tiff)
  }
}

