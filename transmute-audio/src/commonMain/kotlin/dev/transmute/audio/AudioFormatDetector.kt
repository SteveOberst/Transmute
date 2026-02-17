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
    // Fallback sniffs for formats that have no registered decoder on this platform.
    return sniffFallback(bytes)
  }

  /**
   * Magic-byte checks for formats that may lack a platform decoder
   * (e.g. OGG on iOS). These run only when no registered decoder matched.
   */
  private fun sniffFallback(bytes: ByteArray): AudioFormat {
    if (bytes.size < 4) return AudioFormat.UNKNOWN
    // OGG: "OggS" capture pattern
    if (bytes[0] == 'O'.code.toByte() && bytes[1] == 'g'.code.toByte() &&
      bytes[2] == 'g'.code.toByte() && bytes[3] == 'S'.code.toByte()
    ) return AudioFormat.OGG
    return AudioFormat.UNKNOWN
  }
}
