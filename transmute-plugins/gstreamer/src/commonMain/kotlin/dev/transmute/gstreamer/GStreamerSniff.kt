package dev.transmute.gstreamer

import dev.transmute.audio.AudioFormat
import dev.transmute.image.ImageFormat
import dev.transmute.model.core.Bytes
import dev.transmute.video.VideoFormat

/**
 * Shared, platform-independent byte-level format sniffing utilities
 * used by all GStreamer codec implementations (Desktop, Android, iOS).
 *
 * Each function inspects the first N bytes of a media file / stream
 * and returns the detected format or `null`.
 */
internal object GStreamerSniff {

    // -- Audio ----------------------------------------------------------------

    /**
     * Detect ADTS-framed AAC.
     *
     * ADTS sync word: `0xFFF` (12 bits), followed by `layer == 00`.
     * Distinguishes from MPEG-1/2 audio where the layer field is non-zero.
     */
    fun sniffAac(data: Bytes): AudioFormat? {
        val bytes = data.data
        if (bytes.size < 4) return null
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        if (b0 == 0xFF && (b1 and 0xF6) == 0xF0) return AudioFormat.Aac
        return null
    }

    /**
     * Detect M4A (AAC in ISO BMFF / MP4 container).
     *
     * Looks for `ftyp` box at offset 4 with an M4A-family brand,
     * rejecting files that contain video track markers.
     */
    fun sniffM4a(data: Bytes): AudioFormat? {
        val bytes = data.data
        if (bytes.size < 12) return null
        if (!isFtyp(bytes)) return null

        val brand = brand4(bytes, 8)
        if (brand == "M4A " || brand == "M4B " || brand == "M4P " || brand == "M4V ") return AudioFormat.M4a

        // Avoid misclassifying MP4 video as M4A.
        val window = bytes.copyOfRange(0, minOf(bytes.size, 256 * 1024)).decodeToString()
        val hasVideo = window.contains("vide") || window.contains("avc1") || window.contains("hvc1")
        if (hasVideo) return null

        return AudioFormat.M4a
    }

    /**
     * Detect Opus in OGG container.
     *
     * OGG magic `OggS` at offset 0, `OpusHead` identification header at
     * offset 28.
     */
    fun sniffOpus(data: Bytes): AudioFormat? {
        val bytes = data.data
        if (bytes.size < 36) return null
        if (!isOggS(bytes)) return null
        if (bytes[28] == 0x4F.toByte() && bytes[29] == 0x70.toByte() &&
            bytes[30] == 0x75.toByte() && bytes[31] == 0x73.toByte() &&
            bytes[32] == 0x48.toByte() && bytes[33] == 0x65.toByte() &&
            bytes[34] == 0x61.toByte() && bytes[35] == 0x64.toByte()
        ) {
            return AudioFormat.Opus
        }
        return null
    }

    // -- Video ----------------------------------------------------------------

    /**
     * Detect MP4 (H.264 / H.265 / MPEG-4 container).
     *
     * Matches ISO BMFF `ftyp` box with common MP4 brands:
     * `isom`, `mp41`, `mp42`, `avc1`, `M4V `, `iso2`-`iso6`, `mmp4`, `3gp*`, `3g2*`.
     */
    fun sniffMp4(data: Bytes): VideoFormat? {
        val bytes = data.data
        if (bytes.size < 12) return null
        if (!isFtyp(bytes)) return null
        val brand = brand4(bytes, 8)
        return when {
            brand.startsWith("mp4") || brand == "isom" || brand == "M4V " ||
                brand == "avc1" || brand == "iso2" || brand == "iso5" ||
                brand == "iso6" || brand == "mmp4" -> VideoFormat.Mp4
            brand.startsWith("3gp") || brand.startsWith("3g2") -> VideoFormat.Mp4
            else -> null
        }
    }

