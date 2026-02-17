package dev.transmute.audio.codecs.jvm

import com.jcraft.jorbis.VorbisFile
import com.jcraft.jorbis.VorbisFileAccess
import de.sciss.jump3r.lowlevel.LameEncoder
import dev.transmute.audio.AudioCodec
import dev.transmute.audio.AudioIR
import dev.transmute.audio.AudioSamples
import dev.transmute.core.AudioFormat
import dev.transmute.core.ConversionContext
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer
import org.jflac.FLACDecoder
import org.jflac.PCMProcessor
import org.jflac.metadata.StreamInfo
import org.jflac.util.ByteData
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioFormat as JvmAudioFormat

/**
 * MP3 codec for the JVM desktop target.
 *
 * Decodes MP3 via JLayer. Encodes to MP3 at 128 kbps via Jump3r (pure-Java LAME).
 */
class JvmMp3Codec : AudioCodec {

  override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.MP3)
  override val encodableFormats: Set<AudioFormat> = setOf(AudioFormat.MP3)

  override fun sniff(data: ByteArray): AudioFormat? {
    if (data.size < 3) return null
    // ID3v2 tag header
    if (data[0] == 0x49.toByte() && data[1] == 0x44.toByte() && data[2] == 0x33.toByte()) {
      return AudioFormat.MP3
    }
    // MPEG audio frame sync word (first 11 bits set).
    // Exclude layer==00 which is AAC ADTS, not MP3.
    if (data.size >= 2) {
      val b0 = data[0].toInt() and 0xFF
      val b1 = data[1].toInt() and 0xFF
      if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) {
        val layer = (b1 shr 1) and 0x03
        if (layer != 0) return AudioFormat.MP3
      }
    }
    return null
  }

  override suspend fun decode(source: ByteArray, context: ConversionContext): AudioIR {
    val bitstream = Bitstream(ByteArrayInputStream(source))
    val decoder = Decoder()

    var sampleRate = 0
    var channels = 0

    val floats = ArrayList<Float>(source.size) // heuristic

    try {
      while (true) {
        val header = bitstream.readFrame() ?: break
        val output = decoder.decodeFrame(header, bitstream) as? SampleBuffer
          ?: error("Unexpected MP3 decoder output")

        sampleRate = output.sampleFrequency
        channels = output.channelCount

        val pcm: ShortArray = output.buffer
        val len = output.bufferLength
        for (i in 0 until len) {
          floats.add(pcm[i] / 32768.0f)
        }

        bitstream.closeFrame()
      }
    } finally {
      runCatching { bitstream.close() }
    }

    require(sampleRate > 0 && channels > 0) { "Failed to decode MP3 (no frames?)" }

    val data = FloatArray(floats.size)
    for (i in floats.indices) data[i] = floats[i]

    val frameCount = data.size / channels
    val durationMs = (frameCount.toDouble() / sampleRate.toDouble() * 1000.0).toLong()

    return AudioIR(
      samples = AudioSamples(data = data, sampleRate = sampleRate, channelCount = channels),
      sampleRate = sampleRate,
      channelCount = channels,
      durationMs = durationMs,
    )
  }

  override suspend fun encode(ir: AudioIR, context: ConversionContext): ByteArray {
    val pcmBytes = floatToPcm16(ir.samples.data)

    val sourceFormat = JvmAudioFormat(
      ir.sampleRate.toFloat(),
      16,              // bits per sample
      ir.channelCount,
      true,            // signed
      false,           // little-endian
    )

    val lame = LameEncoder(
      sourceFormat,
      128,                             // bitrate kbps
      LameEncoder.CHANNEL_MODE_AUTO,
      LameEncoder.QUALITY_MIDDLE,
      false,                           // VBR
    )

    try {
      val bufSize = lame.getPCMBufferSize()
      val mp3Buf = ByteArray(bufSize)
      val out = java.io.ByteArrayOutputStream(pcmBytes.size / 4)

      var offset = 0
      while (offset < pcmBytes.size) {
        val chunk = minOf(bufSize, pcmBytes.size - offset)
        val written = lame.encodeBuffer(pcmBytes, offset, chunk, mp3Buf)
        if (written > 0) out.write(mp3Buf, 0, written)
        offset += chunk
      }

      val flushed = lame.encodeFinish(mp3Buf)
      if (flushed > 0) out.write(mp3Buf, 0, flushed)

      return out.toByteArray()
    } finally {
      lame.close()
    }
  }
}

