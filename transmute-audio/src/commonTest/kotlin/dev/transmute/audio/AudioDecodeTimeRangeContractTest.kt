package dev.transmute.audio

import dev.transmute.codec.TimeRangeMs
import dev.transmute.common.PipelineContext
import dev.transmute.io.TSource
import dev.transmute.model.core.asBytes
import kotlin.test.Test
import kotlin.test.assertFailsWith

class AudioDecodeTimeRangeContractTest {

  @Test
  fun decodeHandlerPropagatesUnsupportedTimeRange() = kotlinx.coroutines.test.runTest {
    val fakeDecoder = object : AudioDecoder {
      override val supportedFormats: Set<AudioFormat> = setOf(AudioFormat.Wav)
      override suspend fun decode(source: TSource, options: AudioDecodeOptions, context: PipelineContext): AudioIR {
        if (options.decodeRange != null) throw UnsupportedOperationException("decodeRange not supported")
        return AudioTestHelpers.silence()
      }
    }
    val registry = object : AudioDecoderRegistry {
      override fun decoderFor(format: AudioFormat): AudioDecoder? = if (format == AudioFormat.Wav) fakeDecoder else null
    }

    val handler = AudioDecodeHandler(decoders = registry)
    val ctx = PipelineContext(
      decodeOptions = CanonicalAudioDecodeOptions(
        acceptedInputFormats = setOf(AudioFormat.Wav),
        decodeRange = TimeRangeMs(0, 1000),
      ),
    )

    assertFailsWith<UnsupportedOperationException> {
      handler.handle(byteArrayOf(0).asBytes(), ctx)
    }
  }
}
