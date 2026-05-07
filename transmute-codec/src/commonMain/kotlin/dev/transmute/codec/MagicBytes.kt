package dev.transmute.codec

/**
 * Shared magic-byte helpers used by all format detectors.
 *
 * Centralises the low-level container identification logic (ISO BMFF `ftyp`,
 * RIFF, EBML, Ogg) so each domain detector only needs to map container-level
 * results to its own format type.
 */
object MagicBytes {

  private fun ByteArray.decodeSlice(startIndex: Int, length: Int): String =
    copyOfRange(startIndex, startIndex + length).decodeToString()

  // -- ISO Base Media File Format (MP4 / MOV / M4A / HEIF / AVIF) ----------

  /**
   * Returns `true` if [data] starts with an ISO BMFF `ftyp` box.
   * Requires at least 12 bytes.
   */
  fun isIsoBmff(data: ByteArray): Boolean = data.size >= 12 &&
    data[4] == 0x66.toByte() &&
    // 'f'
    data[5] == 0x74.toByte() &&
    // 't'
    data[6] == 0x79.toByte() &&
    // 'y'
    data[7] == 0x70.toByte() // 'p'

  /**
   * Reads the 4-character major brand from an ISO BMFF `ftyp` box,
   * or `null` if the data is too short or not ISO BMFF.
   */
  fun ftypBrand(data: ByteArray): String? {
    if (!isIsoBmff(data)) return null
    return data.decodeSlice(8, 4)
  }

  // -- RIFF (WAV / AVI / WebP) ---------------------------------------------

  /**
   * Returns `true` if [data] starts with a RIFF container header.
   * Requires at least 12 bytes for a useful result (includes the sub-type).
   */
  fun isRiff(data: ByteArray): Boolean = data.size >= 12 &&
    data[0] == 0x52.toByte() &&
    // 'R'
    data[1] == 0x49.toByte() &&
    // 'I'
    data[2] == 0x46.toByte() &&
    // 'F'
    data[3] == 0x46.toByte() // 'F'

  /**
   * Reads the 4-character RIFF sub-type (e.g. `"WAVE"`, `"AVI "`, `"WEBP"`),
   * or `null` if the data is not a RIFF container.
   */
  fun riffType(data: ByteArray): String? {
    if (!isRiff(data)) return null
    return data.decodeSlice(8, 4)
  }

  // -- EBML (WebM / Matroska) -----------------------------------------------

  /**
   * Returns `true` if [data] starts with an EBML header (0x1A45DFA3).
   */
  fun isEbml(data: ByteArray): Boolean = data.size >= 4 &&
    data[0] == 0x1A.toByte() &&
    data[1] == 0x45.toByte() &&
    data[2] == 0xDF.toByte() &&
    data[3] == 0xA3.toByte()

  /**
   * Attempts to read the EBML DocType from the header region.
   *
   * Scans the first ~128 bytes for the DocType element (ID `0x4282`),
   * reads its VINT-encoded length, and returns the string value.
   * Returns `null` if no valid DocType is found.
   */
  fun ebmlDocType(data: ByteArray): String? {
    if (!isEbml(data)) return null
    val searchEnd = minOf(data.size, 128)
    var pos = 0
    while (pos + 3 < searchEnd) {
      // DocType element ID = 0x4282 (2 bytes)
      if (data[pos] == 0x42.toByte() && data[pos + 1] == 0x82.toByte()) {
        pos += 2
        if (pos >= searchEnd) return null
        val first = data[pos].toInt() and 0xFF
        val sizeLen = when {
          first and 0x80 != 0 -> 1
          first and 0x40 != 0 -> 2
          else -> return null
        }
        val mask = 0xFF shr sizeLen
        var size = (first and mask).toLong()
        for (i in 1 until sizeLen) {
          if (pos + i >= searchEnd) return null
          size = (size shl 8) or (data[pos + i].toLong() and 0xFF)
        }
        pos += sizeLen
        val end = minOf(pos + size.toInt(), data.size)
        if (end <= pos) return null
        return data.decodeSlice(pos, end - pos)
      }
      pos++
    }
    return null
  }

  // -- Ogg ------------------------------------------------------------------

  /**
   * Returns `true` if [data] starts with the Ogg page sync pattern (`OggS`).
   */
  fun isOgg(data: ByteArray): Boolean = data.size >= 4 &&
    data[0] == 0x4F.toByte() &&
    // 'O'
    data[1] == 0x67.toByte() &&
    // 'g'
    data[2] == 0x67.toByte() &&
    // 'g'
    data[3] == 0x53.toByte() // 'S'

  /**
   * Returns `true` if the Ogg stream contains an Opus header
   * (`"OpusHead"` at byte offset 28).
   */
  fun isOggOpus(data: ByteArray): Boolean {
    if (!isOgg(data) || data.size < 36) return false
    return data.decodeSlice(28, 8) == "OpusHead"
  }
}
