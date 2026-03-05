package dev.transmute.audio

import dev.transmute.common.Closeable

/**
 * Platform-agnostic intermediate representation for decoded audio data.
 *
 * Every audio decoder produces an [AudioIR]; every audio encoder consumes one.
 */
data class AudioIR(
  val samples: AudioSamples,
  val sampleRate: Int,
  val channelCount: Int,
  val durationMs: Long,
  val metadata: AudioMetadata = AudioMetadata(),
)

// --- Sample data ---

/** Provides pull-based streaming access to decoded audio samples. */
interface SampleStream : Closeable {
  val sampleRate: Int
  val channelCount: Int
  suspend fun readSamples(buffer: FloatArray): Int
}

/** In-memory audio sample buffer. */
data class AudioSamples(val data: FloatArray, val sampleRate: Int, val channelCount: Int) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is AudioSamples) return false
    return sampleRate == other.sampleRate &&
      channelCount == other.channelCount &&
      data.contentEquals(other.data)
  }

  override fun hashCode(): Int {
    var result = data.contentHashCode()
    result = 31 * result + sampleRate
    result = 31 * result + channelCount
    return result
  }
}

// --- Metadata ---

data class AudioMetadata(
  val title: String? = null,
  val artist: String? = null,
  val album: String? = null,
  val genre: String? = null,
  val durationMs: Long? = null,
  val bitrateKbps: Int? = null,
  val appMetadata: Map<String, String> = emptyMap(),
)
