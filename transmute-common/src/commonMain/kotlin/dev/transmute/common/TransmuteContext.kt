package dev.transmute.common

import dev.transmute.model.core.DecodeOptions
import dev.transmute.model.core.EncodeOptions
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.core.NoEncodeOptions

/**
 * Composition root for the Transmute library.
 *
 * `TransmuteContext` holds top-level configuration (logging, extensible
 * extras) and serves as a factory for [PipelineContext] instances.  It replaces
 * the global mutable singletons ([TransmuteConfig], [TransmuteLogging]) with an
 * explicit, immutable configuration object that can be passed through user code.
 *
 * ```kotlin
 * val ctx = TransmuteContext {
 *     logger = TransmuteLogging.printLogger(LogLevel.DEBUG)
 * }
 *
 * val pipeline = ctx.pipelineContext(
 *     decodeOptions = CanonicalImageDecodeOptions(),
 *     encodeOptions = PngEncodeOptions(),
 * )
 * ```
 *
 * ## Extras
 *
 * The [extras] map provides a type-safe extension point for modules that add
 * capabilities without modifying the core context.  For example, the filesystem
 * module stores a `TransmuteFileSystem` instance via a typed extension property:
 *
 * ```kotlin
 * val TransmuteContext.fileSystem: TransmuteFileSystem?
 *     get() = service("transmute.filesystem")
 * ```
 */
class TransmuteContext private constructor(
  /** Logger used by default for new pipeline contexts. */
  val logger: TransmuteLogger,
  /**
   * Extensible key-value store for module-provided services.
   *
   * Keys should use reverse-domain notation (e.g. `"transmute.filesystem"`)
   * to avoid collisions.
   */
  val extras: Map<String, Any>,
) {

  /**
   * Creates a [PipelineContext] pre-configured with this context's logger
   * and a back-reference to this [TransmuteContext].
   *
   * Callers can override [decodeOptions] and [encodeOptions] per-pipeline,
   * or supply a different [logger] for a specific invocation.
   */
  fun pipelineContext(
    decodeOptions: DecodeOptions = NoDecodeOptions,
    encodeOptions: EncodeOptions = NoEncodeOptions,
    logger: TransmuteLogger = this.logger,
  ): PipelineContext = PipelineContext(
    logger = logger,
    decodeOptions = decodeOptions,
    encodeOptions = encodeOptions,
    transmute = this,
  )

  /**
   * Returns a typed service from [extras], or `null` if absent or wrong type.
   *
   * ```kotlin
   * val fs: TransmuteFileSystem? = ctx.service("transmute.filesystem")
   * ```
   */
  inline fun <reified T> service(key: String): T? = extras[key] as? T

  /**
   * Returns a copy with an additional extra entry.
   */
  fun withExtra(key: String, value: Any): TransmuteContext = TransmuteContext(
    logger = logger,
    extras = extras + (key to value),
  )

  /** DSL builder for [TransmuteContext]. */
  class Builder @PublishedApi internal constructor() {
    /** Logger used by default for pipeline contexts. Defaults to [TransmuteLogger.Noop]. */
    var logger: TransmuteLogger = TransmuteLogger.Noop

    @PublishedApi
    internal val extras: MutableMap<String, Any> = mutableMapOf()

    /** Register an extra service by key. */
    fun extra(key: String, value: Any) {
      extras[key] = value
    }

    @PublishedApi
    internal fun build(): TransmuteContext = TransmuteContext(
      logger = logger,
      extras = extras.toMap(),
    )
  }

  companion object {
    /**
     * Creates a [TransmuteContext] using the builder DSL.
     *
     * ```kotlin
     * val ctx = TransmuteContext {
     *     logger = TransmuteLogging.printLogger(LogLevel.DEBUG)
     * }
     * ```
     */
    inline operator fun invoke(block: Builder.() -> Unit = {}): TransmuteContext =
      Builder().apply(block).build()

    /**
     * Creates a default [TransmuteContext] that mirrors the current global
     * configuration from [TransmuteLogging] and [TransmuteConfig].
     *
     * Useful as a migration bridge: existing code that relies on global
     * configuration can obtain a context that reflects those globals.
     */
    fun default(): TransmuteContext = TransmuteContext(
      logger = TransmuteLogging.logger,
      extras = emptyMap(),
    )
  }

  override fun toString(): String =
    "TransmuteContext(logger=$logger, extras=${extras.keys})"
}
