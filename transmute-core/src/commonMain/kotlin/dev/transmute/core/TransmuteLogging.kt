package dev.transmute.core

/**
 * Log levels for Transmute operations, ordered by increasing severity.
 *
 * Use with [TransmuteLogging.configure] to control library verbosity:
 * ```kotlin
 * TransmuteLogging.configure(LogLevel.INFO)     // console output
 * TransmuteLogging.configure(LogLevel.OFF)       // silent (default)
 * ```
 */
enum class LogLevel {
  /** Fine-grained diagnostic output (codec internals, buffer sizes, etc.). */
  DEBUG,

  /** General pipeline progress (stage start/complete, format detection). */
  INFO,

  /** Potentially problematic situations (codec fallback, missing encoder). */
  WARN,

  /** Errors that may still allow the pipeline to finish. */
  ERROR,

  /** Disable all logging. */
  OFF,
}

/**
 * Global logging configuration for the Transmute library.
 *
 * By default logging is **off**. Enable it before any transmutation:
 *
 * ```kotlin
 * // Console logging at INFO level
 * TransmuteLogging.configure(LogLevel.INFO)
 *
 * // Custom backend
 * TransmuteLogging.configure(LogLevel.DEBUG, object : ConversionLogger {
 *   override fun debug(message: String) = myFramework.debug("transmute", message)
 *   override fun info(message: String)  = myFramework.info("transmute", message)
 *   override fun warn(message: String)  = myFramework.warn("transmute", message)
 *   override fun error(message: String, throwable: Throwable?) =
 *     myFramework.error("transmute", message, throwable)
 * })
 * ```
 *
 * Per-operation overrides are supported via each transmuter's `logger()` method.
 */
object TransmuteLogging {

  @Volatile
  private var _level: LogLevel = LogLevel.OFF

  @Volatile
  private var _logger: ConversionLogger = ConversionLogger.Noop

  /** Current global log level. */
  val level: LogLevel get() = _level

  /** Active logger instance (already level-filtered). */
  val logger: ConversionLogger get() = _logger

  /**
   * Configure global logging.
   *
   * @param level  Minimum severity to emit. [LogLevel.OFF] silences everything.
   * @param output Backend that receives filtered messages. Defaults to [PrintLogger].
   */
  fun configure(level: LogLevel, output: ConversionLogger = PrintLogger) {
    _level = level
    _logger = if (level == LogLevel.OFF) ConversionLogger.Noop
              else LevelFilterLogger(level, output)
  }

  /** Reset to default silent state. */
  fun reset() {
    _level = LogLevel.OFF
    _logger = ConversionLogger.Noop
  }

  /**
   * Create a standalone level-filtered [PrintLogger].
   *
   * Useful for per-operation overrides without changing the global config:
   * ```kotlin
   * Transmute.image(bytes) {
   *   logger(TransmuteLogging.printLogger(LogLevel.DEBUG))
   * }
   * ```
   */
  fun printLogger(level: LogLevel): ConversionLogger =
    if (level == LogLevel.OFF) ConversionLogger.Noop
    else LevelFilterLogger(level, PrintLogger)
}

/**
 * [ConversionLogger] that writes to standard output with level prefixes.
 *
 * All messages are written as `[transmute:<LEVEL>] <message>`.
 * Throwable stack traces are appended on error.
 */
object PrintLogger : ConversionLogger {
  override fun debug(message: String) { println("[transmute:DEBUG] $message") }
  override fun info(message: String)  { println("[transmute:INFO]  $message") }
  override fun warn(message: String)  { println("[transmute:WARN]  $message") }
  override fun error(message: String, throwable: Throwable?) {
    println("[transmute:ERROR] $message")
    throwable?.let { println(it.stackTraceToString()) }
  }
}

/**
 * Decorator that suppresses messages below [minLevel].
 */
internal class LevelFilterLogger(
  private val minLevel: LogLevel,
  private val delegate: ConversionLogger,
) : ConversionLogger {
  override fun debug(message: String) { if (minLevel <= LogLevel.DEBUG) delegate.debug(message) }
  override fun info(message: String)  { if (minLevel <= LogLevel.INFO)  delegate.info(message) }
  override fun warn(message: String)  { if (minLevel <= LogLevel.WARN)  delegate.warn(message) }
  override fun error(message: String, throwable: Throwable?) {
    if (minLevel <= LogLevel.ERROR) delegate.error(message, throwable)
  }
}
