package dev.transmute.gstreamer

import dev.transmute.common.PipelineContext
import dev.transmute.image.ImageDecodeOptions
import dev.transmute.image.ImageDecoder
import dev.transmute.image.ImageEncodeOptions
import dev.transmute.image.ImageEncoder
import dev.transmute.image.ImageFormat
import dev.transmute.image.ImageIR
import dev.transmute.model.core.Bytes

// ---------------------------------------------------------------------------
// GStreamer-backed image codecs for iOS.
// Handles HEIF, HEIC, AVIF via GStreamer cinterop -> PNG intermediate ->
// CoreGraphics.
// ---------------------------------------------------------------------------

internal class GstIosImageDecoder : ImageDecoder {
    override val supportedFormats: Set<ImageFormat> = setOf(
        ImageFormat.Heif,
        ImageFormat.Heic,
        ImageFormat.Avif,
    )

    override fun sniff(data: Bytes): ImageFormat? = GStreamerSniff.sniffImage(data)

    override suspend fun decode(
        source: Bytes,
        options: ImageDecodeOptions,
        context: PipelineContext,
    ): ImageIR = GStreamerIosImageEngine.decode(source, options, context)
}

internal class GstIosImageEncoder : ImageEncoder {
    override val supportedFormats: Set<ImageFormat> = setOf(
        ImageFormat.Heif,
        ImageFormat.Heic,
        ImageFormat.Avif,
    )

    override suspend fun encode(
        ir: ImageIR,
        format: ImageFormat,
        options: ImageEncodeOptions,
        context: PipelineContext,
    ): Bytes = GStreamerIosImageEngine.encode(ir, format, options, context)
}
