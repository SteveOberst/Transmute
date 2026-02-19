package dev.transmute.image.transform

import dev.transmute.core.ConversionContext
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.ImageIR
import dev.transmute.image.Orientation
import dev.transmute.image.ImageHint
import dev.transmute.image.ImageTransform
import dev.transmute.core.pipeline.TransformId

/**
 * Applies EXIF-based orientation rotation to an [ImageIR], then sets
 * [Orientation.NORMAL].
 *
 * **Why this matters:**
 * - iOS photos (HEIC/JPEG) write pixels in sensor orientation and
 *   store the real orientation in EXIF tag 274. Without this transform,
 *   a portrait photo appears sideways.
 * - Android photos from some OEMs (Samsung, Xiaomi) do the same.
 * - After this transform, the pixel buffer is physically rotated and
 *   the orientation tag becomes [Orientation.NORMAL], so encoders and
 *   viewers display the image correctly regardless of EXIF support.
 *
 * Supports all three non-trivial orientations:
 * - [Orientation.ROTATE_90] - 90° CW (portrait, camera held upright)
 * - [Orientation.ROTATE_180] - upside down
 * - [Orientation.ROTATE_270] - 90° CCW (landscape, camera held left)
 *
 * This is a pure pixel-shuffle - no interpolation, no quality loss.
 */
class ImageRotateTransform : ImageTransform {

  override fun wouldTransform(hint: ImageHint): Boolean = true // always applies EXIF rotation

  override val id: TransformId = TransformId("image-rotate")

  override suspend fun apply(ir: ImageIR, context: ConversionContext): ImageIR {
    if (ir.orientation == Orientation.NORMAL) {
      context.logger.debug("ImageRotateTransform: already NORMAL - skipping")
      return ir
    }

    val srcBuffer = ir.buffer as? ByteArrayPixelBuffer
      ?: error("ImageRotateTransform requires ByteArrayPixelBuffer")

    context.logger.info("ImageRotateTransform: applying ${ir.orientation}")

    val bpp = ir.pixelFormat.bytesPerPixel
    val srcData = srcBuffer.data
    val srcW = ir.width
    val srcH = ir.height
    val srcStride = ir.stride

    return when (ir.orientation) {
      Orientation.ROTATE_90 -> {
        // 90° CW: (x,y) → (srcH-1-y, x). New dimensions: srcH × srcW.
        val dstW = srcH
        val dstH = srcW
        val dstStride = dstW * bpp
        val dstData = ByteArray(dstH * dstStride)

        for (y in 0 until srcH) {
          for (x in 0 until srcW) {
            val srcOff = y * srcStride + x * bpp
            val dx = srcH - 1 - y
            val dy = x
            val dstOff = dy * dstStride + dx * bpp
            srcData.copyInto(dstData, dstOff, srcOff, srcOff + bpp)
          }
        }

        ir.copy(
          buffer = ByteArrayPixelBuffer(dstData),
          width = dstW,
          height = dstH,
          stride = dstStride,
          orientation = Orientation.NORMAL,
        )
      }

      Orientation.ROTATE_180 -> {
        // 180°: (x,y) → (srcW-1-x, srcH-1-y). Same dimensions.
        val dstStride = srcW * bpp
        val dstData = ByteArray(srcH * dstStride)

        for (y in 0 until srcH) {
          for (x in 0 until srcW) {
            val srcOff = y * srcStride + x * bpp
            val dx = srcW - 1 - x
            val dy = srcH - 1 - y
            val dstOff = dy * dstStride + dx * bpp
            srcData.copyInto(dstData, dstOff, srcOff, srcOff + bpp)
          }
        }

        ir.copy(
          buffer = ByteArrayPixelBuffer(dstData),
          width = srcW,
          height = srcH,
          stride = dstStride,
          orientation = Orientation.NORMAL,
        )
      }

      Orientation.ROTATE_270 -> {
        // 270° CW (= 90° CCW): (x,y) → (y, srcW-1-x). New dimensions: srcH × srcW.
        val dstW = srcH
        val dstH = srcW
        val dstStride = dstW * bpp
        val dstData = ByteArray(dstH * dstStride)

        for (y in 0 until srcH) {
          for (x in 0 until srcW) {
            val srcOff = y * srcStride + x * bpp
            val dx = y
            val dy = srcW - 1 - x
            val dstOff = dy * dstStride + dx * bpp
            srcData.copyInto(dstData, dstOff, srcOff, srcOff + bpp)
          }
        }

        ir.copy(
          buffer = ByteArrayPixelBuffer(dstData),
          width = dstW,
          height = dstH,
          stride = dstStride,
          orientation = Orientation.NORMAL,
        )
      }

      Orientation.NORMAL -> ir // Already handled above, but exhaustive when.
    }
  }
}
