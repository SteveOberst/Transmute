package dev.transmute.audio.codecs

import dev.transmute.audio.AudioDecodeOptions
import dev.transmute.audio.AudioDecoder
import dev.transmute.audio.AudioEncodeOptions
import dev.transmute.audio.AudioEncoder
import dev.transmute.audio.AudioFormat
import dev.transmute.audio.AudioIR
import dev.transmute.audio.AudioMetadata
import dev.transmute.audio.AudioSamples
import dev.transmute.common.PipelineContext
import dev.transmute.io.TSource
import dev.transmute.model.core.Bytes

/**
 * Pure Kotlin WAV decoder supporting PCM (8/16/24/32-bit) and IEEE float formats.
 *
 * This implementation works on all Kotlin Multiplatform targets without native dependencies.
 */
class WavDecoder : AudioDecoder {

  override val supportedFormats: Set<AudioFormat> = setOf(AudioFormat.Wav)

  override suspend fun decode(source: TSource, options: AudioDecodeOptions, context: PipelineContext): AudioIR {
    val bytes = source.readAll()
    require(bytes.size >= 44) { "WAV file too small: ${bytes.size} bytes" }

    // Parse RIFF header
    val riff = bytes.readString(0, 4)
    require(riff == "RIFF") { "Invalid RIFF header: $riff" }

    val wave = bytes.readString(8, 4)
    require(wave == "WAVE") { "Invalid WAVE format: $wave" }

    // Find fmt chunk
    var pos = 12
    var audioFormat = 0
    var channelCount = 0
    var sampleRate = 0
    var bitsPerSample = 0
    var dataBytes: ByteArray? = null

    while (pos < bytes.size - 8) {
      val chunkId = bytes.readString(pos, 4)
      val chunkSize = bytes.readInt32LE(pos + 4)

      when (chunkId) {
        "fmt " -> {
          audioFormat = bytes.readInt16LE(pos + 8)
          channelCount = bytes.readInt16LE(pos + 10)
          sampleRate = bytes.readInt32LE(pos + 12)
          // byteRate = bytes.readInt32LE(pos + 16)
          // blockAlign = bytes.readInt16LE(pos + 20)
          bitsPerSample = bytes.readInt16LE(pos + 22)
        }
        "data" -> {
          dataBytes = bytes.copyOfRange(pos + 8, minOf(pos + 8 + chunkSize, bytes.size))
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

    val bytesPerSample = when {
      audioFormat == 3 && bitsPerSample == 32 -> 4
      audioFormat == 1 && bitsPerSample == 8 -> 1
      audioFormat == 1 && bitsPerSample == 16 -> 2
      audioFormat == 1 && bitsPerSample == 24 -> 3
      audioFormat == 1 && bitsPerSample == 32 -> 4
      else -> error("Unsupported bit depth: $bitsPerSample")
    }

    val timeRange = options.decodeRange?.timeframe()
    val rangedDataBytes = timeRange?.let { range ->
      val totalSamples = dataBytes.size / bytesPerSample
      val startFrame = ((range.startMs * sampleRate) / 1000L).toInt().coerceAtLeast(0)
      val endFrame = ((range.endMsExclusive * sampleRate) / 1000L).toInt().coerceAtLeast(startFrame)
      val startSample = (startFrame * channelCount).coerceAtMost(totalSamples)
      val endSample = (endFrame * channelCount).coerceAtMost(totalSamples)
      val startByte = startSample * bytesPerSample
      val endByte = endSample * bytesPerSample
      if (startByte >= endByte) ByteArray(0) else dataBytes.copyOfRange(startByte, endByte)
    } ?: dataBytes

    val samples = when {
      audioFormat == 3 && bitsPerSample == 32 -> decodeFloat32(rangedDataBytes, channelCount)
      audioFormat == 1 && bitsPerSample == 8 -> decode8Bit(rangedDataBytes, channelCount)
      audioFormat == 1 && bitsPerSample == 16 -> decode16Bit(rangedDataBytes, channelCount)
      audioFormat == 1 && bitsPerSample == 24 -> decode24Bit(rangedDataBytes, channelCount)
      audioFormat == 1 && bitsPerSample == 32 -> decode32Bit(rangedDataBytes, channelCount)
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
      val sample = data.readInt16LE(i * 2).toShort() // Convert to signed
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

  override val supportedFormats: Set<AudioFormat> = setOf(AudioFormat.Wav)

  override suspend fun encode(ir: AudioIR, format: AudioFormat, options: AudioEncodeOptions, context: PipelineContext): Bytes {
    require(format == AudioFormat.Wav) { "WavEncoder only supports WAV, got $format" }
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

    return Bytes(output)
  }
}

// --- ByteArray extensions for little-endian I/O ---

private fun ByteArray.readString(offset: Int, length: Int): String = (offset until offset + length).map {
  this[it].toInt().toChar()
}.joinToString("")

private fun ByteArray.readInt16LE(offset: Int): Int = (this[offset].toInt() and 0xFF) or
  ((this[offset + 1].toInt() and 0xFF) shl 8)

private fun ByteArray.readInt32LE(offset: Int): Int = (this[offset].toInt() and 0xFF) or
  ((this[offset + 1].toInt() and 0xFF) shl 8) or
  ((this[offset + 2].toInt() and 0xFF) shl 16) or
  ((this[offset + 3].toInt() and 0xFF) shl 24)

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
