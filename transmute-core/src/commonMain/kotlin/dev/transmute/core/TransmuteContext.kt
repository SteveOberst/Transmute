package dev.transmute.core

/**
 * Runtime context passed through every pipeline handler.
 *
 * This is intentionally minimal: Transmute is a generic transmutation framework,
 * and runtime format selection flows through explicit values (e.g. `Decoded.format`).
 *
 * Domain decode/encode options are carried here so default handlers and codecs can
 * access them without changing pipeline value types.
 */
data class TransmuteContext(
  val logger: TransmuteLogger = TransmuteLogger.Noop,
  val decodeOptions: DecodeOptions = NoDecodeOptions,
  val encodeOptions: EncodeOptions = NoEncodeOptions,
)

/** Structured logger for pipeline diagnostics. */
interface TransmuteLogger {
  fun debug(message: String)
  fun info(message: String)
  fun warn(message: String)
  fun error(message: String, throwable: Throwable? = null)

  object Noop : TransmuteLogger {
    override fun debug(message: String) {}
    override fun info(message: String) {}
    override fun warn(message: String) {}
    override fun error(message: String, throwable: Throwable?) {}
  }
}
