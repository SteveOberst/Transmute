package dev.transmute.audio

import dev.transmute.core.Bytes
import dev.transmute.core.pipeline.Decoded
import dev.transmute.core.pipeline.EncodedBytes
import dev.transmute.core.pipeline.PipelineHandler

object AudioCodecs {
  object Decode {
    val DEFAULT: PipelineHandler<Bytes, Decoded<AudioFormat, AudioIR>> = AudioDecodeHandler()
  }

  object Encode {
    val DEFAULT: PipelineHandler<Decoded<AudioFormat, AudioIR>, EncodedBytes<AudioFormat>> = AudioDynamicEncodeHandler()

    fun <OUT : AudioFormat> fixed(output: OUT): PipelineHandler<Decoded<AudioFormat, AudioIR>, EncodedBytes<OUT>> =
      AudioFixedEncodeHandler(output)
  }
}

