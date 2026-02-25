package dev.transmute.common

import dev.transmute.model.core.DecodeOptions
import dev.transmute.model.core.EncodeOptions
import dev.transmute.model.core.NoDecodeOptions
import dev.transmute.model.core.NoEncodeOptions

/**
 * Runtime context passed through every pipeline handler.
 *
 * This is intentionally minimal: Transmute is a generic transmutation framework,
 * and runtime format selection flows through explicit values (e.g. `Decoded.format`).
 *
 * Domain decode/encode options are carried here so default handlers and codecs can
 * access them without changing pipeline value types.
 *
 * @property transmute Optional back-reference to the [TransmuteContext] composition root
 *   that created this pipeline context. Allows pipeline handlers to access
 *   top-level configuration (e.g. extras, GStreamer config) when needed.
 */
data class PipelineContext(
  val logger: TransmuteLogger = TransmuteLogger.Noop,
  val decodeOptions: DecodeOptions = NoDecodeOptions,
  val encodeOptions: EncodeOptions = NoEncodeOptions,
  val transmute: TransmuteContext? = null,
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
