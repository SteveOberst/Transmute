package dev.transmute.video

import dev.transmute.core.VideoFormat

/**
 * Detects video format from raw bytes via registered decoders/codecs.
 *
 * The detector iterates registered decoders and returns the first non-null
 * result from `sniff(data)`.
 */
object VideoFormatDetector {

  /**
   * Detects the [VideoFormat] from the first bytes of a video file.
   *
   * @param bytes At least 12 bytes from the start of the file for reliable detection.
   * @return The detected format, or [VideoFormat.UNKNOWN] if not recognized.
   */
  fun detect(bytes: ByteArray): VideoFormat {
    VideoRegistries.installDefaultsIfEmpty()
    for (decoder in VideoRegistries.decoders.allDecoders) {
      decoder.sniff(bytes)?.let { return it }
    }
    return VideoFormat.UNKNOWN
  }
}