/**
 * FLAC codec for the JVM desktop target.
 *
 * Decodes FLAC via jflac (pure Java). Encodes to FLAC via FFmpeg (if available).
 */
class JvmFlacCodec : AudioCodec {

  override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.FLAC)
  override val encodableFormats: Set<AudioFormat> =
    if (FfmpegAudioEngine.available) setOf(AudioFormat.FLAC) else emptySet()

  override fun sniff(data: ByteArray): AudioFormat? {
    if (data.size < 4) return null
    // FLAC stream marker: "fLaC" (0x664C6143)
    if (data[0] == 0x66.toByte() && data[1] == 0x4C.toByte() &&
      data[2] == 0x61.toByte() && data[3] == 0x43.toByte()
    ) {
      return AudioFormat.FLAC
    }
    return null
  }

  override suspend fun decode(source: ByteArray, context: ConversionContext): AudioIR {
    val decoder = FLACDecoder(ByteArrayInputStream(source))

    var sampleRate = 0
    var channels = 0
    var bitsPerSample = 0

    val floats = FloatArrayList()

    decoder.addPCMProcessor(object : PCMProcessor {
      override fun processStreamInfo(streamInfo: StreamInfo?) {
        if (streamInfo != null) {
          sampleRate = streamInfo.sampleRate
          channels = streamInfo.channels
          bitsPerSample = streamInfo.bitsPerSample
        }
      }

      override fun processPCM(byteData: ByteData?) {
        if (byteData == null) return

        val bits = bitsPerSample
        val bytesPerSample = (bits + 7) / 8
        val data = byteData.data
        val len = byteData.len

        val sampleCount = len / bytesPerSample
        var offset = 0

        val max = (1L shl (bits - 1)).toFloat()
        for (i in 0 until sampleCount) {
          val sampleInt = readLeSigned(data, offset, bytesPerSample)
          offset += bytesPerSample
          floats.add(sampleInt / max)
        }
      }
    })

    decoder.decode()

    require(sampleRate > 0 && channels > 0) { "Failed to decode FLAC" }

    val out = floats.toFloatArray()
    val frameCount = out.size / channels
    val durationMs = (frameCount.toDouble() / sampleRate.toDouble() * 1000.0).toLong()

    return AudioIR(
      samples = AudioSamples(data = out, sampleRate = sampleRate, channelCount = channels),
      sampleRate = sampleRate,
      channelCount = channels,
      durationMs = durationMs,
    )
  }

  override suspend fun encode(ir: AudioIR, context: ConversionContext): ByteArray {
    check(FfmpegAudioEngine.available) { "FLAC encoding requires FFmpeg on PATH" }
    return FfmpegAudioEngine.encode(
      ir, "flac", "flac", "flac",
      bitrate = null,
      extraArgs = listOf("-compression_level", "5"),
      context = context,
    )
  }

  private fun readLeSigned(bytes: ByteArray, offset: Int, size: Int): Int {
    var v = 0
    for (i in 0 until size) {
      v = v or ((bytes[offset + i].toInt() and 0xFF) shl (8 * i))
    }
    // sign extend
    val shift = 32 - size * 8
    return (v shl shift) shr shift
  }
}

/**
 * OGG/Vorbis codec for the JVM desktop target.
 *
 * Decodes OGG/Vorbis via jorbis (pure Java). Encodes via FFmpeg (if available).
 */
class JvmOggVorbisCodec : AudioCodec {

