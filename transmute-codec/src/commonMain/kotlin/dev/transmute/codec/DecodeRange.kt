package dev.transmute.codec

/**
 * Marker interface for decoder range requests.
 *
 * Decoders must either:
 * - perform an efficient range decode for the requested range type, or
 * - throw [UnsupportedOperationException] (never silently ignore a range request).
 */
interface DecodeRange {
  /**
   * Convert this range request to the canonical time range representation.
   *
   * All built-in decoders interpret range requests in terms of this time range.
   */
  fun timeframe(): TimeRangeMs
}
