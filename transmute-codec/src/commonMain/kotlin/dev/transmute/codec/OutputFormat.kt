package dev.transmute.codec

import dev.transmute.model.core.MediaFormat

/**
 * Output format selection for dynamic-output transmuters.
 *
 * This replaces the ambiguous "nullable outputFormat" pattern with an explicit sentinel:
 * - [ORIGINAL] means "fall back to the input format" (unless an encode handler chooses otherwise).
 * - [Exact] forces an explicit output format.
 */
sealed interface OutputFormat<out F : MediaFormat<*, *>> {
  /** Use the original/input format. */
  data object ORIGINAL : OutputFormat<Nothing>

  /** Force an explicit output format. */
  data class Exact<F : MediaFormat<*, *>>(val format: F) : OutputFormat<F>
}

/** Resolves an [OutputFormat] against the detected input/original [format]. */
fun <F : MediaFormat<*, *>> OutputFormat<F>.resolve(format: F): F = when (this) {
  OutputFormat.ORIGINAL -> format
  is OutputFormat.Exact -> this.format
}
