package dev.transmute.video

import dev.transmute.codec.MagicBytes
import dev.transmute.model.core.Bytes

/**
 * Detects video format from raw bytes using built-in magic byte detection.
 *
 * Covers all standard video formats (MP4, MOV, AVI, WebM, MKV).
 * Returns [VideoFormat.Unknown] for unrecognised data.
 */
object VideoFormatDetector {

  /**
   * Detects the [VideoFormat] from the first bytes of a video file.
   *
   * @param bytes At least 12 bytes from the start of the file for reliable detection.
   * @return The detected format, or [VideoFormat.Unknown] if not recognized.
   */
  fun detect(bytes: Bytes): VideoFormat = detectBuiltIn(bytes) ?: VideoFormat.Unknown

  /**
   * Magic-byte checks covering all supported video formats.
   */
  private fun detectBuiltIn(bytes: Bytes): VideoFormat? {
    val data = bytes.data

    // ISO BMFF: [size][ftyp][brand]
    MagicBytes.ftypBrand(data)?.let { brand ->
      if (brand == "qt  ") return VideoFormat.Mov
      if (
        brand.startsWith("mp4") ||
        brand == "isom" ||
        brand == "M4V " ||
        brand == "avc1" ||
        brand == "iso2" ||
        brand == "iso5" ||
        brand == "iso6" ||
        brand == "mmp4" ||
        brand.startsWith("3gp") ||
        brand.startsWith("3g2")
      ) {
        return VideoFormat.Mp4
      }
    }

    // AVI: RIFF....AVI
    if (MagicBytes.riffType(data) == "AVI ") return VideoFormat.Avi

    // EBML (WebM / Matroska)
    if (MagicBytes.isEbml(data)) {
      return when (MagicBytes.ebmlDocType(data)) {
        "matroska" -> VideoFormat.Mkv
        "webm" -> VideoFormat.Webm
        else -> VideoFormat.Webm // Short EBML without identifiable doctype -- assume WebM
      }
    }

    return null
  }
}
