package dev.transmute

import dev.transmute.image.transform.ImageBlurTransform
import dev.transmute.image.transform.ImageBrightnessContrastTransform
import dev.transmute.image.transform.ImageCropTransform
import dev.transmute.image.transform.ImageFlipTransform
import dev.transmute.image.transform.ImageGrayscaleTransform
import dev.transmute.image.transform.ImageOpacityTransform
import dev.transmute.image.transform.ImageResizeTransform
import dev.transmute.image.transform.ImageRotateTransform
import dev.transmute.image.transform.ImageScaleTransform
import dev.transmute.image.transform.kernel.ResampleFilter

/** Scale to fit within [maxWidth]×[maxHeight], preserving aspect ratio. No upscaling. */
fun <IN> DynamicImageTransmuterBuilder<IN>.scale(maxWidth: Int, maxHeight: Int): DynamicImageTransmuterBuilder<IN> = apply {
  transform { add(ImageScaleTransform(maxWidth, maxHeight)) }
}

/** Scale to fit within [maxWidth]×[maxHeight], preserving aspect ratio. No upscaling. */
fun <IN, OUT : dev.transmute.core.ImageFormatTag> ImageTransmuterBuilder<IN, OUT>.scale(maxWidth: Int, maxHeight: Int): ImageTransmuterBuilder<IN, OUT> = apply {
  transform { add(ImageScaleTransform(maxWidth, maxHeight)) }
}

fun <IN> DynamicImageTransmuterBuilder<IN>.resize(
  targetWidth: Int,
  targetHeight: Int,
  filter: ResampleFilter = ResampleFilter.BICUBIC_MITCHELL,
  allowUpscale: Boolean = true,
): DynamicImageTransmuterBuilder<IN> = apply {
  transform { add(ImageResizeTransform(targetWidth, targetHeight, filter, allowUpscale)) }
}

fun <IN, OUT : dev.transmute.core.ImageFormatTag> ImageTransmuterBuilder<IN, OUT>.resize(
  targetWidth: Int,
  targetHeight: Int,
  filter: ResampleFilter = ResampleFilter.BICUBIC_MITCHELL,
  allowUpscale: Boolean = true,
): ImageTransmuterBuilder<IN, OUT> = apply {
  transform { add(ImageResizeTransform(targetWidth, targetHeight, filter, allowUpscale)) }
}

/** Crop to the sub-region starting at ([x], [y]) with the given [width] and [height]. */
fun <IN> DynamicImageTransmuterBuilder<IN>.crop(x: Int, y: Int, width: Int, height: Int): DynamicImageTransmuterBuilder<IN> = apply {
  transform { add(ImageCropTransform(x, y, width, height)) }
}

/** Crop to the sub-region starting at ([x], [y]) with the given [width] and [height]. */
fun <IN, OUT : dev.transmute.core.ImageFormatTag> ImageTransmuterBuilder<IN, OUT>.crop(
  x: Int,
  y: Int,
  width: Int,
  height: Int,
): ImageTransmuterBuilder<IN, OUT> = apply {
  transform { add(ImageCropTransform(x, y, width, height)) }
}

/** Auto-rotate based on EXIF orientation metadata in the IR. */
fun <IN> DynamicImageTransmuterBuilder<IN>.autoRotate(): DynamicImageTransmuterBuilder<IN> = apply {
  transform { add(ImageRotateTransform()) }
}

/** Auto-rotate based on EXIF orientation metadata in the IR. */
fun <IN, OUT : dev.transmute.core.ImageFormatTag> ImageTransmuterBuilder<IN, OUT>.autoRotate(): ImageTransmuterBuilder<IN, OUT> = apply {
  transform { add(ImageRotateTransform()) }
}

/** Convert to grayscale using BT.709 luma coefficients. */
fun <IN> DynamicImageTransmuterBuilder<IN>.grayscale(): DynamicImageTransmuterBuilder<IN> = apply {
  transform { add(ImageGrayscaleTransform()) }
}

/** Convert to grayscale using BT.709 luma coefficients. */
fun <IN, OUT : dev.transmute.core.ImageFormatTag> ImageTransmuterBuilder<IN, OUT>.grayscale(): ImageTransmuterBuilder<IN, OUT> = apply {
  transform { add(ImageGrayscaleTransform()) }
}

/** Flip horizontally and/or vertically. */
fun <IN> DynamicImageTransmuterBuilder<IN>.flip(horizontal: Boolean = false, vertical: Boolean = false): DynamicImageTransmuterBuilder<IN> = apply {
  transform { add(ImageFlipTransform(horizontal, vertical)) }
}

/** Flip horizontally and/or vertically. */
fun <IN, OUT : dev.transmute.core.ImageFormatTag> ImageTransmuterBuilder<IN, OUT>.flip(
  horizontal: Boolean = false,
  vertical: Boolean = false,
): ImageTransmuterBuilder<IN, OUT> = apply {
  transform { add(ImageFlipTransform(horizontal, vertical)) }
}

/** Adjust brightness (−255..+255) and/or contrast (0..3). */
fun <IN> DynamicImageTransmuterBuilder<IN>.brightnessContrast(brightness: Float = 0f, contrast: Float = 1f): DynamicImageTransmuterBuilder<IN> = apply {
  transform { add(ImageBrightnessContrastTransform(brightness, contrast)) }
}

/** Adjust brightness (−255..+255) and/or contrast (0..3). */
fun <IN, OUT : dev.transmute.core.ImageFormatTag> ImageTransmuterBuilder<IN, OUT>.brightnessContrast(
  brightness: Float = 0f,
  contrast: Float = 1f,
): ImageTransmuterBuilder<IN, OUT> = apply {
  transform { add(ImageBrightnessContrastTransform(brightness, contrast)) }
}

/** Apply box blur with the given pixel [radius]. */
fun <IN> DynamicImageTransmuterBuilder<IN>.blur(radius: Int = 1): DynamicImageTransmuterBuilder<IN> = apply {
  transform { add(ImageBlurTransform(radius)) }
}

/** Apply box blur with the given pixel [radius]. */
fun <IN, OUT : dev.transmute.core.ImageFormatTag> ImageTransmuterBuilder<IN, OUT>.blur(radius: Int = 1): ImageTransmuterBuilder<IN, OUT> = apply {
  transform { add(ImageBlurTransform(radius)) }
}

/** Adjust alpha channel opacity (0.0 = transparent, 1.0 = unchanged). */
fun <IN> DynamicImageTransmuterBuilder<IN>.opacity(opacity: Float): DynamicImageTransmuterBuilder<IN> = apply {
  transform { add(ImageOpacityTransform(opacity)) }
}

/** Adjust alpha channel opacity (0.0 = transparent, 1.0 = unchanged). */
fun <IN, OUT : dev.transmute.core.ImageFormatTag> ImageTransmuterBuilder<IN, OUT>.opacity(opacity: Float): ImageTransmuterBuilder<IN, OUT> = apply {
  transform { add(ImageOpacityTransform(opacity)) }
}
