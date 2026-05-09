package dev.transmute.gstreamer

import dev.transmute.common.PipelineContext
import dev.transmute.image.ImageDecodeOptions
import dev.transmute.image.ImageDecoder
import dev.transmute.image.ImageEncodeOptions
import dev.transmute.image.ImageEncoder
import dev.transmute.image.ImageFormat
import dev.transmute.image.ImageIR
import dev.transmute.io.TSource
import dev.transmute.model.core.Bytes

// ---
// GStreamer-backed image codecs for Android.
// Handles HEIF, HEIC, AVIF via GStreamer JNI -> PNG intermediate ->
// Android BitmapFactory.
// ---

internal class GstAndroidImageDecoder : ImageDecoder {
  override val supportedFormats: Set<ImageFormat> = setOf(
    ImageFormat.Heif,
    ImageFormat.Heic,
    ImageFormat.Avif,
  )

  override suspend fun decode(source: TSource, options: ImageDecodeOptions, context: PipelineContext): ImageIR {
    val bytes = if (source is Bytes) source else Bytes(source.readAll())
    return GStreamerAndroidImageEngine.decode(bytes, options, context)
  }
}

internal class GstAndroidImageEncoder : ImageEncoder {
  override val supportedFormats: Set<ImageFormat> = setOf(
    ImageFormat.Heif,
    ImageFormat.Heic,
    ImageFormat.Avif,
  )

  override suspend fun encode(ir: ImageIR, format: ImageFormat, options: ImageEncodeOptions, context: PipelineContext): Bytes =
    GStreamerAndroidImageEngine.encode(ir, format, options, context)
}
