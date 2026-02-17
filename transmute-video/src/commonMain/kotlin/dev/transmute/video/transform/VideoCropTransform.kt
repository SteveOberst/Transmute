package dev.transmute.video.transform

import dev.transmute.video.VideoIR
import dev.transmute.video.VideoFrame
import dev.transmute.video.FrameStream
import dev.transmute.core.ConversionContext
import dev.transmute.core.pipeline.Transform
import dev.transmute.core.pipeline.TransformId
import dev.transmute.image.ByteArrayPixelBuffer

/**
 * Crops video frames to a sub-region.
 *
 * Operates on each frame's [ByteArrayPixelBuffer] - the same row-copy
 * strategy used by [ImageCropTransform][dev.transmute.image.transform.ImageCropTransform].
 * Coordinates are clamped to frame bounds.
 *
 * @param x Left edge of the crop region.
 * @param y Top edge of the crop region.
 * @param cropWidth Width of the crop region.
 * @param cropHeight Height of the crop region.
 */
class VideoCropTransform(
  private val x: Int,
  private val y: Int,
  private val cropWidth: Int,
  private val cropHeight: Int,
) : Transform<VideoIR> {

  override val id = TransformId("video.crop")

  override suspend fun apply(ir: VideoIR, context: ConversionContext): VideoIR {
    val track = ir.videoTrack

    // Clamp crop rect to frame bounds.
    val cx = x.coerceIn(0, track.width)
    val cy = y.coerceIn(0, track.height)
    val cw = cropWidth.coerceIn(0, track.width - cx)
    val ch = cropHeight.coerceIn(0, track.height - cy)

    if (cw == 0 || ch == 0) {
      context.logger.warn("VideoCropTransform: crop region is empty - returning original")
      return ir
    }

    if (cx == 0 && cy == 0 && cw == track.width && ch == track.height) {
      context.logger.debug("VideoCropTransform: crop region equals full frame - skipping")
      return ir
    }

    context.logger.info("VideoCropTransform: cropping to ($cx,$cy) ${cw}×${ch}")

    return ir.copy(
      videoTrack = track.copy(
        width = cw,
        height = ch,
        frames = CroppedFrameStream(track.frames, cx, cy, cw, ch),
      ),
    )
  }
}

/**
 * Frame stream that crops each frame to the given sub-region.
 */
private class CroppedFrameStream(
  private val source: FrameStream,
  private val cx: Int,
  private val cy: Int,
  private val cw: Int,
  private val ch: Int,
) : FrameStream {
  override val frameCount: Long = source.frameCount

  override suspend fun nextFrame(): VideoFrame? {
    val frame = source.nextFrame() ?: return null

    val srcData = (frame.buffer as? ByteArrayPixelBuffer)?.data ?: return frame
    val bpp = frame.pixelFormat.bytesPerPixel
    val srcStride = frame.width * bpp
    val dstStride = cw * bpp
    val dstData = ByteArray(ch * dstStride)

    for (row in 0 until ch) {
      val srcOffset = (cy + row) * srcStride + cx * bpp
      val dstOffset = row * dstStride
      if (srcOffset + dstStride <= srcData.size) {
        srcData.copyInto(dstData, dstOffset, srcOffset, srcOffset + dstStride)
      }
    }

    return frame.copy(
      buffer = ByteArrayPixelBuffer(dstData),
      width = cw,
      height = ch,
    )
  }
}
