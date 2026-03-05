package dev.transmute.video

import dev.transmute.codec.pipeline.Decoded
import dev.transmute.codec.pipeline.EncodedBytes
import dev.transmute.codec.pipeline.PipelineHandler
import dev.transmute.io.TSource

object VideoCodecs {
  object Decode {
    val DEFAULT: PipelineHandler<TSource, Decoded<VideoFormat, VideoIR>> = VideoDecodeHandler()
  }

  object Encode {
    val DEFAULT: PipelineHandler<Decoded<VideoFormat, VideoIR>, EncodedBytes<VideoFormat>> = VideoDynamicEncodeHandler()

    fun <OUT : VideoFormat> fixed(output: OUT): PipelineHandler<Decoded<VideoFormat, VideoIR>, EncodedBytes<OUT>> =
      VideoFixedEncodeHandler(output)
  }
}
