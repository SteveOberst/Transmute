package dev.transmute

import dev.transmute.audio.AudioFormat
import dev.transmute.model.core.Bytes
import dev.transmute.codec.pipeline.EncodedBytes
import dev.transmute.image.ImageFormat
import dev.transmute.video.VideoFormat

class TransmuteImage internal constructor(
  private val codec: TransmuteCodec,
) {

  operator fun invoke(
    block: DynamicImageTransmuterBuilder<Bytes, EncodedBytes<ImageFormat>>.() -> Unit = {},
  ): DynamicImageTransmuter =
    DynamicImageTransmuterBuilder(
      defaultDecodePipeline = { codec.image.defaultDecoder().pipeline },
      defaultEncodePipeline = { codec.image.defaultEncoder().pipeline },
    ).apply(block).build()

  /** Power-user factory methods for custom input / output types. */
  val custom: Custom = Custom()

  fun <OUT_FORMAT : ImageFormat> to(
    output: OUT_FORMAT,
    block: ImageTransmuterBuilder<Bytes, OUT_FORMAT>.() -> Unit = {},
  ): ImageTransmuter<Bytes, EncodedBytes<OUT_FORMAT>> =
    ImageTransmuterBuilder(
      output,
      defaultDecodePipeline = { codec.image.defaultDecoder().pipeline },
    ).apply(block).build()

  @Deprecated("Use custom.out { } instead", ReplaceWith("custom.out(block)"))
  fun <OUT> out(block: DynamicImageTransmuterBuilder<Bytes, OUT>.() -> Unit): ImageTransmuter<Bytes, OUT> =
    custom.out(block)

  @Deprecated("Use custom.from { } instead", ReplaceWith("custom.from(block)"))
  fun <IN> from(
    block: DynamicImageTransmuterBuilder<IN, EncodedBytes<ImageFormat>>.() -> Unit = {},
  ): ImageTransmuter<IN, EncodedBytes<ImageFormat>> =
    custom.from(block)

  @Deprecated("Use custom.fromOut { } instead", ReplaceWith("custom.fromOut(block)"))
  fun <IN, OUT> fromOut(block: DynamicImageTransmuterBuilder<IN, OUT>.() -> Unit): ImageTransmuter<IN, OUT> =
    custom.fromOut(block)

  @Deprecated("Use custom.toFrom(...) instead", ReplaceWith("custom.toFrom(output, block)"))
  fun <IN, OUT_FORMAT : ImageFormat> toFrom(
    output: OUT_FORMAT,
    block: ImageTransmuterBuilder<IN, OUT_FORMAT>.() -> Unit = {},
  ): ImageTransmuter<IN, EncodedBytes<OUT_FORMAT>> =
    custom.toFrom(output, block)

  inner class Custom internal constructor() {
    fun <OUT> out(block: DynamicImageTransmuterBuilder<Bytes, OUT>.() -> Unit): ImageTransmuter<Bytes, OUT> =
      DynamicImageTransmuterBuilder<Bytes, OUT>(
        defaultDecodePipeline = { codec.image.defaultDecoder().pipeline },
      ).apply(block).build()

    fun <IN> from(
      block: DynamicImageTransmuterBuilder<IN, EncodedBytes<ImageFormat>>.() -> Unit = {},
    ): ImageTransmuter<IN, EncodedBytes<ImageFormat>> =
      DynamicImageTransmuterBuilder<IN, EncodedBytes<ImageFormat>>(
        defaultEncodePipeline = { codec.image.defaultEncoder().pipeline },
      ).apply(block).build()

    fun <IN, OUT> fromOut(block: DynamicImageTransmuterBuilder<IN, OUT>.() -> Unit): ImageTransmuter<IN, OUT> =
      DynamicImageTransmuterBuilder<IN, OUT>().apply(block).build()

    fun <IN, OUT_FORMAT : ImageFormat> toFrom(
      output: OUT_FORMAT,
      block: ImageTransmuterBuilder<IN, OUT_FORMAT>.() -> Unit = {},
    ): ImageTransmuter<IN, EncodedBytes<OUT_FORMAT>> =
      ImageTransmuterBuilder<IN, OUT_FORMAT>(output).apply(block).build()
  }
}

