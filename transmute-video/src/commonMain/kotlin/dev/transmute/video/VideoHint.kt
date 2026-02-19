package dev.transmute.video

import dev.transmute.core.VideoFormat

/**
 * Lightweight metadata snapshot for a video item.
 *
 * Used with [dev.transmute.VideoTransmuter.wouldTransmute] to determine whether a configured
 * transmuter would produce any change on a video file without decoding it.
 * All properties are nullable — `null` means the value is unknown, and the
 * transmuter will conservatively assume the transform *might* apply.
 *
 * ```kotlin
 * val transmuter = Transmute.video().apply {
 *     resize(1080, 1080)
 *     frameRate(30.0)
 *     metadata(MetadataPolicy.STRIP_ALL)
 * }
 *
 * val hint = VideoHint(width = item.width, height = item.height, fps = item.fps)
 * if (transmuter.wouldTransmute(hint)) {
 *     val compressed = transmuter.transmute(item.bytes)
 * }
 * ```
 */
data class VideoHint(
    /** Frame width in pixels, or `null` if unknown. */
    val width: Int? = null,
    /** Frame height in pixels, or `null` if unknown. */
    val height: Int? = null,
    /** Frame rate in frames per second, or `null` if unknown. */
    val fps: Double? = null,
    /** Track duration in milliseconds, or `null` if unknown. */
    val durationMs: Long? = null,
    /** Detected or declared format, or `null` if unknown. */
    val format: VideoFormat? = null,
    /** Encoded file size in bytes, or `null` if unknown. */
    val sizeBytes: Long? = null,
)
