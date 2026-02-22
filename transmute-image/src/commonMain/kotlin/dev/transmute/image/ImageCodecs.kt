package dev.transmute.image

import dev.transmute.core.Bytes
import dev.transmute.core.pipeline.Decoded
import dev.transmute.core.pipeline.EncodedBytes
import dev.transmute.core.pipeline.PipelineHandler

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
    /** Default dynamic-output encoder (resolves [dev.transmute.core.OutputFormat] from encode options). */
    val DEFAULT: PipelineHandler<Decoded<ImageFormat, ImageIR>, EncodedBytes<ImageFormat>> = ImageDynamicEncodeHandler()

    fun <OUT : ImageFormat> fixed(output: OUT): PipelineHandler<Decoded<ImageFormat, ImageIR>, EncodedBytes<OUT>> =
      ImageFixedEncodeHandler(output)
  }
}

