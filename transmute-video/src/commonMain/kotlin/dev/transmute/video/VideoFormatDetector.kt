package dev.transmute.video

import dev.transmute.core.VideoFormat

/**
 * Detects video format from raw bytes using magic-byte signatures.
 *
 * Registered codecs' [sniff()][dev.transmute.core.Codec.sniff] are tried first;
 * if none match, falls back to built-in magic-byte detection.
 */
object VideoFormatDetector {

  /**
   * Detects the [VideoFormat] from the first bytes of a video file.
   *
   * @param bytes At least 12 bytes from the start of the file for reliable detection.
   * @return The detected format, or [VideoFormat.UNKNOWN] if not recognized.
   */
  fun detect(bytes: ByteArray): VideoFormat {
    // 1. Try registered codecs' sniff() first.
    for (codec in VideoRegistries.codecs) {
      codec.sniff(bytes)?.let { return it }
    }

    // 2. Fall back to built-in magic-byte detection.
    return detectByMagicBytes(bytes)
  }

  /**
   * Built-in magic-byte detection for all known video formats.
   */
  fun detectByMagicBytes(bytes: ByteArray): VideoFormat {
    if (bytes.size < 4) return VideoFormat.UNKNOWN

    // Check ftyp box (MP4, MOV, 3GP, etc.)
    if (bytes.size >= 12) {
      val ftyp = bytes.readString(4, 4)
      if (ftyp == "ftyp") {
        val brand = bytes.readString(8, 4)
        return when {
          // QuickTime
          brand == "qt  " -> VideoFormat.MOV
          // Various MP4 brands
          brand.startsWith("mp4") || brand == "isom" || brand == "M4V " ||
            brand == "avc1" || brand == "iso2" || brand == "iso5" ||
            brand == "iso6" || brand == "mmp4" -> VideoFormat.MP4
          // 3GP (not in our enum, treat as MP4)
          brand.startsWith("3gp") || brand.startsWith("3g2") -> VideoFormat.MP4
          // HEIF/HEIC containers (image, not video)
          brand == "heic" || brand == "mif1" || brand == "msf1" -> VideoFormat.MP4
          // Default to MOV for unknown ftyp
          else -> VideoFormat.MOV
        }
      }
    }

    // WebM: starts with EBML header 0x1A45DFA3
    if (bytes[0] == 0x1A.toByte() &&
      bytes[1] == 0x45.toByte() &&
      bytes[2] == 0xDF.toByte() &&
      bytes[3] == 0xA3.toByte()
    ) {
      // Could be WebM or Matroska - check doctype if enough bytes
      // For simplicity, assume WebM for now
      return VideoFormat.WEBM
    }

    // MKV: same EBML header, distinguish by looking for "matroska" or "webm" doctype
    // Since both use EBML, return MKV if we can detect it
    if (bytes.size >= 40) {
      val content = bytes.decodeToString()
      if (content.contains("matroska")) {
        return VideoFormat.MKV
      }
      if (content.contains("webm")) {
        return VideoFormat.WEBM
      }
    }

    // AVI: "RIFF" ... "AVI "
    if (bytes.size >= 12 &&
      bytes[0] == 'R'.code.toByte() &&
      bytes[1] == 'I'.code.toByte() &&
      bytes[2] == 'F'.code.toByte() &&
      bytes[3] == 'F'.code.toByte() &&
      bytes[8] == 'A'.code.toByte() &&
      bytes[9] == 'V'.code.toByte() &&
      bytes[10] == 'I'.code.toByte() &&
      bytes[11] == ' '.code.toByte()
    ) {
      return VideoFormat.AVI
    }

    return VideoFormat.UNKNOWN
  }

  private fun ByteArray.readString(offset: Int, length: Int): String {
    if (offset + length > size) return ""
    return (offset until offset + length).map { this[it].toInt().toChar() }.joinToString("")
  }
}
