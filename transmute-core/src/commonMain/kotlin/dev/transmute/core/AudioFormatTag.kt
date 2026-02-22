package dev.transmute.core

/** Type-level tags for concrete [AudioFormat] values. */
sealed interface AudioFormatTag : FormatTag<AudioFormat> {
  data object Mp3 : AudioFormatTag { override val format: AudioFormat = AudioFormat.MP3 }
  data object Aac : AudioFormatTag { override val format: AudioFormat = AudioFormat.AAC }
  data object Wav : AudioFormatTag { override val format: AudioFormat = AudioFormat.WAV }
  data object Ogg : AudioFormatTag { override val format: AudioFormat = AudioFormat.OGG }
  data object Flac : AudioFormatTag { override val format: AudioFormat = AudioFormat.FLAC }
  data object M4a : AudioFormatTag { override val format: AudioFormat = AudioFormat.M4A }
  data object Opus : AudioFormatTag { override val format: AudioFormat = AudioFormat.OPUS }
}
