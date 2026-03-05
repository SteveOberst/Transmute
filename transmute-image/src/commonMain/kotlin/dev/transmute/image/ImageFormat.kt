package dev.transmute.image

import dev.transmute.model.core.ContainerFamily
import dev.transmute.model.core.MediaFormat

/**
 * Typed image formats supported by Transmute.
 *
 * This replaces the old `enum class ImageFormat` with typed singleton objects,
 * enabling fixed-output transmuters without auxiliary type tags.
 */
sealed interface ImageFormat : MediaFormat<ImageDecodeOptions, ImageEncodeOptions> {

  data object Jpeg : ImageFormat {
    override val label: String = "JPEG"
    override val mimeType: String = "image/jpeg"
    override val extension: String = "jpg"
    override val containerFamily: ContainerFamily = ContainerFamily.Jpeg
  }
  data object Png : ImageFormat {
    override val label: String = "PNG"
    override val mimeType: String = "image/png"
    override val extension: String = "png"
    override val containerFamily: ContainerFamily = ContainerFamily.Png
  }
  data object Webp : ImageFormat {
    override val label: String = "WebP"
    override val mimeType: String = "image/webp"
    override val extension: String = "webp"
    override val containerFamily: ContainerFamily = ContainerFamily.WebP
  }
  data object Heif : ImageFormat {
    override val label: String = "HEIF"
    override val mimeType: String = "image/heif"
    override val extension: String = "heif"
    override val containerFamily: ContainerFamily = ContainerFamily.Heif
  }
  data object Heic : ImageFormat {
    override val label: String = "HEIC"
    override val mimeType: String = "image/heic"
    override val extension: String = "heic"
    override val containerFamily: ContainerFamily = ContainerFamily.Heif
  }
  data object Avif : ImageFormat {
    override val label: String = "AVIF"
    override val mimeType: String = "image/avif"
    override val extension: String = "avif"
    override val containerFamily: ContainerFamily = ContainerFamily.Avif
  }
  data object Gif : ImageFormat {
    override val label: String = "GIF"
    override val mimeType: String = "image/gif"
    override val extension: String = "gif"
    override val containerFamily: ContainerFamily = ContainerFamily.Gif
  }
  data object Bmp : ImageFormat {
    override val label: String = "BMP"
    override val mimeType: String = "image/bmp"
    override val extension: String = "bmp"
    override val containerFamily: ContainerFamily = ContainerFamily.Bmp
  }
  data object Tiff : ImageFormat {
    override val label: String = "TIFF"
    override val mimeType: String = "image/tiff"
    override val extension: String = "tiff"
    override val containerFamily: ContainerFamily = ContainerFamily.Tiff
  }

  data object Unknown : ImageFormat {
    override val label: String = "Unknown"
    override val mimeType: String = "application/octet-stream"
    override val extension: String = "bin"
  }

  companion object {
    val all: Set<ImageFormat> = setOf(Jpeg, Png, Webp, Heif, Heic, Avif, Gif, Bmp, Tiff)
  }
}
