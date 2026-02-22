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
    return sniffFallback(bytes)
  }

  /**
   * Magic-byte checks for formats that may lack a decoder on this target.
   *
   * These run only when no registered decoder matched, making format detection
   * independent from decode capability.
   */
  private fun sniffFallback(bytes: ByteArray): VideoFormat {
    // ISO BMFF: [size][ftyp][brand]
    if (bytes.size >= 12 &&
      bytes[4] == 0x66.toByte() && bytes[5] == 0x74.toByte() &&
      bytes[6] == 0x79.toByte() && bytes[7] == 0x70.toByte()
    ) {
      val brand = (8 until 12).map { bytes[it].toInt().toChar() }.joinToString("")
      if (brand == "qt  ") return VideoFormat.MOV
      if (
        brand.startsWith("mp4") || brand == "isom" || brand == "M4V " ||
        brand == "avc1" || brand == "iso2" || brand == "iso5" ||
        brand == "iso6" || brand == "mmp4" ||
        brand.startsWith("3gp") || brand.startsWith("3g2")
      ) return VideoFormat.MP4
    }

    // AVI: RIFF....AVI␠
    if (bytes.size >= 12 &&
      bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
      bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
      bytes[8] == 'A'.code.toByte() && bytes[9] == 'V'.code.toByte() &&
      bytes[10] == 'I'.code.toByte() && bytes[11] == ' '.code.toByte()
    ) return VideoFormat.AVI

    // EBML (WebM / Matroska)
    if (bytes.size >= 4 &&
      bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() &&
      bytes[2] == 0xDF.toByte() && bytes[3] == 0xA3.toByte()
    ) {
      if (bytes.size >= 40) {
        val content = bytes.copyOfRange(0, minOf(bytes.size, 64)).decodeToString()
        if (content.contains("matroska")) return VideoFormat.MKV
        if (content.contains("webm")) return VideoFormat.WEBM
      }
      // Short EBML data without identifiable doctype - assume WebM (more common)
      return VideoFormat.WEBM
    }

    return VideoFormat.UNKNOWN
  }
}
