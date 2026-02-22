package dev.transmute.audio

import dev.transmute.core.MediaFormat

/**
 * Typed audio formats supported by Transmute.
 *
 * This replaces the old `enum class AudioFormat` with typed singleton objects.
 */
sealed interface AudioFormat : MediaFormat<AudioDecodeOptions, AudioEncodeOptions> {

  data object Mp3 : AudioFormat { override val mimeType: String = "audio/mpeg"; override val extension: String = "mp3" }
  data object Aac : AudioFormat { override val mimeType: String = "audio/aac"; override val extension: String = "aac" }
  data object Wav : AudioFormat { override val mimeType: String = "audio/wav"; override val extension: String = "wav" }
  data object Ogg : AudioFormat { override val mimeType: String = "audio/ogg"; override val extension: String = "ogg" }
  data object Flac : AudioFormat { override val mimeType: String = "audio/flac"; override val extension: String = "flac" }
  data object M4a : AudioFormat { override val mimeType: String = "audio/mp4"; override val extension: String = "m4a" }
  data object Opus : AudioFormat { override val mimeType: String = "audio/opus"; override val extension: String = "opus" }

  data object Unknown : AudioFormat { override val mimeType: String = "application/octet-stream"; override val extension: String = "bin" }

  companion object {
    val all: Set<AudioFormat> = setOf(Mp3, Aac, Wav, Ogg, Flac, M4a, Opus)
  }
}

