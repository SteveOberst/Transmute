package dev.transmute.gstreamer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.transmute.common.PipelineContext
import dev.transmute.image.AlphaSemantics
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.ColorInfo
import dev.transmute.image.ImageDecodeOptions
import dev.transmute.image.ImageEncodeOptions
import dev.transmute.image.ImageFormat
import dev.transmute.image.ImageFormatDetector
import dev.transmute.image.ImageIR
import dev.transmute.image.ImageMetadata
import dev.transmute.image.Orientation
import dev.transmute.image.PixelFormat
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.asBytes
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Image encode / decode engine for Android via the GStreamer JNI bridge.
 *
 * Uses the same temp-file + pipeline-descriptor pattern as the Desktop
 * engine, but with Android's [BitmapFactory] / [Bitmap] for the PNG
 * intermediate step (instead of `java.awt.image.BufferedImage`).
 */
internal object GStreamerAndroidImageEngine {

  val available: Boolean get() = GStreamerJni.available

  // -- Decode ---

  /**
   * Decode HEIF/HEIC/AVIF to [ImageIR] via GStreamer -> PNG -> BitmapFactory.
   */
  suspend fun decode(source: Bytes, options: ImageDecodeOptions, context: PipelineContext): ImageIR = withContext(Dispatchers.IO) {
    check(available) { "GStreamer is not available on this device" }

    val format = ImageFormatDetector.detect(source)
    val ext = when (format) {
      ImageFormat.Heif -> "heif"
      ImageFormat.Heic -> "heic"
      ImageFormat.Avif -> "avif"
      else -> "bin"
    }

    val tmpIn = File.createTempFile("transmute_gst_img_in_", ".$ext")
    val tmpOut = File.createTempFile("transmute_gst_img_out_", ".png")
    try {
      tmpIn.writeBytes(source.data)
      val desc = buildPipelineDesc(
        "filesrc location=${tmpIn.absolutePath}",
        "! decodebin",
        "! videoconvert",
        "! pngenc",
        "! filesink location=${tmpOut.absolutePath}",
      )
      GStreamerJni.runPipeline(desc)

      val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
      val bitmap = BitmapFactory.decodeFile(tmpOut.absolutePath, opts)
        ?: error("BitmapFactory failed to decode GStreamer-produced PNG")

      val w = bitmap.width
      val h = bitmap.height
      val pixels = IntArray(w * h)
      bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

      // Convert ARGB_8888 (Android) -> RGBA_8888
      val rgba = ByteArray(w * h * 4)
      for (i in pixels.indices) {
        val argb = pixels[i]
        val off = i * 4
        rgba[off] = ((argb shr 16) and 0xFF).toByte() // R
        rgba[off + 1] = ((argb shr 8) and 0xFF).toByte() // G
        rgba[off + 2] = ((argb) and 0xFF).toByte() // B
        rgba[off + 3] = ((argb shr 24) and 0xFF).toByte() // A
      }
      bitmap.recycle()

      ImageIR(
        buffer = ByteArrayPixelBuffer(rgba),
        width = w,
        height = h,
        stride = w * 4,
        pixelFormat = PixelFormat.RGBA_8888,
        alphaSemantics = AlphaSemantics.STRAIGHT,
        colorInfo = ColorInfo(),
        orientation = Orientation.NORMAL,
        metadata = ImageMetadata(),
      )
    } finally {
      tmpIn.delete()
      tmpOut.delete()
    }
  }

  // -- Encode ---

  /**
   * Encode [ImageIR] to HEIF/HEIC/AVIF via PNG -> GStreamer pipeline.
   */
  suspend fun encode(ir: ImageIR, format: ImageFormat, options: ImageEncodeOptions, context: PipelineContext): Bytes =
    withContext(Dispatchers.IO) {
      check(available) { "GStreamer is not available on this device" }

      val buffer = ir.buffer as? ByteArrayPixelBuffer
        ?: error("GStreamerAndroidImageEngine requires ByteArrayPixelBuffer")
      require(ir.pixelFormat == PixelFormat.RGBA_8888) { "Only RGBA_8888 is supported" }

      val w = ir.width
      val h = ir.height
      val tmpIn = File.createTempFile("transmute_gst_img_enc_in_", ".png")
      val ext = when (format) {
        ImageFormat.Avif -> "avif"
        else -> "heif"
      }
      val tmpOut = File.createTempFile("transmute_gst_img_enc_out_", ".$ext")

      try {
        // Convert RGBA_8888 -> ARGB_8888 (Android int format)
        val rgba = buffer.data
        val pixels = IntArray(w * h)
        for (i in 0 until w * h) {
          val off = i * 4
          val r = rgba[off].toInt() and 0xFF
          val g = rgba[off + 1].toInt() and 0xFF
          val b = rgba[off + 2].toInt() and 0xFF
          val a = rgba[off + 3].toInt() and 0xFF
          pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        tmpIn.outputStream().use { os ->
          bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
        }
        bitmap.recycle()

        val encoderPipeline = when (format) {
          ImageFormat.Avif -> "! pngdec ! videoconvert ! av1enc ! isofmp4mux"
          else -> "! pngdec ! videoconvert ! x265enc ! h265parse ! mp4mux"
        }

        val desc = buildPipelineDesc(
          "filesrc location=${tmpIn.absolutePath}",
          encoderPipeline,
          "! filesink location=${tmpOut.absolutePath}",
        )
        GStreamerJni.runPipeline(desc)
        tmpOut.readBytes().asBytes()
      } finally {
        tmpIn.delete()
        tmpOut.delete()
      }
    }
}
