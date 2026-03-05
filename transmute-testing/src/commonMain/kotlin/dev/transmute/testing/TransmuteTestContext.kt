package dev.transmute.testing

import dev.transmute.common.LogLevel
import dev.transmute.common.PipelineContext
import dev.transmute.common.PrintLogger
import dev.transmute.common.TransmuteLogger
import dev.transmute.common.TransmuteLogging

/**
 * Factory for test-ready [PipelineContext] instances.
 *
 * Provides sensible defaults for test environments: print-to-stdout logging,
 * no decode/encode options overrides, and optional custom logger injection.
 *
 * ### Quick start
 * ```kotlin
 * val ctx = TransmuteTestContext.create()
 * codec.encode(ir, format, options, ctx)
 * ```
 *
 * ### Custom log level
 * ```kotlin
 * val ctx = TransmuteTestContext.create(logLevel = LogLevel.DEBUG)
 * ```
 *
 * ### Silent context (no output)
 * ```kotlin
 * val ctx = TransmuteTestContext.silent()
 * ```
 */
object TransmuteTestContext {

  /**
   * Creates a [PipelineContext] suitable for tests.
   *
   * @param logLevel Minimum log severity. Defaults to [LogLevel.DEBUG] so tests
   *   get full diagnostic output.
   * @param logger   Custom logger backend. Defaults to [PrintLogger] (stdout).
   */
  fun create(
    logLevel: LogLevel = LogLevel.DEBUG,
    logger: TransmuteLogger = TransmuteLogging.printLogger(logLevel),
  ): PipelineContext = PipelineContext(logger = logger)

  /**
   * Creates a silent [PipelineContext] that discards all log output.
   *
   * Useful for benchmarks or tests that assert on output and don't want
   * pipeline noise in stdout.
   */
  fun silent(): PipelineContext = PipelineContext(logger = TransmuteLogger.Noop)
}
