package dev.transmute.audio

import dev.transmute.model.core.ContainerFamily
import dev.transmute.model.core.MediaFormat

/**
 * Typed audio formats supported by Transmute.
 *
 * This replaces the old `enum class AudioFormat` with typed singleton objects.
 */
sealed interface AudioFormat : MediaFormat<AudioDecodeOptions, AudioEncodeOptions> {

  data object Mp3 : AudioFormat {
    override val label: String = "MP3"
    override val mimeType: String = "audio/mpeg"
    override val extension: String = "mp3"
    override val containerFamily: ContainerFamily = ContainerFamily.Mpeg
  }
  data object Aac : AudioFormat {
    override val label: String = "AAC"
    override val mimeType: String = "audio/aac"
    override val extension: String = "aac"
    override val containerFamily: ContainerFamily = ContainerFamily.Mpeg
  }
  data object Wav : AudioFormat {
    override val label: String = "WAV"
    override val mimeType: String = "audio/wav"
    override val extension: String = "wav"
    override val containerFamily: ContainerFamily = ContainerFamily.Riff
  }
  data object Ogg : AudioFormat {
    override val label: String = "Ogg"
    override val mimeType: String = "audio/ogg"
    override val extension: String = "ogg"
    override val containerFamily: ContainerFamily = ContainerFamily.Ogg
  }
  data object Flac : AudioFormat {
    override val label: String = "FLAC"
    override val mimeType: String = "audio/flac"
    override val extension: String = "flac"
    override val containerFamily: ContainerFamily = ContainerFamily.Flac
  }
  data object M4a : AudioFormat {
    override val label: String = "M4A"
    override val mimeType: String = "audio/mp4"
    override val extension: String = "m4a"
    override val containerFamily: ContainerFamily = ContainerFamily.IsoBmff
  }
  data object Opus : AudioFormat {
    override val label: String = "Opus"
    override val mimeType: String = "audio/opus"
    override val extension: String = "opus"
    override val containerFamily: ContainerFamily = ContainerFamily.Ogg
  }

  data object Unknown : AudioFormat {
    override val label: String = "Unknown"
    override val mimeType: String = "application/octet-stream"
    override val extension: String = "bin"
  }

  companion object {
    val all: Set<AudioFormat> = setOf(Mp3, Aac, Wav, Ogg, Flac, M4a, Opus)
  }
}
