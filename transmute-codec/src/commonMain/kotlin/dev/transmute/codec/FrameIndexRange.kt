package dev.transmute.codec

import kotlin.math.ceil
import kotlin.math.floor

/**
 * Inclusive-exclusive frame index range: `[startFrameIndex, endFrameIndexExclusive)`.
 *
 * This range is converted to a time range via [frameRate]. This is exact for
 * constant-frame-rate sources and a best-effort approximation for variable-frame-rate content.
 *
 * The conversion rounds outwards (start = floor, end = ceil) to avoid accidentally excluding frames.
 */
data class FrameIndexRange(
  val startFrameIndex: Long,
  val endFrameIndexExclusive: Long,
  val frameRate: Double,
) : DecodeRange {
  init {
    require(startFrameIndex >= 0) { "startFrameIndex must be >= 0, was $startFrameIndex" }
    require(endFrameIndexExclusive > startFrameIndex) {
      "endFrameIndexExclusive must be > startFrameIndex, was $endFrameIndexExclusive vs $startFrameIndex"
    }
    require(frameRate > 0.0 && frameRate.isFinite()) { "frameRate must be finite and > 0, was $frameRate" }
  }

  val frameCount: Long get() = endFrameIndexExclusive - startFrameIndex

  override fun timeframe(): TimeRangeMs {
    val startMs = floor(startFrameIndex * 1000.0 / frameRate).toLong().coerceAtLeast(0L)
    val endMsExclusive = ceil(endFrameIndexExclusive * 1000.0 / frameRate).toLong().coerceAtLeast(startMs + 1)
    return TimeRangeMs(startMs = startMs, endMsExclusive = endMsExclusive)
  }
}
