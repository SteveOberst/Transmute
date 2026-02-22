package dev.transmute.core

/** Type-level tags for concrete [ImageFormat] values. */
sealed interface ImageFormatTag : FormatTag<ImageFormat> {
  data object Jpeg : ImageFormatTag { override val format: ImageFormat = ImageFormat.JPEG }
  data object Png : ImageFormatTag { override val format: ImageFormat = ImageFormat.PNG }
  data object Webp : ImageFormatTag { override val format: ImageFormat = ImageFormat.WEBP }
  data object Heif : ImageFormatTag { override val format: ImageFormat = ImageFormat.HEIF }
  data object Heic : ImageFormatTag { override val format: ImageFormat = ImageFormat.HEIC }
  data object Avif : ImageFormatTag { override val format: ImageFormat = ImageFormat.AVIF }
  data object Gif : ImageFormatTag { override val format: ImageFormat = ImageFormat.GIF }
  data object Bmp : ImageFormatTag { override val format: ImageFormat = ImageFormat.BMP }
  data object Tiff : ImageFormatTag { override val format: ImageFormat = ImageFormat.TIFF }
}
