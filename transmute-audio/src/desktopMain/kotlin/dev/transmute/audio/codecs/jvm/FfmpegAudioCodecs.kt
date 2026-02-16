package dev.transmute.audio.codecs.jvm

import dev.transmute.audio.AudioCodec
import dev.transmute.audio.AudioIR
import dev.transmute.core.AudioFormat
import dev.transmute.core.ConversionContext

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

  override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.AAC)
  override val encodableFormats: Set<AudioFormat> = setOf(AudioFormat.AAC)

  override fun sniff(data: ByteArray): AudioFormat? {
    if (data.size < 4) return null
    val b0 = data[0].toInt() and 0xFF
    val b1 = data[1].toInt() and 0xFF
    // ADTS sync word (0xFFF) + layer must be 00 (distinguishes from MPEG audio).
    if (b0 == 0xFF && (b1 and 0xF6) == 0xF0) return AudioFormat.AAC
    return null
  }

  override suspend fun decode(source: ByteArray, context: ConversionContext): AudioIR =
    FfmpegAudioEngine.decode(source, "aac", context)

  override suspend fun encode(ir: AudioIR, context: ConversionContext): ByteArray =
    FfmpegAudioEngine.encode(ir, "aac", "adts", "aac", "128k", context = context)
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

  override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.M4A)
  override val encodableFormats: Set<AudioFormat> = setOf(AudioFormat.M4A)

  override fun sniff(data: ByteArray): AudioFormat? {
    if (data.size < 8) return null
    // ISO BMFF / MP4: bytes 4..7 = "ftyp"
    if (data[4] == 0x66.toByte() && data[5] == 0x74.toByte() &&
      data[6] == 0x79.toByte() && data[7] == 0x70.toByte()
    ) {
      return AudioFormat.M4A
    }
    return null
  }

  override suspend fun decode(source: ByteArray, context: ConversionContext): AudioIR =
    FfmpegAudioEngine.decode(source, "m4a", context)

  override suspend fun encode(ir: AudioIR, context: ConversionContext): ByteArray =
    FfmpegAudioEngine.encode(ir, "aac", "ipod", "m4a", "128k", context = context)
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

  override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.OPUS)
  override val encodableFormats: Set<AudioFormat> = setOf(AudioFormat.OPUS)

  override fun sniff(data: ByteArray): AudioFormat? {
    if (data.size < 36) return null
    // OGG container magic: "OggS"
    if (data[0] != 0x4F.toByte() || data[1] != 0x67.toByte() ||
      data[2] != 0x67.toByte() || data[3] != 0x53.toByte()
    ) return null
    // Opus identification header: "OpusHead" at typical first-page payload offset.
    if (data[28] == 0x4F.toByte() && data[29] == 0x70.toByte() &&
      data[30] == 0x75.toByte() && data[31] == 0x73.toByte() &&
      data[32] == 0x48.toByte() && data[33] == 0x65.toByte() &&
      data[34] == 0x61.toByte() && data[35] == 0x64.toByte()
    ) {
      return AudioFormat.OPUS
    }
    return null
  }

  override suspend fun decode(source: ByteArray, context: ConversionContext): AudioIR =
    FfmpegAudioEngine.decode(source, "opus", context)

  override suspend fun encode(ir: AudioIR, context: ConversionContext): ByteArray =
    FfmpegAudioEngine.encode(ir, "libopus", "ogg", "opus", "128k", context = context)
}
