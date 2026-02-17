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
fun ImageTransmuter.scale(maxWidth: Int, maxHeight: Int): ImageTransmuter = apply {
  pipeline.add(ImageScaleTransform(maxWidth, maxHeight))
}

/**
 * Resize to exact [targetWidth]×[targetHeight] using a configurable resample [filter].
 *
 * Unlike [scale], this does **not** preserve aspect ratio - it resizes to the
 * exact dimensions specified.
 */
fun ImageTransmuter.resize(
  targetWidth: Int,
  targetHeight: Int,
  filter: ResampleFilter = ResampleFilter.BICUBIC_MITCHELL,
  allowUpscale: Boolean = true,
): ImageTransmuter = apply {
  pipeline.add(ImageResizeTransform(targetWidth, targetHeight, filter, allowUpscale))
}

/** Crop to the sub-region starting at ([x], [y]) with the given [width] and [height]. */
fun ImageTransmuter.crop(x: Int, y: Int, width: Int, height: Int): ImageTransmuter = apply {
  pipeline.add(ImageCropTransform(x, y, width, height))
}

/** Auto-rotate based on EXIF orientation metadata in the IR. */
fun ImageTransmuter.autoRotate(): ImageTransmuter = apply {
  pipeline.add(ImageRotateTransform())
}

/** Convert to grayscale using BT.709 luma coefficients. */
fun ImageTransmuter.grayscale(): ImageTransmuter = apply {
  pipeline.add(ImageGrayscaleTransform())
}

/** Flip horizontally and/or vertically. */
fun ImageTransmuter.flip(horizontal: Boolean = false, vertical: Boolean = false): ImageTransmuter = apply {
  pipeline.add(ImageFlipTransform(horizontal, vertical))
}

/** Adjust brightness (−255..+255) and/or contrast (0..3). */
fun ImageTransmuter.brightnessContrast(brightness: Float = 0f, contrast: Float = 1f): ImageTransmuter = apply {
  pipeline.add(ImageBrightnessContrastTransform(brightness, contrast))
}

/** Apply box blur with the given pixel [radius]. */
fun ImageTransmuter.blur(radius: Int = 1): ImageTransmuter = apply {
  pipeline.add(ImageBlurTransform(radius))
}

/** Adjust alpha channel opacity (0.0 = transparent, 1.0 = unchanged). */
fun ImageTransmuter.opacity(opacity: Float): ImageTransmuter = apply {
  pipeline.add(ImageOpacityTransform(opacity))
}
