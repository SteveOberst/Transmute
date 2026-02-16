package dev.transmute.audio.codecs

import dev.transmute.audio.AudioDecoder
import dev.transmute.audio.AudioEncoder
import dev.transmute.audio.AudioIR
import dev.transmute.audio.AudioMetadata
import dev.transmute.audio.AudioSamples
import dev.transmute.core.AudioFormat
import dev.transmute.core.ConversionContext

/**
 * Pure Kotlin WAV decoder supporting PCM (8/16/24/32-bit) and IEEE float formats.
 *
 * This implementation works on all Kotlin Multiplatform targets without native dependencies.
 */
class WavDecoder : AudioDecoder {

  override val supportedFormats: Set<AudioFormat> = setOf(AudioFormat.WAV)

  override suspend fun decode(source: ByteArray, context: ConversionContext): AudioIR {
    require(source.size >= 44) { "WAV file too small: ${source.size} bytes" }

    // Parse RIFF header
    val riff = source.readString(0, 4)
    require(riff == "RIFF") { "Invalid RIFF header: $riff" }

    val wave = source.readString(8, 4)
    require(wave == "WAVE") { "Invalid WAVE format: $wave" }

    // Find fmt chunk
    var pos = 12
    var audioFormat = 0
    var channelCount = 0
    var sampleRate = 0
    var bitsPerSample = 0
    var dataBytes: ByteArray? = null

    while (pos < source.size - 8) {
      val chunkId = source.readString(pos, 4)
      val chunkSize = source.readInt32LE(pos + 4)

      when (chunkId) {
        "fmt " -> {
          audioFormat = source.readInt16LE(pos + 8)
          channelCount = source.readInt16LE(pos + 10)
          sampleRate = source.readInt32LE(pos + 12)
          // byteRate = source.readInt32LE(pos + 16)
          // blockAlign = source.readInt16LE(pos + 20)
          bitsPerSample = source.readInt16LE(pos + 22)
        }
        "data" -> {
          dataBytes = source.copyOfRange(pos + 8, minOf(pos + 8 + chunkSize, source.size))
        }
      }
      pos += 8 + chunkSize
      // WAV chunks are word-aligned
      if (chunkSize % 2 != 0) pos++
    }

    requireNotNull(dataBytes) { "No data chunk found in WAV file" }
    require(audioFormat == 1 || audioFormat == 3) {
      "Unsupported WAV format: $audioFormat (only PCM=1 and IEEE_FLOAT=3 supported)"
    }

    val samples = when {
      audioFormat == 3 && bitsPerSample == 32 -> decodeFloat32(dataBytes, channelCount)
      audioFormat == 1 && bitsPerSample == 8 -> decode8Bit(dataBytes, channelCount)
      audioFormat == 1 && bitsPerSample == 16 -> decode16Bit(dataBytes, channelCount)
      audioFormat == 1 && bitsPerSample == 24 -> decode24Bit(dataBytes, channelCount)
      audioFormat == 1 && bitsPerSample == 32 -> decode32Bit(dataBytes, channelCount)
      else -> error("Unsupported bit depth: $bitsPerSample")
    }

    val durationMs = (samples.size.toLong() * 1000L) / (sampleRate * channelCount)

    return AudioIR(
      samples = AudioSamples(
        data = samples,
        sampleRate = sampleRate,
        channelCount = channelCount,
      ),
      sampleRate = sampleRate,
      channelCount = channelCount,
      durationMs = durationMs,
      metadata = AudioMetadata(
        durationMs = durationMs,
        bitrateKbps = (sampleRate * channelCount * bitsPerSample) / 1000,
      ),
    )
  }

  private fun decode8Bit(data: ByteArray, channels: Int): FloatArray {
    val samples = FloatArray(data.size)
    for (i in data.indices) {
      // 8-bit WAV is unsigned (0-255), center at 128
      samples[i] = (data[i].toInt() and 0xFF) / 128f - 1f
    }
    return samples
  }

  private fun decode16Bit(data: ByteArray, channels: Int): FloatArray {
    val sampleCount = data.size / 2
    val samples = FloatArray(sampleCount)
    for (i in 0 until sampleCount) {
      val sample = data.readInt16LE(i * 2).toShort()  // Convert to signed
      samples[i] = sample / 32768f
    }
    return samples
  }