    /**
     * Detect QuickTime MOV.
     *
     * Matches ISO BMFF `ftyp` box with brand `qt  `.
     */
    fun sniffMov(data: Bytes): VideoFormat? {
        val bytes = data.data
        if (bytes.size < 12) return null
        if (!isFtyp(bytes)) return null
        val brand = brand4(bytes, 8)
        return if (brand == "qt  ") VideoFormat.Mov else null
    }

    /**
     * Detect WebM (VP8/VP9 in EBML container).
     *
     * EBML header magic `0x1A45DFA3`. If the first 64 bytes contain
     * `"matroska"` it's classified as MKV instead.
     */
    fun sniffWebm(data: Bytes): VideoFormat? {
        val bytes = data.data
        if (bytes.size < 4) return null
        if (!isEbml(bytes)) return null
        if (bytes.size >= 40) {
            val content = bytes.copyOfRange(0, minOf(bytes.size, 64)).decodeToString()
            if (content.contains("matroska")) return null // MKV, not WebM
            if (content.contains("webm")) return VideoFormat.Webm
        }
        return VideoFormat.Webm
    }

    /**
     * Detect AVI (Audio Video Interleave).
     *
     * RIFF magic at offset 0, `AVI ` at offset 8.
     */
    fun sniffAvi(data: Bytes): VideoFormat? {
        val bytes = data.data
        if (bytes.size < 12) return null
        if (isRiff(bytes) &&
            bytes[8] == 'A'.code.toByte() && bytes[9] == 'V'.code.toByte() &&
            bytes[10] == 'I'.code.toByte() && bytes[11] == ' '.code.toByte()
        ) return VideoFormat.Avi
        return null
    }

    /**
     * Detect Matroska (MKV).
     *
     * EBML header + `"matroska"` doc-type string in the first 64 bytes.
     */
    fun sniffMkv(data: Bytes): VideoFormat? {
        val bytes = data.data
        if (bytes.size < 4) return null
        if (!isEbml(bytes)) return null
        if (bytes.size >= 40) {
            val content = bytes.copyOfRange(0, minOf(bytes.size, 64)).decodeToString()
            if (content.contains("matroska")) return VideoFormat.Mkv
        }
        return null
    }

    // -- Image ----------------------------------------------------------------

    /**
     * Detect HEIF/HEIC/AVIF from ISO BMFF `ftyp` box brand.
     */
    fun sniffImage(data: Bytes): ImageFormat? {
        val bytes = data.data
        if (bytes.size < 12) return null
        if (!isFtyp(bytes)) return null
        val brand = brand4(bytes, 8)
        return when (brand) {
            "heic", "heix" -> ImageFormat.Heic
            "mif1", "msf1" -> ImageFormat.Heif
            "hevc", "hevx" -> ImageFormat.Heic
            "avif", "avis" -> ImageFormat.Avif
            else -> null
        }
    }

    // -- Helpers --------------------------------------------------------------

    /** `true` when bytes 4-7 are `"ftyp"`. */
    private fun isFtyp(bytes: ByteArray): Boolean =
        bytes[4] == 0x66.toByte() && bytes[5] == 0x74.toByte() &&
            bytes[6] == 0x79.toByte() && bytes[7] == 0x70.toByte()

    /** `true` when bytes 0-3 are OGG magic `"OggS"`. */
    private fun isOggS(bytes: ByteArray): Boolean =
        bytes[0] == 0x4F.toByte() && bytes[1] == 0x67.toByte() &&
            bytes[2] == 0x67.toByte() && bytes[3] == 0x53.toByte()

    /** `true` when bytes 0-3 are EBML magic `0x1A45DFA3`. */
    private fun isEbml(bytes: ByteArray): Boolean =
        bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() &&
            bytes[2] == 0xDF.toByte() && bytes[3] == 0xA3.toByte()

    /** `true` when bytes 0-3 are `"RIFF"`. */
    private fun isRiff(bytes: ByteArray): Boolean =
        bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte()

    /** Read a 4-char ASCII brand from [bytes] at [offset]. */
    private fun brand4(bytes: ByteArray, offset: Int): String =
        (offset until offset + 4).map { bytes[it].toInt().toChar() }.joinToString("")
}
