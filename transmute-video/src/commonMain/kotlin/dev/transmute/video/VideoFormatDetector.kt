package dev.transmute.video

import dev.transmute.model.core.Bytes

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
   * @return The detected format, or [VideoFormat.Unknown] if not recognized.
   */
  fun detect(bytes: Bytes): VideoFormat {
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
  private fun sniffFallback(bytes: Bytes): VideoFormat {
    val data = bytes.data
    // ISO BMFF: [size][ftyp][brand]
    if (data.size >= 12 &&
      data[4] == 0x66.toByte() && data[5] == 0x74.toByte() &&
      data[6] == 0x79.toByte() && data[7] == 0x70.toByte()
    ) {
      val brand = (8 until 12).map { data[it].toInt().toChar() }.joinToString("")
      if (brand == "qt  ") return VideoFormat.Mov
      if (
        brand.startsWith("mp4") || brand == "isom" || brand == "M4V " ||
        brand == "avc1" || brand == "iso2" || brand == "iso5" ||
        brand == "iso6" || brand == "mmp4" ||
        brand.startsWith("3gp") || brand.startsWith("3g2")
      ) return VideoFormat.Mp4
    }

    // AVI: RIFF....AVI
    if (data.size >= 12 &&
      data[0] == 'R'.code.toByte() && data[1] == 'I'.code.toByte() &&
      data[2] == 'F'.code.toByte() && data[3] == 'F'.code.toByte() &&
      data[8] == 'A'.code.toByte() && data[9] == 'V'.code.toByte() &&
      data[10] == 'I'.code.toByte() && data[11] == ' '.code.toByte()
    ) return VideoFormat.Avi

    // EBML (WebM / Matroska)
    if (data.size >= 4 &&
      data[0] == 0x1A.toByte() && data[1] == 0x45.toByte() &&
      data[2] == 0xDF.toByte() && data[3] == 0xA3.toByte()
    ) {
      if (data.size >= 40) {
        val content = data.copyOfRange(0, minOf(data.size, 64)).decodeToString()
        if (content.contains("matroska")) return VideoFormat.Mkv
        if (content.contains("webm")) return VideoFormat.Webm
      }
      // Short EBML data without identifiable doctype - assume WebM (more common)
      return VideoFormat.Webm
    }

    return VideoFormat.Unknown
  }
}