  private fun decode24Bit(data: ByteArray, channels: Int): FloatArray {
    val sampleCount = data.size / 3
    val samples = FloatArray(sampleCount)
    for (i in 0 until sampleCount) {
      val offset = i * 3
      val sample = (data[offset].toInt() and 0xFF) or
        ((data[offset + 1].toInt() and 0xFF) shl 8) or
        ((data[offset + 2].toInt()) shl 16)
      samples[i] = sample / 8388608f
    }
    return samples
  }

  private fun decode32Bit(data: ByteArray, channels: Int): FloatArray {
    val sampleCount = data.size / 4
    val samples = FloatArray(sampleCount)
    for (i in 0 until sampleCount) {
      val sample = data.readInt32LE(i * 4)
      samples[i] = sample / 2147483648f
    }
    return samples
  }

  private fun decodeFloat32(data: ByteArray, channels: Int): FloatArray {
    val sampleCount = data.size / 4
    val samples = FloatArray(sampleCount)
    for (i in 0 until sampleCount) {
      samples[i] = Float.fromBits(data.readInt32LE(i * 4))
    }
    return samples
  }
}

/**
 * Pure Kotlin WAV encoder producing 16-bit PCM WAV files.
 */
class WavEncoder : AudioEncoder {

  override val supportedFormats: Set<AudioFormat> = setOf(AudioFormat.WAV)

  override suspend fun encode(ir: AudioIR, context: ConversionContext): ByteArray {
    val samples = ir.samples.data
    val sampleRate = ir.sampleRate
    val channelCount = ir.channelCount
    val bitsPerSample = 16
    val byteRate = sampleRate * channelCount * (bitsPerSample / 8)
    val blockAlign = channelCount * (bitsPerSample / 8)
    val dataSize = samples.size * 2 // 16-bit = 2 bytes per sample

    val headerSize = 44
    val fileSize = headerSize + dataSize - 8

    val output = ByteArray(headerSize + dataSize)

    // RIFF header
    output.writeString(0, "RIFF")
    output.writeInt32LE(4, fileSize)
    output.writeString(8, "WAVE")

    // fmt chunk
    output.writeString(12, "fmt ")
    output.writeInt32LE(16, 16) // chunk size
    output.writeInt16LE(20, 1) // PCM format
    output.writeInt16LE(22, channelCount)
    output.writeInt32LE(24, sampleRate)
    output.writeInt32LE(28, byteRate)
    output.writeInt16LE(32, blockAlign)
    output.writeInt16LE(34, bitsPerSample)

    // data chunk
    output.writeString(36, "data")
    output.writeInt32LE(40, dataSize)

    // Write samples as 16-bit PCM
    for (i in samples.indices) {
      val clamped = samples[i].coerceIn(-1f, 1f)
      val pcm = (clamped * 32767f).toInt().toShort()
      output.writeInt16LE(44 + i * 2, pcm.toInt())
    }

    return output
  }
}

// --- ByteArray extensions for little-endian I/O ---

private fun ByteArray.readString(offset: Int, length: Int): String {
  return (offset until offset + length).map { this[it].toInt().toChar() }.joinToString("")
}

private fun ByteArray.readInt16LE(offset: Int): Int {
  return (this[offset].toInt() and 0xFF) or
    ((this[offset + 1].toInt() and 0xFF) shl 8)
}

private fun ByteArray.readInt32LE(offset: Int): Int {
  return (this[offset].toInt() and 0xFF) or
    ((this[offset + 1].toInt() and 0xFF) shl 8) or
    ((this[offset + 2].toInt() and 0xFF) shl 16) or
    ((this[offset + 3].toInt() and 0xFF) shl 24)
}

private fun ByteArray.writeString(offset: Int, value: String) {
  for (i in value.indices) {
    this[offset + i] = value[i].code.toByte()
  }
}

private fun ByteArray.writeInt16LE(offset: Int, value: Int) {
  this[offset] = (value and 0xFF).toByte()
  this[offset + 1] = ((value shr 8) and 0xFF).toByte()
}

private fun ByteArray.writeInt32LE(offset: Int, value: Int) {
  this[offset] = (value and 0xFF).toByte()
  this[offset + 1] = ((value shr 8) and 0xFF).toByte()
  this[offset + 2] = ((value shr 16) and 0xFF).toByte()
  this[offset + 3] = ((value shr 24) and 0xFF).toByte()
}
