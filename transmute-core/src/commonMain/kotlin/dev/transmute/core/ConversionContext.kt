package dev.transmute.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

/**
 * Runtime context passed through every stage of the conversion pipeline.
 *
 * Holds cancellation, progress, budgets, and a scratchpad for inter-stage data.
 */
data class ConversionContext(
  val jobId: String,
  val coroutineJob: Job? = null,
  val metadataPolicy: MetadataPolicy,
  val onProgress: (Float) -> Unit = {},
  val logger: ConversionLogger = ConversionLogger.Noop,
  val scratchpad: MutableMap<String, Any> = mutableMapOf(),
  val timeBudgetMs: Long = 0,
  val memoryBudgetBytes: Long = 0,
) {
  fun isCancelled(): Boolean = coroutineJob?.isCancelled == true

  fun checkCancellation() {
    if (isCancelled()) throw CancellationException("Conversion cancelled: jobId=$jobId")
  }
}

/** Structured logger for pipeline diagnostics. */
interface ConversionLogger {
  fun debug(message: String)
  fun info(message: String)
  fun warn(message: String)
  fun error(message: String, throwable: Throwable? = null)

  object Noop : ConversionLogger {
    override fun debug(message: String) {}
    override fun info(message: String) {}
    override fun warn(message: String) {}
    override fun error(message: String, throwable: Throwable?) {}
  }
}
