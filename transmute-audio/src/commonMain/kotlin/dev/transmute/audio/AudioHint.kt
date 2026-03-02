package dev.transmute.audio

/**
 * Lightweight metadata snapshot for an audio item.
 *
 * Used with [dev.transmute.AudioTransmuter.wouldTransmute] to determine whether a configured
 * transmuter would produce any change on an audio file without decoding it.
 * All properties are nullable - `null` means the value is unknown, and the
 * transmuter will conservatively assume the transform *might* apply.
 *
 * ```kotlin
 * val transmuter = Transmute.audio {
 *     resample(44100)
 *     mono()
 * }
 *
 * val hint = AudioHint(sampleRate = track.sampleRate, channelCount = track.channels)
 * if (transmuter.wouldTransmute(hint)) {
 *     val processed = transmuter.transmute(track.bytes.asBytes())
 * }
 * ```
 */
data class AudioHint(
    /** Track duration in milliseconds, or `null` if unknown. */
    val durationMs: Long? = null,
    /** Sample rate in Hz (e.g. 44100, 48000), or `null` if unknown. */
    val sampleRate: Int? = null,
    /** Number of audio channels (e.g. 1 = mono, 2 = stereo), or `null` if unknown. */
    val channelCount: Int? = null,
    /** Detected or declared format, or `null` if unknown. */
    val format: AudioFormat? = null,
    /** Encoded file size in bytes, or `null` if unknown. */
    val sizeBytes: Long? = null,
)
