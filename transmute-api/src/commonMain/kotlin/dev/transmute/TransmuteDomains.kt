package dev.transmute

import dev.transmute.audio.AudioFormat
import dev.transmute.model.core.Bytes
import dev.transmute.codec.pipeline.EncodedBytes
import dev.transmute.image.ImageFormat
import dev.transmute.video.VideoFormat

class TransmuteImage internal constructor() {

  operator fun invoke(
    block: DynamicImageTransmuterBuilder<Bytes, EncodedBytes<ImageFormat>>.() -> Unit = {},
  ): DynamicImageTransmuter =
    DynamicImageTransmuterBuilder(
      defaultDecodePipeline = { Transmute.codec().image.defaultDecoder().pipeline },
      defaultEncodePipeline = { Transmute.codec().image.defaultEncoder().pipeline },
    ).apply(block).build()

  fun <OUT> out(block: DynamicImageTransmuterBuilder<Bytes, OUT>.() -> Unit): ImageTransmuter<Bytes, OUT> =
    DynamicImageTransmuterBuilder<Bytes, OUT>(
      defaultDecodePipeline = { Transmute.codec().image.defaultDecoder().pipeline },
    ).apply(block).build()

  fun <IN> from(
    block: DynamicImageTransmuterBuilder<IN, EncodedBytes<ImageFormat>>.() -> Unit = {},
  ): ImageTransmuter<IN, EncodedBytes<ImageFormat>> =
    DynamicImageTransmuterBuilder<IN, EncodedBytes<ImageFormat>>(
      defaultEncodePipeline = { Transmute.codec().image.defaultEncoder().pipeline },
    )
      .apply(block)
      .build()

  fun <IN, OUT> fromOut(block: DynamicImageTransmuterBuilder<IN, OUT>.() -> Unit): ImageTransmuter<IN, OUT> =
    DynamicImageTransmuterBuilder<IN, OUT>().apply(block).build()

  fun <OUT_FORMAT : ImageFormat> to(
    output: OUT_FORMAT,
    block: ImageTransmuterBuilder<Bytes, OUT_FORMAT>.() -> Unit = {},
  ): ImageTransmuter<Bytes, EncodedBytes<OUT_FORMAT>> =
    ImageTransmuterBuilder(
      output,
      defaultDecodePipeline = { Transmute.codec().image.defaultDecoder().pipeline },
    ).apply(block).build()

  fun <IN, OUT_FORMAT : ImageFormat> toFrom(
    output: OUT_FORMAT,
    block: ImageTransmuterBuilder<IN, OUT_FORMAT>.() -> Unit = {},
  ): ImageTransmuter<IN, EncodedBytes<OUT_FORMAT>> =
    ImageTransmuterBuilder<IN, OUT_FORMAT>(output).apply(block).build()
}

class TransmuteAudio internal constructor() {

  operator fun invoke(
    block: DynamicAudioTransmuterBuilder<Bytes, EncodedBytes<AudioFormat>>.() -> Unit = {},
  ): DynamicAudioTransmuter =
    DynamicAudioTransmuterBuilder(
      defaultDecodePipeline = { Transmute.codec().audio.defaultDecoder().pipeline },
      defaultEncodePipeline = { Transmute.codec().audio.defaultEncoder().pipeline },
    ).apply(block).build()

  fun <OUT> out(block: DynamicAudioTransmuterBuilder<Bytes, OUT>.() -> Unit): AudioTransmuter<Bytes, OUT> =
    DynamicAudioTransmuterBuilder<Bytes, OUT>(
      defaultDecodePipeline = { Transmute.codec().audio.defaultDecoder().pipeline },
    ).apply(block).build()

  fun <IN> from(
    block: DynamicAudioTransmuterBuilder<IN, EncodedBytes<AudioFormat>>.() -> Unit = {},
  ): AudioTransmuter<IN, EncodedBytes<AudioFormat>> =
    DynamicAudioTransmuterBuilder<IN, EncodedBytes<AudioFormat>>(
      defaultEncodePipeline = { Transmute.codec().audio.defaultEncoder().pipeline },
    )
      .apply(block)
      .build()

  fun <IN, OUT> fromOut(block: DynamicAudioTransmuterBuilder<IN, OUT>.() -> Unit): AudioTransmuter<IN, OUT> =
    DynamicAudioTransmuterBuilder<IN, OUT>().apply(block).build()

  fun <OUT_FORMAT : AudioFormat> to(
    output: OUT_FORMAT,
    block: AudioTransmuterBuilder<Bytes, OUT_FORMAT>.() -> Unit = {},
  ): AudioTransmuter<Bytes, EncodedBytes<OUT_FORMAT>> =
    AudioTransmuterBuilder(
      output,
      defaultDecodePipeline = { Transmute.codec().audio.defaultDecoder().pipeline },
    ).apply(block).build()

  fun <IN, OUT_FORMAT : AudioFormat> toFrom(
    output: OUT_FORMAT,
    block: AudioTransmuterBuilder<IN, OUT_FORMAT>.() -> Unit = {},
  ): AudioTransmuter<IN, EncodedBytes<OUT_FORMAT>> =
    AudioTransmuterBuilder<IN, OUT_FORMAT>(output).apply(block).build()
}

class TransmuteVideo internal constructor() {

  operator fun invoke(
    block: DynamicVideoTransmuterBuilder<Bytes, EncodedBytes<VideoFormat>>.() -> Unit = {},
  ): DynamicVideoTransmuter =
    DynamicVideoTransmuterBuilder(
      defaultDecodePipeline = { Transmute.codec().video.defaultDecoder().pipeline },
      defaultEncodePipeline = { Transmute.codec().video.defaultEncoder().pipeline },
    ).apply(block).build()

  fun <OUT> out(block: DynamicVideoTransmuterBuilder<Bytes, OUT>.() -> Unit): VideoTransmuter<Bytes, OUT> =
    DynamicVideoTransmuterBuilder<Bytes, OUT>(
      defaultDecodePipeline = { Transmute.codec().video.defaultDecoder().pipeline },
    ).apply(block).build()

  fun <IN> from(
    block: DynamicVideoTransmuterBuilder<IN, EncodedBytes<VideoFormat>>.() -> Unit = {},
  ): VideoTransmuter<IN, EncodedBytes<VideoFormat>> =
    DynamicVideoTransmuterBuilder<IN, EncodedBytes<VideoFormat>>(
      defaultEncodePipeline = { Transmute.codec().video.defaultEncoder().pipeline },
    )
      .apply(block)
      .build()

  fun <IN, OUT> fromOut(block: DynamicVideoTransmuterBuilder<IN, OUT>.() -> Unit): VideoTransmuter<IN, OUT> =
    DynamicVideoTransmuterBuilder<IN, OUT>().apply(block).build()

  fun <OUT_FORMAT : VideoFormat> to(
    output: OUT_FORMAT,
    block: VideoTransmuterBuilder<Bytes, OUT_FORMAT>.() -> Unit = {},
  ): VideoTransmuter<Bytes, EncodedBytes<OUT_FORMAT>> =
    VideoTransmuterBuilder(
      output,
      defaultDecodePipeline = { Transmute.codec().video.defaultDecoder().pipeline },
    ).apply(block).build()

  fun <IN, OUT_FORMAT : VideoFormat> toFrom(
    output: OUT_FORMAT,
    block: VideoTransmuterBuilder<IN, OUT_FORMAT>.() -> Unit = {},
  ): VideoTransmuter<IN, EncodedBytes<OUT_FORMAT>> =
    VideoTransmuterBuilder<IN, OUT_FORMAT>(output).apply(block).build()
}
