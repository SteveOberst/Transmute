package dev.transmute

import dev.transmute.image.ImageFormat
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

/** Scale to fit within [maxWidth]x[maxHeight], preserving aspect ratio. No upscaling. */
fun <IN, OUT> DynamicImageTransmuterBuilder<IN, OUT>.scale(maxWidth: Int, maxHeight: Int): DynamicImageTransmuterBuilder<IN, OUT> = apply {
  transform { add(ImageScaleTransform(maxWidth, maxHeight)) }
}

/** Scale to fit within [maxWidth]x[maxHeight], preserving aspect ratio. No upscaling. */
fun <IN, OUT : ImageFormat> ImageTransmuterBuilder<IN, OUT>.scale(maxWidth: Int, maxHeight: Int): ImageTransmuterBuilder<IN, OUT> = apply {
  transform { add(ImageScaleTransform(maxWidth, maxHeight)) }
}

fun <IN, OUT> DynamicImageTransmuterBuilder<IN, OUT>.resize(
  targetWidth: Int,
  targetHeight: Int,
  filter: ResampleFilter = ResampleFilter.BICUBIC_MITCHELL,
  allowUpscale: Boolean = true,
): DynamicImageTransmuterBuilder<IN, OUT> = apply {
  transform { add(ImageResizeTransform(targetWidth, targetHeight, filter, allowUpscale)) }
}

fun <IN, OUT : ImageFormat> ImageTransmuterBuilder<IN, OUT>.resize(
  targetWidth: Int,
  targetHeight: Int,
  filter: ResampleFilter = ResampleFilter.BICUBIC_MITCHELL,
  allowUpscale: Boolean = true,
): ImageTransmuterBuilder<IN, OUT> = apply {
  transform { add(ImageResizeTransform(targetWidth, targetHeight, filter, allowUpscale)) }
}

/** Crop to the sub-region starting at ([x], [y]) with the given [width] and [height]. */
fun <IN, OUT> DynamicImageTransmuterBuilder<IN, OUT>.crop(x: Int, y: Int, width: Int, height: Int): DynamicImageTransmuterBuilder<IN, OUT> =
  apply {
    transform { add(ImageCropTransform(x, y, width, height)) }
  }

/** Crop to the sub-region starting at ([x], [y]) with the given [width] and [height]. */
fun <IN, OUT : ImageFormat> ImageTransmuterBuilder<IN, OUT>.crop(
  x: Int,
  y: Int,
  width: Int,
  height: Int,
): ImageTransmuterBuilder<IN, OUT> = apply {
  transform { add(ImageCropTransform(x, y, width, height)) }
}

/** Rotate clockwise by [degrees] (90, 180, or 270). Defaults to 90 deg. */
fun <IN, OUT> DynamicImageTransmuterBuilder<IN, OUT>.rotate(degrees: Int = 90): DynamicImageTransmuterBuilder<IN, OUT> = apply {
  transform { add(ImageRotateTransform(degrees)) }
}

/** Rotate clockwise by [degrees] (90, 180, or 270). Defaults to 90 deg. */
fun <IN, OUT : ImageFormat> ImageTransmuterBuilder<IN, OUT>.rotate(degrees: Int = 90): ImageTransmuterBuilder<IN, OUT> = apply {
  transform { add(ImageRotateTransform(degrees)) }
}

/** Convert to grayscale using BT.709 luma coefficients. */
fun <IN, OUT> DynamicImageTransmuterBuilder<IN, OUT>.grayscale(): DynamicImageTransmuterBuilder<IN, OUT> = apply {
  transform { add(ImageGrayscaleTransform()) }
}

/** Convert to grayscale using BT.709 luma coefficients. */
fun <IN, OUT : ImageFormat> ImageTransmuterBuilder<IN, OUT>.grayscale(): ImageTransmuterBuilder<IN, OUT> = apply {
  transform { add(ImageGrayscaleTransform()) }
}

/** Flip horizontally and/or vertically. */
fun <IN, OUT> DynamicImageTransmuterBuilder<IN, OUT>.flip(
  horizontal: Boolean = false,
  vertical: Boolean = false,
): DynamicImageTransmuterBuilder<IN, OUT> = apply {
  transform { add(ImageFlipTransform(horizontal, vertical)) }
}

/** Flip horizontally and/or vertically. */
fun <IN, OUT : ImageFormat> ImageTransmuterBuilder<IN, OUT>.flip(
  horizontal: Boolean = false,
  vertical: Boolean = false,
): ImageTransmuterBuilder<IN, OUT> = apply {
  transform { add(ImageFlipTransform(horizontal, vertical)) }
}

/** Adjust brightness (255..+255) and/or contrast (0..3). */
fun <IN, OUT> DynamicImageTransmuterBuilder<IN, OUT>.brightnessContrast(
  brightness: Float = 0f,
  contrast: Float = 1f,
): DynamicImageTransmuterBuilder<IN, OUT> = apply {
  transform { add(ImageBrightnessContrastTransform(brightness, contrast)) }
}

/** Adjust brightness (255..+255) and/or contrast (0..3). */
fun <IN, OUT : ImageFormat> ImageTransmuterBuilder<IN, OUT>.brightnessContrast(
  brightness: Float = 0f,
  contrast: Float = 1f,
): ImageTransmuterBuilder<IN, OUT> = apply {
  transform { add(ImageBrightnessContrastTransform(brightness, contrast)) }
}

/** Apply box blur with the given pixel [radius]. */
fun <IN, OUT> DynamicImageTransmuterBuilder<IN, OUT>.blur(radius: Int = 1): DynamicImageTransmuterBuilder<IN, OUT> = apply {
  transform { add(ImageBlurTransform(radius)) }
}

/** Apply box blur with the given pixel [radius]. */
fun <IN, OUT : ImageFormat> ImageTransmuterBuilder<IN, OUT>.blur(radius: Int = 1): ImageTransmuterBuilder<IN, OUT> = apply {
  transform { add(ImageBlurTransform(radius)) }
}

/** Adjust alpha channel opacity (0.0 = transparent, 1.0 = unchanged). */
fun <IN, OUT> DynamicImageTransmuterBuilder<IN, OUT>.opacity(opacity: Float): DynamicImageTransmuterBuilder<IN, OUT> = apply {
  transform { add(ImageOpacityTransform(opacity)) }
}

/** Adjust alpha channel opacity (0.0 = transparent, 1.0 = unchanged). */
fun <IN, OUT : ImageFormat> ImageTransmuterBuilder<IN, OUT>.opacity(opacity: Float): ImageTransmuterBuilder<IN, OUT> = apply {
  transform { add(ImageOpacityTransform(opacity)) }
}