class TransmuteAudio internal constructor(
  private val codec: TransmuteCodec,
) {

  operator fun invoke(
    block: DynamicAudioTransmuterBuilder<Bytes, EncodedBytes<AudioFormat>>.() -> Unit = {},
  ): DynamicAudioTransmuter =
    DynamicAudioTransmuterBuilder(
      defaultDecodePipeline = { codec.audio.defaultDecoder().pipeline },
      defaultEncodePipeline = { codec.audio.defaultEncoder().pipeline },
    ).apply(block).build()

  /** Power-user factory methods for custom input / output types. */
  val custom: Custom = Custom()

  fun <OUT_FORMAT : AudioFormat> to(
    output: OUT_FORMAT,
    block: AudioTransmuterBuilder<Bytes, OUT_FORMAT>.() -> Unit = {},
  ): AudioTransmuter<Bytes, EncodedBytes<OUT_FORMAT>> =
    AudioTransmuterBuilder(
      output,
      defaultDecodePipeline = { codec.audio.defaultDecoder().pipeline },
    ).apply(block).build()

  @Deprecated("Use custom.out { } instead", ReplaceWith("custom.out(block)"))
  fun <OUT> out(block: DynamicAudioTransmuterBuilder<Bytes, OUT>.() -> Unit): AudioTransmuter<Bytes, OUT> =
    custom.out(block)

  @Deprecated("Use custom.from { } instead", ReplaceWith("custom.from(block)"))
  fun <IN> from(
    block: DynamicAudioTransmuterBuilder<IN, EncodedBytes<AudioFormat>>.() -> Unit = {},
  ): AudioTransmuter<IN, EncodedBytes<AudioFormat>> =
    custom.from(block)

  @Deprecated("Use custom.fromOut { } instead", ReplaceWith("custom.fromOut(block)"))
  fun <IN, OUT> fromOut(block: DynamicAudioTransmuterBuilder<IN, OUT>.() -> Unit): AudioTransmuter<IN, OUT> =
    custom.fromOut(block)

  @Deprecated("Use custom.toFrom(...) instead", ReplaceWith("custom.toFrom(output, block)"))
  fun <IN, OUT_FORMAT : AudioFormat> toFrom(
    output: OUT_FORMAT,
    block: AudioTransmuterBuilder<IN, OUT_FORMAT>.() -> Unit = {},
  ): AudioTransmuter<IN, EncodedBytes<OUT_FORMAT>> =
    custom.toFrom(output, block)

  inner class Custom internal constructor() {
    fun <OUT> out(block: DynamicAudioTransmuterBuilder<Bytes, OUT>.() -> Unit): AudioTransmuter<Bytes, OUT> =
      DynamicAudioTransmuterBuilder<Bytes, OUT>(
        defaultDecodePipeline = { codec.audio.defaultDecoder().pipeline },
      ).apply(block).build()

    fun <IN> from(
      block: DynamicAudioTransmuterBuilder<IN, EncodedBytes<AudioFormat>>.() -> Unit = {},
    ): AudioTransmuter<IN, EncodedBytes<AudioFormat>> =
      DynamicAudioTransmuterBuilder<IN, EncodedBytes<AudioFormat>>(
        defaultEncodePipeline = { codec.audio.defaultEncoder().pipeline },
      ).apply(block).build()

    fun <IN, OUT> fromOut(block: DynamicAudioTransmuterBuilder<IN, OUT>.() -> Unit): AudioTransmuter<IN, OUT> =
      DynamicAudioTransmuterBuilder<IN, OUT>().apply(block).build()

    fun <IN, OUT_FORMAT : AudioFormat> toFrom(
      output: OUT_FORMAT,
      block: AudioTransmuterBuilder<IN, OUT_FORMAT>.() -> Unit = {},
    ): AudioTransmuter<IN, EncodedBytes<OUT_FORMAT>> =
      AudioTransmuterBuilder<IN, OUT_FORMAT>(output).apply(block).build()
  }
}

