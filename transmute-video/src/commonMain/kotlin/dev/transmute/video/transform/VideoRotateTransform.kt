package dev.transmute.video.transform

import dev.transmute.common.PipelineContext
import dev.transmute.codec.pipeline.TransformId
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.video.FrameStream
import dev.transmute.video.VideoFrame
import dev.transmute.video.VideoHint
import dev.transmute.video.VideoIR
import dev.transmute.video.VideoTransform

/**
 * Rotates video frames by 90°, 180°, or 270° clockwise.
 *
 * Uses the same pure pixel-shuffle approach as
 * [ImageRotateTransform][dev.transmute.image.transform.ImageRotateTransform].
 * No interpolation - every source pixel maps exactly to one destination pixel.
 *
 * For 90° and 270° rotations, frame width and height are swapped
 * and the video track dimensions are updated accordingly.
 *
 * @param degrees Rotation angle. Must be 90, 180, or 270.
 */
class VideoRotateTransform(
  val degrees: Int,
) : VideoTransform {

  override fun wouldTransform(hint: VideoHint): Boolean = true // always applies rotation

  init {
    require(degrees in setOf(90, 180, 270)) { "Rotation must be 90, 180, or 270 degrees, got $degrees" }
  }

  override val id = TransformId("video.rotate")

  override suspend fun apply(ir: VideoIR, context: PipelineContext): VideoIR {
    context.logger.info("VideoRotateTransform: rotating ${degrees}° CW")

    val track = ir.videoTrack
    val swapDimensions = degrees == 90 || degrees == 270
    val newWidth = if (swapDimensions) track.height else track.width
    val newHeight = if (swapDimensions) track.width else track.height

    return ir.copy(
      videoTrack = track.copy(
        width = newWidth,
        height = newHeight,
        frames = RotatedFrameStream(track.frames, degrees),
      ),
    )
  }
}

/**
 * Frame stream wrapper that rotates each frame.
 */
private class RotatedFrameStream(
  private val source: FrameStream,
  private val degrees: Int,
) : FrameStream {
  override val frameCount: Long = source.frameCount

  override fun close() = source.close()

  override suspend fun nextFrame(): VideoFrame? {
    val frame = source.nextFrame() ?: return null

    val srcData = (frame.buffer as? ByteArrayPixelBuffer)?.data ?: return frame
    val bpp = frame.pixelFormat.bytesPerPixel
    val srcW = frame.width
    val srcH = frame.height

    return when (degrees) {
      90 -> {
        // 90° CW: (x,y) → (srcH-1-y, x)
        val dstW = srcH
        val dstH = srcW
        val dstData = ByteArray(dstW * dstH * bpp)

        for (y in 0 until srcH) {
          for (x in 0 until srcW) {
            val srcOff = (y * srcW + x) * bpp
            val dx = srcH - 1 - y
            val dy = x
            val dstOff = (dy * dstW + dx) * bpp
            srcData.copyInto(dstData, dstOff, srcOff, srcOff + bpp)
          }
        }

        frame.copy(
          buffer = ByteArrayPixelBuffer(dstData),
          width = dstW,
          height = dstH,
        )
      }

      180 -> {
        // 180°: (x,y) → (srcW-1-x, srcH-1-y)
        val dstData = ByteArray(srcW * srcH * bpp)

        for (y in 0 until srcH) {
          for (x in 0 until srcW) {
            val srcOff = (y * srcW + x) * bpp
            val dx = srcW - 1 - x
            val dy = srcH - 1 - y
            val dstOff = (dy * srcW + dx) * bpp
            srcData.copyInto(dstData, dstOff, srcOff, srcOff + bpp)
          }
        }

        frame.copy(buffer = ByteArrayPixelBuffer(dstData))
      }

      270 -> {
        // 270° CW (= 90° CCW): (x,y) → (y, srcW-1-x)
        val dstW = srcH
        val dstH = srcW
        val dstData = ByteArray(dstW * dstH * bpp)

        for (y in 0 until srcH) {
          for (x in 0 until srcW) {
            val srcOff = (y * srcW + x) * bpp
            val dx = y
            val dy = srcW - 1 - x
            val dstOff = (dy * dstW + dx) * bpp
            srcData.copyInto(dstData, dstOff, srcOff, srcOff + bpp)
          }
        }

        frame.copy(
          buffer = ByteArrayPixelBuffer(dstData),
          width = dstW,
          height = dstH,
        )
      }

      else -> frame
    }
  }
}
