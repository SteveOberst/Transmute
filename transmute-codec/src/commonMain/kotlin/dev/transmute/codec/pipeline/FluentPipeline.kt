package dev.transmute.codec.pipeline

import dev.transmute.common.PipelineContext

/**
 * A typed, sequential pipeline where each step can change the value's type.
 */
class Pipeline<IN, OUT> internal constructor(
  private val steps: List<suspend (Any?, PipelineContext) -> Any?>,
) : PipelineHandler<IN, OUT> {

  @Suppress("UNCHECKED_CAST")
  suspend fun run(input: IN, context: PipelineContext): OUT {
    var value: Any? = input
    for (step in steps) value = step(value, context)
    return value as OUT
  }

  override suspend fun handle(value: IN, context: PipelineContext): OUT = run(value, context)
}

/**
 * Fluent builder for [Pipeline].
 *
 * The key idea: the builder is parameterized by the *current* output type. Each [then]
 * returns a new builder instance whose current type is the handler's output.
 */
class PipelineBuilder<IN, CUR> internal constructor(
  private val steps: List<suspend (Any?, PipelineContext) -> Any?>,
) {

  /**
   * Alias for [then] intended for the first pipeline step to avoid starting a pipeline with `then(...)`.
   *
   * This is a pure naming convenience; it behaves identically to [then].
   */
  infix fun <NEXT> startWith(handler: PipelineHandler<CUR, NEXT>): PipelineBuilder<IN, NEXT> =
    this then handler

  /**
   * Alias for [then] intended for the first pipeline step to avoid starting a pipeline with `then { ... }`.
   *
   * This is a pure naming convenience; it behaves identically to [then].
   */
  infix fun <NEXT> startWith(block: suspend (CUR, PipelineContext) -> NEXT): PipelineBuilder<IN, NEXT> =
    this then block

  infix fun <NEXT> then(handler: PipelineHandler<CUR, NEXT>): PipelineBuilder<IN, NEXT> {
    val nextSteps = steps.toMutableList()
    nextSteps += { value, ctx ->
      @Suppress("UNCHECKED_CAST")
      handler.handle(value as CUR, ctx) as Any?
    }
    return PipelineBuilder(nextSteps)
  }

  infix fun <NEXT> then(block: suspend (CUR, PipelineContext) -> NEXT): PipelineBuilder<IN, NEXT> =
    then(PipelineHandler { value, ctx -> block(value, ctx) })

  fun build(): Pipeline<IN, CUR> = Pipeline(steps = steps.toList())

  companion object {
    fun <IN> start(): PipelineBuilder<IN, IN> = PipelineBuilder(emptyList())
  }
}

/**
 * Append a **same-type** handler to a [PipelineBuilder].
 *
 * This exists mostly to enable ergonomic `+=` usage:
 *
 * ```kotlin
 * var b = PipelineBuilder.start<Bytes>().startWith(ImageCodecs.Decode.DEFAULT)
 * b += tap { decoded, ctx -> ctx.logger.info("decoded ${decoded.format}") }
 * val pipeline = b.build()
 * ```
 *
 * For **type-changing** steps you must use [PipelineBuilder.then]/[PipelineBuilder.startWith].
 */
operator fun <IN, CUR> PipelineBuilder<IN, CUR>.plus(
  next: PipelineHandler<CUR, CUR>,
): PipelineBuilder<IN, CUR> = this then next

/**
 * Append a **same-type** lambda step to a [PipelineBuilder]. See [plus].
 */
operator fun <IN, CUR> PipelineBuilder<IN, CUR>.plus(
  block: suspend (CUR, PipelineContext) -> CUR,
): PipelineBuilder<IN, CUR> = this then block