  override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.OGG)
  override val encodableFormats: Set<AudioFormat> =
    if (FfmpegAudioEngine.available) setOf(AudioFormat.OGG) else emptySet()

  override fun sniff(data: ByteArray): AudioFormat? {
    if (data.size < 4) return null
    // OGG container magic: "OggS"
    if (data[0] != 0x4F.toByte() || data[1] != 0x67.toByte() ||
      data[2] != 0x67.toByte() || data[3] != 0x53.toByte()
    ) return null
    // With enough data, confirm Vorbis identification header (type 0x01 + "vorbis").
    if (data.size >= 35) {
      if (data[28] == 0x01.toByte() &&
        data[29] == 0x76.toByte() && data[30] == 0x6F.toByte() &&
        data[31] == 0x72.toByte() && data[32] == 0x62.toByte() &&
        data[33] == 0x69.toByte() && data[34] == 0x73.toByte()
      ) {
        return AudioFormat.OGG
      }
      // Enough data but no Vorbis header — might be Opus or another OGG codec.
      return null
    }
    // Short OGG header (can't determine inner codec) — report as OGG container.
    return AudioFormat.OGG
  }

  override suspend fun decode(source: ByteArray, context: ConversionContext): AudioIR {
    val vorbis = VorbisFile(ByteArrayInputStream(source), ByteArray(0), 0)

    val info = vorbis.getInfo(0)
    val sampleRate = info.rate
    val channels = info.channels

    val pcmBytes = ByteArray(8192)
    val bitstream = IntArray(1)

    val floats = ArrayList<Float>(source.size) // heuristic

    try {
      while (true) {
        // VorbisFile.read returns raw PCM bytes.
        // Params: (buffer, offset, length, bigEndian, wordSize, bitstream)
        val bytesRead = VorbisFileAccess.read(vorbis, pcmBytes, 0, pcmBytes.size, 0, 2, bitstream)
        if (bytesRead <= 0) break

        var i = 0
        while (i + 1 < bytesRead) {
          val lo = pcmBytes[i].toInt() and 0xFF
          val hi = pcmBytes[i + 1].toInt()
          val sample16 = (hi shl 8) or lo
          floats.add(sample16.toShort() / 32768.0f)
          i += 2
        }
      }
    } finally {
      runCatching { vorbis.close() }
    }

    require(sampleRate > 0 && channels > 0) { "Failed to decode OGG/Vorbis" }

    val data = FloatArray(floats.size)
    for (idx in floats.indices) data[idx] = floats[idx]

    val frameCount = if (channels > 0) data.size / channels else 0
    val durationMs = if (sampleRate > 0) {
      (frameCount.toDouble() / sampleRate.toDouble() * 1000.0).toLong()
    } else {
      0L
    }

    return AudioIR(
      samples = AudioSamples(data = data, sampleRate = sampleRate, channelCount = channels),
      sampleRate = sampleRate,
      channelCount = channels,
      durationMs = durationMs,
    )
  }

  override suspend fun encode(ir: AudioIR, context: ConversionContext): ByteArray {
    check(FfmpegAudioEngine.available) { "OGG/Vorbis encoding requires FFmpeg on PATH" }
    return FfmpegAudioEngine.encode(
      ir, "libvorbis", "ogg", "ogg", "128k",
      context = context,
    )
  }
}

private fun floatToPcm16(samples: FloatArray): ByteArray {
  val pcm = ByteArray(samples.size * 2)
  for (i in samples.indices) {
    val s = (samples[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
    pcm[i * 2] = (s.toInt() and 0xFF).toByte()
    pcm[i * 2 + 1] = (s.toInt() shr 8 and 0xFF).toByte()
  }
  return pcm
}

private class FloatArrayList(initialCapacity: Int = 16) {
  private var data = FloatArray(initialCapacity)
  private var size = 0

  fun add(value: Float) {
    if (size == data.size) data = data.copyOf(maxOf(16, data.size * 2))
    data[size++] = value
  }

  fun toFloatArray(): FloatArray = data.copyOf(size)
}
