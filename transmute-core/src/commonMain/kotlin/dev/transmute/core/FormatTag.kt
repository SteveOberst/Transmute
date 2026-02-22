package dev.transmute.core

/**
 * Type-level tag for a concrete [MediaFormat].
 *
 * This exists to make fluent APIs type-safe across stages that change format.
 * For example, an encode pipeline that targets PNG can expose handlers that
 * only accept PNG-encoded bytes.
 */
interface FormatTag<F : MediaFormat> {
  val format: F
}

/**
 * A runtime-only format tag.
 *
 * Use this when the concrete format cannot be known at compile time
 * (e.g. "preserve input format").
 */
data class AnyFormatTag<F : MediaFormat>(
  override val format: F,
) : FormatTag<F>
