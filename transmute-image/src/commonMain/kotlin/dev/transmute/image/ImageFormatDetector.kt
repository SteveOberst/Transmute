package dev.transmute.image

import dev.transmute.core.ImageFormat

/**
 * Detects image format from raw file bytes by inspecting magic-byte signatures.
 *
 * Covers every format commonly encountered on iOS and Android devices:
 * - **JPEG** — universal camera format
 * - **PNG** — screenshots, stickers, transparency
 * - **WebP** — Android default since 4.0, also used by WhatsApp/Telegram
 * - **HEIF / HEIC** — iOS default since iOS 11 (iPhone 7+)
 * - **AVIF** — next-gen format, growing adoption on Android 12+
 * - **GIF** — animated images
 * - **BMP** — legacy, sometimes from Windows screenshots
 * - **TIFF** — ProRAW on iOS, some scanner apps
 *
 * All detection is pure Kotlin — no platform dependencies.
 * Uses [ImageFormat] from the core module for type-safe format identification.
 */
object ImageFormatDetector {

  /**
   * Sniffs the format from the first bytes of an image file.
   *
   * Requires at least 12 bytes for reliable detection (HEIF/WebP need
   * the RIFF/ftyp box). Returns [ImageFormat.UNKNOWN] for unrecognised data.
   */
  fun detect(data: ByteArray): ImageFormat {
    // 1. Try registered codecs' sniff() first.
    for (codec in ImageRegistries.codecs) {
      codec.sniff(data)?.let { return it }
    }

    // 2. Fall back to built-in magic-byte detection.
    return detectByMagicBytes(data)
  }

  /**
   * Built-in magic-byte detection for all known image formats.
   */
  fun detectByMagicBytes(data: ByteArray): ImageFormat {
    if (data.size < 4) return ImageFormat.UNKNOWN

    // --- JPEG: FF D8 FF ---
    if (data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte() && data[2] == 0xFF.toByte()) {
      return ImageFormat.JPEG
    }

    // --- PNG: 89 50 4E 47 0D 0A 1A 0A ---
    if (data.size >= 8 &&
      data[0] == 0x89.toByte() && data[1] == 0x50.toByte() &&
      data[2] == 0x4E.toByte() && data[3] == 0x47.toByte() &&
      data[4] == 0x0D.toByte() && data[5] == 0x0A.toByte() &&
      data[6] == 0x1A.toByte() && data[7] == 0x0A.toByte()
    ) {
      return ImageFormat.PNG
    }

    // --- GIF: "GIF87a" or "GIF89a" ---
    if (data.size >= 6 &&
      data[0] == 0x47.toByte() && data[1] == 0x49.toByte() &&
      data[2] == 0x46.toByte() && data[3] == 0x38.toByte() &&
      (data[4] == 0x37.toByte() || data[4] == 0x39.toByte()) &&
      data[5] == 0x61.toByte()
    ) {
      return ImageFormat.GIF
    }

    // --- BMP: "BM" ---
    if (data[0] == 0x42.toByte() && data[1] == 0x4D.toByte()) {
      return ImageFormat.BMP
    }

    // --- TIFF: "II" (little-endian) + 42 or "MM" (big-endian) + 42 ---
    if (data.size >= 4) {
      if (data[0] == 0x49.toByte() && data[1] == 0x49.toByte() &&
        data[2] == 0x2A.toByte() && data[3] == 0x00.toByte()
      ) return ImageFormat.TIFF

      if (data[0] == 0x4D.toByte() && data[1] == 0x4D.toByte() &&
        data[2] == 0x00.toByte() && data[3] == 0x2A.toByte()
      ) return ImageFormat.TIFF
    }

    // --- WebP: RIFF....WEBP ---
    if (data.size >= 12 &&
      data[0] == 0x52.toByte() && data[1] == 0x49.toByte() &&
      data[2] == 0x46.toByte() && data[3] == 0x46.toByte() &&
      data[8] == 0x57.toByte() && data[9] == 0x45.toByte() &&
      data[10] == 0x42.toByte() && data[11] == 0x50.toByte()
    ) {
      return ImageFormat.WEBP
    }

    // --- HEIF / HEIC / AVIF: ISO BMFF ftyp box ---
    // Structure: [4-byte size][ftyp][4-byte brand]
    // The brand determines the exact sub-format.
    if (data.size >= 12) {
      val ftyp = data[4] == 0x66.toByte() && data[5] == 0x74.toByte() &&
        data[6] == 0x79.toByte() && data[7] == 0x70.toByte()

      if (ftyp) {
        val brand = data.sliceArray(8 until 12).decodeToString()
        return when {
          brand == "heic" || brand == "heix" -> ImageFormat.HEIC
          brand == "mif1" || brand == "msf1" -> ImageFormat.HEIF
          brand == "hevc" || brand == "hevx" -> ImageFormat.HEIC
          brand == "avif" || brand == "avis" -> ImageFormat.AVIF
          else -> ImageFormat.HEIF // Generic ISO BMFF image container
        }
      }
    }

    return ImageFormat.UNKNOWN
  }

  /**
   * Returns `true` if the format supports transparency (alpha channel).
   * Important for choosing the right encoder — JPEG/HEIC don't support alpha.
   */
  fun supportsAlpha(format: ImageFormat): Boolean = when (format) {
    ImageFormat.PNG, ImageFormat.WEBP, ImageFormat.GIF, ImageFormat.AVIF -> true
    ImageFormat.JPEG, ImageFormat.HEIF, ImageFormat.HEIC, ImageFormat.BMP,
    ImageFormat.TIFF, ImageFormat.UNKNOWN -> false
  }

  /**
   * Returns `true` if the format is lossy by default.
   * Useful for deciding whether re-encoding saves space.
   */
  fun isLossy(format: ImageFormat): Boolean = when (format) {
    ImageFormat.JPEG, ImageFormat.HEIF, ImageFormat.HEIC, ImageFormat.AVIF -> true
    ImageFormat.PNG, ImageFormat.GIF, ImageFormat.BMP, ImageFormat.TIFF -> false
    ImageFormat.WEBP -> true // WebP supports both, but defaults to lossy
    ImageFormat.UNKNOWN -> false
  }
}
