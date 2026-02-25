package dev.transmute.image

import dev.transmute.model.core.Bytes
import dev.transmute.codec.pipeline.Decoded
import dev.transmute.codec.pipeline.EncodedBytes
import dev.transmute.codec.pipeline.PipelineHandler

/**
 * Convenience entry points for the default decode/encode handlers.
 *
 * These are regular [PipelineHandler] instances intended to be used directly in fluent pipelines:
 *
 * ```kotlin
 * decode { startWith(ImageCodecs.Decode.DEFAULT) }
 * encode { startWith(ImageCodecs.Encode.DEFAULT) }
 * ```
 */
object ImageCodecs {
  object Decode {
    val DEFAULT: PipelineHandler<Bytes, Decoded<ImageFormat, ImageIR>> = ImageDecodeHandler()
  }

  object Encode {
    /** Default dynamic-output encoder (resolves [dev.transmute.codec.OutputFormat] from encode options). */
    val DEFAULT: PipelineHandler<Decoded<ImageFormat, ImageIR>, EncodedBytes<ImageFormat>> = ImageDynamicEncodeHandler()

    fun <OUT : ImageFormat> fixed(output: OUT): PipelineHandler<Decoded<ImageFormat, ImageIR>, EncodedBytes<OUT>> =
      ImageFixedEncodeHandler(output)
  }
}

