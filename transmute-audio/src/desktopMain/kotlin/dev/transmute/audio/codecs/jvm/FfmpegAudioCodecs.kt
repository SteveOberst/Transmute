package dev.transmute.audio.codecs.jvm

import dev.transmute.audio.AudioCodec
import dev.transmute.audio.AudioDecodeOptions
import dev.transmute.audio.AudioEncodeOptions
import dev.transmute.audio.AudioFormat
import dev.transmute.audio.AudioIR
import dev.transmute.core.Bytes
import dev.transmute.core.TransmuteContext
import dev.transmute.core.asBytes

// ---------------------------------------------------------------------------
// AAC codec (ADTS container, FFmpeg-backed)
// ---------------------------------------------------------------------------

/**
 * AAC codec for the JVM desktop target.
 *
 * Decodes and encodes AAC (ADTS) via the system FFmpeg binary.
 * Register only when [FfmpegAudioEngine.available] is `true`.
 */
internal class JvmAacCodec : AudioCodec {

  override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.Aac)
  override val encodableFormats: Set<AudioFormat> = setOf(AudioFormat.Aac)

  override fun sniff(data: Bytes): AudioFormat? {
    val bytes = data.data
    if (bytes.size < 4) return null
    val b0 = bytes[0].toInt() and 0xFF
    val b1 = bytes[1].toInt() and 0xFF
    // ADTS sync word (0xFFF) + layer must be 00 (distinguishes from MPEG audio).
    if (b0 == 0xFF && (b1 and 0xF6) == 0xF0) return AudioFormat.Aac
    return null
  }

  override suspend fun decode(source: Bytes, options: AudioDecodeOptions, context: TransmuteContext): AudioIR =
    FfmpegAudioEngine.decode(source.data, "aac", context)

  override suspend fun encode(
    ir: AudioIR,
    format: AudioFormat,
    options: AudioEncodeOptions,
    context: TransmuteContext,
  ): Bytes {
    require(format == AudioFormat.Aac) { "JvmAacCodec only supports AAC, got $format" }
    return FfmpegAudioEngine.encode(ir, "aac", "adts", "aac", "128k", context = context).asBytes()
  }
}

// ---------------------------------------------------------------------------
// M4A codec (AAC in MP4/IPOD container, FFmpeg-backed)
// ---------------------------------------------------------------------------

/**
 * M4A codec for the JVM desktop target.
 *
 * Decodes and encodes M4A (AAC inside an MP4 container) via the system FFmpeg
 * binary. Register only when [FfmpegAudioEngine.available] is `true`.
 */
internal class JvmM4aCodec : AudioCodec {

  override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.M4a)
  override val encodableFormats: Set<AudioFormat> = setOf(AudioFormat.M4a)

  override fun sniff(data: Bytes): AudioFormat? {
    val bytes = data.data
    if (bytes.size < 12) return null
    // ISO BMFF / MP4: bytes 4..7 = "ftyp"
    if (bytes[4] != 0x66.toByte() || bytes[5] != 0x74.toByte() ||
      bytes[6] != 0x79.toByte() || bytes[7] != 0x70.toByte()
    ) return null

    val brand = (8 until 12).map { bytes[it].toInt().toChar() }.joinToString("")
    if (brand == "M4A " || brand == "M4B " || brand == "M4P " || brand == "M4V ") return AudioFormat.M4a

    // Avoid misclassifying MP4 video as M4A if we see a video marker early.
    val window = bytes.copyOfRange(0, minOf(bytes.size, 256 * 1024)).decodeToString()
    val hasVideo = window.contains("vide") || window.contains("avc1") || window.contains("hvc1")
    if (hasVideo) return null

    return AudioFormat.M4a
  }

  override suspend fun decode(source: Bytes, options: AudioDecodeOptions, context: TransmuteContext): AudioIR =
    FfmpegAudioEngine.decode(source.data, "m4a", context)

  override suspend fun encode(
    ir: AudioIR,
    format: AudioFormat,
    options: AudioEncodeOptions,
    context: TransmuteContext,
  ): Bytes {
    require(format == AudioFormat.M4a) { "JvmM4aCodec only supports M4A, got $format" }
    return FfmpegAudioEngine.encode(ir, "aac", "ipod", "m4a", "128k", context = context).asBytes()
  }
}

// ---------------------------------------------------------------------------
// OPUS codec (Opus in OGG container, FFmpeg-backed)
// ---------------------------------------------------------------------------

/**
 * OPUS codec for the JVM desktop target.
 *
 * Decodes and encodes Opus (in an OGG container) via the system FFmpeg binary.
 * Requires `libopus` support compiled into FFmpeg (standard on most installs).
 * Register only when [FfmpegAudioEngine.available] is `true`.
 */
internal class JvmOpusCodec : AudioCodec {

  override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.Opus)
  override val encodableFormats: Set<AudioFormat> = setOf(AudioFormat.Opus)

  override fun sniff(data: Bytes): AudioFormat? {
    val bytes = data.data
    if (bytes.size < 36) return null
    // OGG container magic: "OggS"
    if (bytes[0] != 0x4F.toByte() || bytes[1] != 0x67.toByte() ||
      bytes[2] != 0x67.toByte() || bytes[3] != 0x53.toByte()
    ) return null
    // Opus identification header: "OpusHead" at typical first-page payload offset.
    if (bytes[28] == 0x4F.toByte() && bytes[29] == 0x70.toByte() &&
      bytes[30] == 0x75.toByte() && bytes[31] == 0x73.toByte() &&
      bytes[32] == 0x48.toByte() && bytes[33] == 0x65.toByte() &&
      bytes[34] == 0x61.toByte() && bytes[35] == 0x64.toByte()
    ) {
      return AudioFormat.Opus
    }
    return null
  }

  override suspend fun decode(source: Bytes, options: AudioDecodeOptions, context: TransmuteContext): AudioIR =
    FfmpegAudioEngine.decode(source.data, "opus", context)

  override suspend fun encode(
    ir: AudioIR,
    format: AudioFormat,
    options: AudioEncodeOptions,
    context: TransmuteContext,
  ): Bytes {
    require(format == AudioFormat.Opus) { "JvmOpusCodec only supports OPUS, got $format" }
    return FfmpegAudioEngine.encode(ir, "libopus", "ogg", "opus", "128k", context = context).asBytes()
  }
}
