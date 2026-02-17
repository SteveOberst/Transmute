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
    return AudioFormat.UNKNOWN
  }
}
