package dev.transmute.audio

import dev.transmute.core.AudioFormat

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
   * @return The detected format, or [AudioFormat.UNKNOWN] if not recognized.
   */
  fun detect(bytes: ByteArray): AudioFormat {
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
  private fun sniffFallback(bytes: ByteArray): AudioFormat {
    // WAV: RIFF....WAVE
    if (bytes.size >= 12 &&
      bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
      bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
      bytes[8] == 'W'.code.toByte() && bytes[9] == 'A'.code.toByte() &&
      bytes[10] == 'V'.code.toByte() && bytes[11] == 'E'.code.toByte()
    ) return AudioFormat.WAV

    // FLAC: "fLaC"
    if (bytes.size >= 4 &&
      bytes[0] == 0x66.toByte() && bytes[1] == 0x4C.toByte() &&
      bytes[2] == 0x61.toByte() && bytes[3] == 0x43.toByte()
    ) return AudioFormat.FLAC

    // OGG / OPUS: "OggS" + optional "OpusHead"
    if (bytes.size >= 4 &&
      bytes[0] == 'O'.code.toByte() && bytes[1] == 'g'.code.toByte() &&
      bytes[2] == 'g'.code.toByte() && bytes[3] == 'S'.code.toByte()
    ) {
      if (bytes.size >= 36) {
        val header = String(bytes, 28, 8, Charsets.US_ASCII)
        if (header == "OpusHead") return AudioFormat.OPUS
      }
      return AudioFormat.OGG
    }

    // ISO BMFF / MP4: [size][ftyp][brand]...
    if (bytes.size >= 12 &&
      bytes[4] == 0x66.toByte() && bytes[5] == 0x74.toByte() &&
      bytes[6] == 0x79.toByte() && bytes[7] == 0x70.toByte()
    ) {
      val brand = (8 until 12).map { bytes[it].toInt().toChar() }.joinToString("")
      // Explicit M4A family brands
      if (brand == "M4A " || brand == "M4B " || brand == "M4P " || brand == "M4V ") return AudioFormat.M4A
      // Otherwise, treat as M4A only if we see audio markers and no obvious video marker.
      val window = bytes.copyOfRange(0, minOf(bytes.size, 256 * 1024))
      val s = window.decodeToString()
      val hasAudio = s.contains("soun") || s.contains("mp4a")
      val hasVideo = s.contains("vide") || s.contains("avc1") || s.contains("hvc1")
      if (hasAudio && !hasVideo) return AudioFormat.M4A
    }

    // AAC ADTS: 0xFFF sync word + layer must be 00
    if (bytes.size >= 2) {
      val b0 = bytes[0].toInt() and 0xFF
      val b1 = bytes[1].toInt() and 0xFF
      if (b0 == 0xFF && (b1 and 0xF6) == 0xF0) return AudioFormat.AAC
    }

    // MP3: ID3 tag or MPEG frame sync (checked after AAC to avoid ADTS false positives)
    if (bytes.size >= 3 &&
      bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()
    ) return AudioFormat.MP3
    if (bytes.size >= 2) {
      val b0 = bytes[0].toInt() and 0xFF
      val b1 = bytes[1].toInt() and 0xFF
      if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) return AudioFormat.MP3
    }

    return AudioFormat.UNKNOWN
  }
}