class TransmuteVideo internal constructor(
  private val codec: TransmuteCodec,
) {

  operator fun invoke(
    block: DynamicVideoTransmuterBuilder<Bytes, EncodedBytes<VideoFormat>>.() -> Unit = {},
  ): DynamicVideoTransmuter =
    DynamicVideoTransmuterBuilder(
      defaultDecodePipeline = { codec.video.defaultDecoder().pipeline },
      defaultEncodePipeline = { codec.video.defaultEncoder().pipeline },
    ).apply(block).build()

  /** Power-user factory methods for custom input / output types. */
  val custom: Custom = Custom()

  fun <OUT_FORMAT : VideoFormat> to(
    output: OUT_FORMAT,
    block: VideoTransmuterBuilder<Bytes, OUT_FORMAT>.() -> Unit = {},
  ): VideoTransmuter<Bytes, EncodedBytes<OUT_FORMAT>> =
    VideoTransmuterBuilder(
      output,
      defaultDecodePipeline = { codec.video.defaultDecoder().pipeline },
    ).apply(block).build()

  @Deprecated("Use custom.out { } instead", ReplaceWith("custom.out(block)"))
  fun <OUT> out(block: DynamicVideoTransmuterBuilder<Bytes, OUT>.() -> Unit): VideoTransmuter<Bytes, OUT> =
    custom.out(block)

  @Deprecated("Use custom.from { } instead", ReplaceWith("custom.from(block)"))
  fun <IN> from(
    block: DynamicVideoTransmuterBuilder<IN, EncodedBytes<VideoFormat>>.() -> Unit = {},
  ): VideoTransmuter<IN, EncodedBytes<VideoFormat>> =
    custom.from(block)

  @Deprecated("Use custom.fromOut { } instead", ReplaceWith("custom.fromOut(block)"))
  fun <IN, OUT> fromOut(block: DynamicVideoTransmuterBuilder<IN, OUT>.() -> Unit): VideoTransmuter<IN, OUT> =
    custom.fromOut(block)

  @Deprecated("Use custom.toFrom(...) instead", ReplaceWith("custom.toFrom(output, block)"))
  fun <IN, OUT_FORMAT : VideoFormat> toFrom(
    output: OUT_FORMAT,
    block: VideoTransmuterBuilder<IN, OUT_FORMAT>.() -> Unit = {},
  ): VideoTransmuter<IN, EncodedBytes<OUT_FORMAT>> =
    custom.toFrom(output, block)

  inner class Custom internal constructor() {
    fun <OUT> out(block: DynamicVideoTransmuterBuilder<Bytes, OUT>.() -> Unit): VideoTransmuter<Bytes, OUT> =
      DynamicVideoTransmuterBuilder<Bytes, OUT>(
        defaultDecodePipeline = { codec.video.defaultDecoder().pipeline },
      ).apply(block).build()

    fun <IN> from(
      block: DynamicVideoTransmuterBuilder<IN, EncodedBytes<VideoFormat>>.() -> Unit = {},
    ): VideoTransmuter<IN, EncodedBytes<VideoFormat>> =
      DynamicVideoTransmuterBuilder<IN, EncodedBytes<VideoFormat>>(
        defaultEncodePipeline = { codec.video.defaultEncoder().pipeline },
      ).apply(block).build()

    fun <IN, OUT> fromOut(block: DynamicVideoTransmuterBuilder<IN, OUT>.() -> Unit): VideoTransmuter<IN, OUT> =
      DynamicVideoTransmuterBuilder<IN, OUT>().apply(block).build()

    fun <IN, OUT_FORMAT : VideoFormat> toFrom(
      output: OUT_FORMAT,
      block: VideoTransmuterBuilder<IN, OUT_FORMAT>.() -> Unit = {},
    ): VideoTransmuter<IN, EncodedBytes<OUT_FORMAT>> =
      VideoTransmuterBuilder<IN, OUT_FORMAT>(output).apply(block).build()
  }
}
