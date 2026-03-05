package dev.transmute.audio

import dev.transmute.codec.MagicBytes
import dev.transmute.model.core.Bytes

/**
 * Detects audio format from raw bytes using built-in magic byte detection.
 *
 * Covers all standard audio formats (WAV, FLAC, OGG, OPUS, M4A, AAC, MP3).
 * Returns [AudioFormat.Unknown] for unrecognised data.
 */
object AudioFormatDetector {

  /**
   * Detects the [AudioFormat] from the first bytes of an audio file.
   *
   * @param bytes At least 12 bytes from the start of the file for reliable detection.
   * @return The detected format, or [AudioFormat.Unknown] if not recognized.
   */
  fun detect(bytes: Bytes): AudioFormat = detectBuiltIn(bytes) ?: AudioFormat.Unknown

  /**
   * Magic-byte checks covering all supported audio formats.
   */
  private fun detectBuiltIn(bytes: Bytes): AudioFormat? {
    val data = bytes.data

    // WAV: RIFF....WAVE
    if (MagicBytes.riffType(data) == "WAVE") return AudioFormat.Wav

    // FLAC: "fLaC"
    if (data.size >= 4 &&
      data[0] == 0x66.toByte() &&
      data[1] == 0x4C.toByte() &&
      data[2] == 0x61.toByte() &&
      data[3] == 0x43.toByte()
    ) {
      return AudioFormat.Flac
    }

    // OGG / OPUS: "OggS" + optional "OpusHead"
    if (MagicBytes.isOgg(data)) {
      if (MagicBytes.isOggOpus(data)) return AudioFormat.Opus
      return AudioFormat.Ogg
    }

    // ISO BMFF / MP4: [size][ftyp][brand]...
    MagicBytes.ftypBrand(data)?.let { brand ->
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
      data[0] == 0x49.toByte() &&
      data[1] == 0x44.toByte() &&
      data[2] == 0x33.toByte()
    ) {
      return AudioFormat.Mp3
    }
    if (data.size >= 2) {
      val b0 = data[0].toInt() and 0xFF
      val b1 = data[1].toInt() and 0xFF
      if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) return AudioFormat.Mp3
    }

    return null
  }
}
