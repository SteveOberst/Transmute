package dev.transmute.audio

import dev.transmute.core.AudioFormat

/**
 * Detects audio format from raw bytes using magic-byte signatures.
 *
 * Registered codecs' [sniff()][dev.transmute.core.Codec.sniff] are tried first;
 * if none match, falls back to built-in magic-byte detection.
 */
object AudioFormatDetector {

  /**
   * Detects the [AudioFormat] from the first bytes of an audio file.
   *
   * @param bytes At least 12 bytes from the start of the file for reliable detection.
   * @return The detected format, or [AudioFormat.UNKNOWN] if not recognized.
   */
  fun detect(bytes: ByteArray): AudioFormat {
    // 1. Try registered codecs' sniff() first.
    for (codec in AudioRegistries.codecs) {
      codec.sniff(bytes)?.let { return it }
    }

    // 2. Fall back to built-in magic-byte detection.
    return detectByMagicBytes(bytes)
  }

  /**
   * Built-in magic-byte detection for all known audio formats.
   */
  fun detectByMagicBytes(bytes: ByteArray): AudioFormat {
    if (bytes.size < 4) return AudioFormat.UNKNOWN

    // WAV: "RIFF" ... "WAVE"
    if (bytes.size >= 12 &&
      bytes[0] == 'R'.code.toByte() &&
      bytes[1] == 'I'.code.toByte() &&
      bytes[2] == 'F'.code.toByte() &&
      bytes[3] == 'F'.code.toByte() &&
      bytes[8] == 'W'.code.toByte() &&
      bytes[9] == 'A'.code.toByte() &&
      bytes[10] == 'V'.code.toByte() &&
      bytes[11] == 'E'.code.toByte()
    ) {
      return AudioFormat.WAV
    }

    // FLAC: "fLaC"
    if (bytes[0] == 'f'.code.toByte() &&
      bytes[1] == 'L'.code.toByte() &&
      bytes[2] == 'a'.code.toByte() &&
      bytes[3] == 'C'.code.toByte()
    ) {
      return AudioFormat.FLAC
    }

    // OGG (also used for Opus/Vorbis): "OggS"
    if (bytes[0] == 'O'.code.toByte() &&
      bytes[1] == 'g'.code.toByte() &&
      bytes[2] == 'g'.code.toByte() &&
      bytes[3] == 'S'.code.toByte()
    ) {
      // Check for Opus inside OGG
      if (bytes.size >= 36) {
        val hasOpusHead = bytes.slice(28..35).map { it.toInt().toChar() }.joinToString("") == "OpusHead"
        if (hasOpusHead) return AudioFormat.OPUS
      }
      return AudioFormat.OGG
    }

    // MP3: ID3 tag or frame sync
    // ID3v2: "ID3"
    if (bytes[0] == 'I'.code.toByte() &&
      bytes[1] == 'D'.code.toByte() &&
      bytes[2] == '3'.code.toByte()
    ) {
      return AudioFormat.MP3
    }
    // MP3 frame sync: 0xFF 0xFB, 0xFF 0xFA, 0xFF 0xF3, 0xFF 0xF2
    if (bytes[0] == 0xFF.toByte() &&
      (bytes[1] == 0xFB.toByte() || bytes[1] == 0xFA.toByte() ||
        bytes[1] == 0xF3.toByte() || bytes[1] == 0xF2.toByte())
    ) {
      return AudioFormat.MP3
    }

    // AAC: ADTS frame sync 0xFF 0xF1 or 0xFF 0xF9
    if (bytes[0] == 0xFF.toByte() &&
      (bytes[1] == 0xF1.toByte() || bytes[1] == 0xF9.toByte())
    ) {
      return AudioFormat.AAC
    }

    // M4A/MP4 audio: ftyp box
    if (bytes.size >= 8) {
      val ftyp = bytes.slice(4..7).map { it.toInt().toChar() }.joinToString("")
      if (ftyp == "ftyp") {
        // Check brand for M4A
        if (bytes.size >= 12) {
          val brand = bytes.slice(8..11).map { it.toInt().toChar() }.joinToString("")
          if (brand == "M4A " || brand == "M4B " || brand == "mp42" || brand == "isom") {
            return AudioFormat.M4A
          }
        }
        return AudioFormat.M4A
      }
    }

    return AudioFormat.UNKNOWN
  }
}
