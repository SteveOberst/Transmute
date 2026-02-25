package dev.transmute.video

import dev.transmute.codec.TimeRangeMs
import dev.transmute.common.PipelineContext
import dev.transmute.model.core.asBytes
import kotlin.test.Test
import kotlin.test.assertFailsWith

class VideoDecodeTimeRangeContractTest {

  @Test
  fun decodeHandlerPropagatesUnsupportedTimeRange() = kotlinx.coroutines.test.runTest {
    val fakeDecoder = object : VideoDecoder {
      override val supportedFormats: Set<VideoFormat> = setOf(VideoFormat.Mp4)
      override suspend fun decode(source: dev.transmute.model.core.Bytes, options: VideoDecodeOptions, context: PipelineContext): VideoIR {
        if (options.decodeRange != null) throw UnsupportedOperationException("decodeRange not supported")
        return VideoTestHelpers.syntheticVideo()
      }
    }
    val registry = object : VideoDecoderRegistry {
      override fun decoderFor(format: VideoFormat): VideoDecoder? = if (format == VideoFormat.Mp4) fakeDecoder else null
    }

    val handler = VideoDecodeHandler(decoders = registry)
    val ctx = PipelineContext(
      decodeOptions = CanonicalVideoDecodeOptions(
        acceptedInputFormats = setOf(VideoFormat.Mp4),
        decodeRange = TimeRangeMs(0, 1000),
      ),
    )

    assertFailsWith<UnsupportedOperationException> {
      handler.handle(byteArrayOf(0).asBytes(), ctx)
    }
  }
}
