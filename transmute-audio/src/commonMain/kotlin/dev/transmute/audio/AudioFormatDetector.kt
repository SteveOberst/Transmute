package dev.transmute.audio

import dev.transmute.model.core.Bytes

/**
 * Detects audio format from raw bytes via registered decoders/codecs.
 *
 * The detector iterates registered decoders and returns the first non-null
 * result from `sniff(data)`.
 */
object AudioFormatDetector {

  /**
   * Detects the [AudioFormat] from the first bytes of an audio file.
   *
   * @param bytes At least 12 bytes from the start of the file for reliable detection.
   * @return The detected format, or [AudioFormat.Unknown] if not recognized.
   */
  fun detect(bytes: Bytes): AudioFormat {
    AudioRegistries.installDefaultsIfEmpty()
    for (decoder in AudioRegistries.decoders.allDecoders) {
      decoder.sniff(bytes)?.let { return it }
    }
    return sniffFallback(bytes)
  }

  /**
   * Magic-byte checks for formats that may lack a platform decoder on this target.
   *
   * These run only when no registered decoder matched, making format detection
   * independent from decode capability.
   */
  private fun sniffFallback(bytes: Bytes): AudioFormat {
    val data = bytes.data
    // WAV: RIFF....WAVE
    if (data.size >= 12 &&
      data[0] == 'R'.code.toByte() && data[1] == 'I'.code.toByte() &&
      data[2] == 'F'.code.toByte() && data[3] == 'F'.code.toByte() &&
      data[8] == 'W'.code.toByte() && data[9] == 'A'.code.toByte() &&
      data[10] == 'V'.code.toByte() && data[11] == 'E'.code.toByte()
    ) return AudioFormat.Wav

    // FLAC: "fLaC"
    if (data.size >= 4 &&
      data[0] == 0x66.toByte() && data[1] == 0x4C.toByte() &&
      data[2] == 0x61.toByte() && data[3] == 0x43.toByte()
    ) return AudioFormat.Flac

    // OGG / OPUS: "OggS" + optional "OpusHead"
    if (data.size >= 4 &&
      data[0] == 'O'.code.toByte() && data[1] == 'g'.code.toByte() &&
      data[2] == 'g'.code.toByte() && data[3] == 'S'.code.toByte()
    ) {
      if (data.size >= 36) {
        val header = String(data, 28, 8, Charsets.US_ASCII)
        if (header == "OpusHead") return AudioFormat.Opus
      }
      return AudioFormat.Ogg
    }

    // ISO BMFF / MP4: [size][ftyp][brand]...
    if (data.size >= 12 &&
      data[4] == 0x66.toByte() && data[5] == 0x74.toByte() &&
      data[6] == 0x79.toByte() && data[7] == 0x70.toByte()
    ) {
      val brand = (8 until 12).map { data[it].toInt().toChar() }.joinToString("")
      // Explicit M4A family brands
      if (brand == "M4A " || brand == "M4B " || brand == "M4P " || brand == "M4V ") return AudioFormat.M4a
      // Otherwise, treat as M4A only if we see audio markers and no obvious video marker.
      val window = data.copyOfRange(0, minOf(data.size, 256 * 1024))
      val s = window.decodeToString()
      val hasAudio = s.contains("soun") || s.contains("mp4a")
      val hasVideo = s.contains("vide") || s.contains("avc1") || s.contains("hvc1")
      if (hasAudio && !hasVideo) return AudioFormat.M4a
    }

    // AAC ADTS: 0xFFF sync word + layer must be 00
    if (data.size >= 2) {
      val b0 = data[0].toInt() and 0xFF
      val b1 = data[1].toInt() and 0xFF
      if (b0 == 0xFF && (b1 and 0xF6) == 0xF0) return AudioFormat.Aac
    }

    // MP3: ID3 tag or MPEG frame sync (checked after AAC to avoid ADTS false positives)
    if (data.size >= 3 &&
      data[0] == 'I'.code.toByte() && data[1] == 'D'.code.toByte() && data[2] == '3'.code.toByte()
    ) return AudioFormat.Mp3
    if (data.size >= 2) {
      val b0 = data[0].toInt() and 0xFF
      val b1 = data[1].toInt() and 0xFF
      if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) return AudioFormat.Mp3
    }

    return AudioFormat.Unknown
  }
}
