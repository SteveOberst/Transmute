package dev.transmute

import dev.transmute.model.core.DecodeOptions
import dev.transmute.model.core.EncodeOptions
import dev.transmute.codec.pipeline.DecodePipeline
import dev.transmute.codec.pipeline.EncodePipeline
import dev.transmute.codec.pipeline.PipelineBuilder
import dev.transmute.codec.pipeline.PipelineHandler

class DecodeStage<IN, OUT, OPTS : DecodeOptions>(
  defaultOptions: OPTS,
) {
  var options: OPTS = defaultOptions
  var pipeline: DecodePipeline<IN, OUT>? = null

  fun options(options: OPTS): DecodeStage<IN, OUT, OPTS> = apply { this.options = options }

  fun pipeline(pipeline: DecodePipeline<IN, OUT>): DecodeStage<IN, OUT, OPTS> = apply { this.pipeline = pipeline }

  fun pipeline(initial: PipelineHandler<IN, OUT>): DecodeStage<IN, OUT, OPTS> = apply {
    pipeline = PipelineBuilder.start<IN>().startWith(initial).build()
  }

  fun <CUR> pipeline(
    initial: PipelineHandler<IN, CUR>,
    block: PipelineBuilder<IN, CUR>.() -> PipelineBuilder<IN, OUT>,
  ): DecodeStage<IN, OUT, OPTS> = apply {
    pipeline = PipelineBuilder.start<IN>().startWith(initial).block().build()
  }
}

class EncodeStage<IN, OUT, OPTS : EncodeOptions>(
  defaultOptions: OPTS,
) {
  var options: OPTS = defaultOptions
  var pipeline: EncodePipeline<IN, OUT>? = null

  fun options(options: OPTS): EncodeStage<IN, OUT, OPTS> = apply { this.options = options }

  fun pipeline(pipeline: EncodePipeline<IN, OUT>): EncodeStage<IN, OUT, OPTS> = apply { this.pipeline = pipeline }

  fun pipeline(initial: PipelineHandler<IN, OUT>): EncodeStage<IN, OUT, OPTS> = apply {
    pipeline = PipelineBuilder.start<IN>().startWith(initial).build()
  }

  fun <CUR> pipeline(
    initial: PipelineHandler<IN, CUR>,
    block: PipelineBuilder<IN, CUR>.() -> PipelineBuilder<IN, OUT>,
  ): EncodeStage<IN, OUT, OPTS> = apply {
    pipeline = PipelineBuilder.start<IN>().startWith(initial).block().build()
  }
}

