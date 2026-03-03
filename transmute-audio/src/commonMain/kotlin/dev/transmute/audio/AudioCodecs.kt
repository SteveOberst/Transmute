package dev.transmute.audio

import dev.transmute.io.TSource
import dev.transmute.codec.pipeline.Decoded
import dev.transmute.codec.pipeline.EncodedBytes
import dev.transmute.codec.pipeline.PipelineHandler

object AudioCodecs {
  object Decode {
    val DEFAULT: PipelineHandler<TSource, Decoded<AudioFormat, AudioIR>> = AudioDecodeHandler()
  }

  object Encode {
    val DEFAULT: PipelineHandler<Decoded<AudioFormat, AudioIR>, EncodedBytes<AudioFormat>> = AudioDynamicEncodeHandler()

    fun <OUT : AudioFormat> fixed(output: OUT): PipelineHandler<Decoded<AudioFormat, AudioIR>, EncodedBytes<OUT>> =
      AudioFixedEncodeHandler(output)
  }
}

