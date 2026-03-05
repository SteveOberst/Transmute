package dev.transmute.codec

/**
 * Inclusive-exclusive time range in milliseconds: `[startMs, endMsExclusive)`.
 */
data class TimeRangeMs(val startMs: Long, val endMsExclusive: Long) : DecodeRange {
  init {
    require(startMs >= 0) { "startMs must be >= 0, was $startMs" }
    require(endMsExclusive > startMs) { "endMsExclusive must be > startMs, was $endMsExclusive vs $startMs" }
  }

  val durationMs: Long get() = endMsExclusive - startMs

  override fun timeframe(): TimeRangeMs = this
}
