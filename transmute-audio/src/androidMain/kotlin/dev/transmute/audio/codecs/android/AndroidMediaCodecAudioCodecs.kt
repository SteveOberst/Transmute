package dev.transmute.audio.codecs.android

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import de.sciss.jump3r.mp3.BitStream
import de.sciss.jump3r.mp3.GainAnalysis
import de.sciss.jump3r.mp3.ID3Tag
import de.sciss.jump3r.mp3.Lame
import de.sciss.jump3r.mp3.Presets
import de.sciss.jump3r.mp3.Quantize
import de.sciss.jump3r.mp3.QuantizePVT
import de.sciss.jump3r.mp3.Reservoir
import de.sciss.jump3r.mp3.Takehiro
import de.sciss.jump3r.mp3.VBRTag
import de.sciss.jump3r.mp3.Version
import de.sciss.jump3r.mpg.Common
import de.sciss.jump3r.mpg.Interface
import de.sciss.jump3r.mpg.MPGLib
import dev.transmute.audio.AudioCodec
import dev.transmute.audio.AudioDecoder
import dev.transmute.audio.AudioIR
import dev.transmute.audio.AudioSamples
import dev.transmute.core.AudioFormat
import dev.transmute.core.ConversionContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

private class ByteArrayMediaDataSource(private val bytes: ByteArray) : MediaDataSource() {
  override fun close() {}

  override fun getSize(): Long = bytes.size.toLong()

  override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
    if (position >= bytes.size) return -1
    val count = minOf(size, bytes.size - position.toInt())
    System.arraycopy(bytes, position.toInt(), buffer, offset, count)
    return count
  }
}

// ---------------------------------------------------------------------------
// Shared decode logic — used by both decode-only and full codec classes.
// ---------------------------------------------------------------------------

/**
 * Decodes any audio format supported by Android's [MediaCodec] pipeline
 * into an [AudioIR] of interleaved float samples.
 */
private suspend fun decodeWithMediaCodec(source: ByteArray, context: ConversionContext): AudioIR {
  val extractor = MediaExtractor()
  val dataSource = ByteArrayMediaDataSource(source)

  try {
    extractor.setDataSource(dataSource)

    val trackIndex = (0 until extractor.trackCount)
      .firstOrNull { idx ->
        val mf = extractor.getTrackFormat(idx)
        val mime = mf.getString(MediaFormat.KEY_MIME)
        mime?.startsWith("audio/") == true
      } ?: error("No audio track found")

    extractor.selectTrack(trackIndex)

    val inFormat = extractor.getTrackFormat(trackIndex)
    val mime = inFormat.getString(MediaFormat.KEY_MIME) ?: error("Missing mime")

    val codec = MediaCodec.createDecoderByType(mime)
    try {
      codec.configure(inFormat, null, null, 0)
      codec.start()

      val bufferInfo = MediaCodec.BufferInfo()

      var sampleRate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
      var channels = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
      var pcmEncoding = 2 // ENCODING_PCM_16BIT

      val out = FloatArrayList()
      var sawInputEos = false
      var sawOutputEos = false

      while (!sawOutputEos) {
        if (!sawInputEos) {
          val inputIndex = codec.dequeueInputBuffer(10_000)
          if (inputIndex >= 0) {
            val inputBuffer = codec.getInputBuffer(inputIndex) ?: continue
            inputBuffer.clear()

            val sampleSize = extractor.readSampleData(inputBuffer, 0)
            if (sampleSize < 0) {
              codec.queueInputBuffer(
                inputIndex,
                0,
                0,
                0L,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
              )
              sawInputEos = true
            } else {
              val ptsUs = extractor.sampleTime
              codec.queueInputBuffer(inputIndex, 0, sampleSize, ptsUs, 0)
              extractor.advance()
            }
          }
        }

        val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
        when {
          outputIndex >= 0 -> {
            val outputBuffer = codec.getOutputBuffer(outputIndex)
            if (outputBuffer != null && bufferInfo.size > 0) {
              outputBuffer.position(bufferInfo.offset)
              outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

              when (pcmEncoding) {
                4 -> { // ENCODING_PCM_FLOAT
                  val floats = outputBuffer.slice().order(ByteOrder.nativeOrder()).asFloatBuffer()
                  val tmp = FloatArray(floats.remaining())
                  floats.get(tmp)
                  out.addAll(tmp)
                }

                else -> {
                  // Assume 16-bit little-endian PCM.
                  val shorts = outputBuffer.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                  val count = shorts.remaining()
                  for (i in 0 until count) {
                    out.add(shorts.get(i) / 32768.0f)
                  }
                }
              }
            }

            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
              sawOutputEos = true
            }

            codec.releaseOutputBuffer(outputIndex, false)
          }

          outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
            val newFormat = codec.outputFormat
            if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
              sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            }
            if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
              channels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            }
            if (newFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
              pcmEncoding = newFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
            }
          }

          outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
            // keep looping
          }
        }
      }

      val data = out.toFloatArray()
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
    } finally {
      runCatching { codec.stop() }
      runCatching { codec.release() }
    }
  } finally {
    runCatching { extractor.release() }
    runCatching { dataSource.close() }
  }
}

// ---------------------------------------------------------------------------
// Shared encode helpers
// ---------------------------------------------------------------------------

private fun floatToPcm16(samples: FloatArray): ByteArray {
  val pcm = ByteArray(samples.size * 2)
  for (i in samples.indices) {
    val s = (samples[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
    pcm[i * 2] = (s.toInt() and 0xFF).toByte()
    pcm[i * 2 + 1] = (s.toInt() shr 8 and 0xFF).toByte()
  }
  return pcm
}

/**
 * Builds a 7-byte ADTS header for one AAC frame.
 *
 * ADTS (Audio Data Transport Stream) wraps each encoded AAC frame
 * with sync info, profile, sample rate, and channel config so the
 * file can be played without a container.
 */
private fun buildAdtsHeader(frameLength: Int, sampleRate: Int, channelCount: Int): ByteArray {
  val totalLength = frameLength + 7

  val freqIndex = when (sampleRate) {
    96000 -> 0; 88200 -> 1; 64000 -> 2; 48000 -> 3
    44100 -> 4; 32000 -> 5; 24000 -> 6; 22050 -> 7
    16000 -> 8; 12000 -> 9; 11025 -> 10; 8000 -> 11
    7350 -> 12; else -> 4
  }

  val header = ByteArray(7)
  header[0] = 0xFF.toByte()
  header[1] = 0xF9.toByte() // MPEG-4, Layer 0, no CRC
  header[2] = ((1 shl 6) or (freqIndex shl 2) or (channelCount shr 2)).toByte()
  header[3] = ((channelCount and 3 shl 6) or (totalLength shr 11)).toByte()
  header[4] = ((totalLength shr 3) and 0xFF).toByte()
  header[5] = ((totalLength and 7 shl 5) or 0x1F).toByte()
  header[6] = 0xFC.toByte()

  return header
}

// ---------------------------------------------------------------------------
// Decode-only codecs — formats where Android has no encoder.
// ---------------------------------------------------------------------------

internal abstract class AndroidMediaCodecAudioDecoder(
  private val format: AudioFormat,
) : AudioDecoder {

  override val supportedFormats: Set<AudioFormat> = setOf(format)

  override suspend fun decode(source: ByteArray, context: ConversionContext): AudioIR =
    decodeWithMediaCodec(source, context)
}

internal class AndroidMp3Codec : AudioCodec {

  override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.MP3)
  override val encodableFormats: Set<AudioFormat> = setOf(AudioFormat.MP3)

  override fun sniff(data: ByteArray): AudioFormat? {
    if (data.size < 3) return null
    // ID3v2 tag header
    if (data[0] == 0x49.toByte() && data[1] == 0x44.toByte() && data[2] == 0x33.toByte()) {
      return AudioFormat.MP3
    }
    // MPEG audio frame sync word (first 11 bits set)
    if (data.size >= 2 && (data[0].toInt() and 0xFF) == 0xFF && (data[1].toInt() and 0xE0) == 0xE0) {
      return AudioFormat.MP3
    }
    return null
  }

  override suspend fun decode(source: ByteArray, context: ConversionContext): AudioIR =
    decodeWithMediaCodec(source, context)

  override suspend fun encode(ir: AudioIR, context: ConversionContext): ByteArray {
    val samples = ir.samples.data
    val channels = ir.channelCount
    val sampleRate = ir.sampleRate
    val frameCount = samples.size / channels

    // Split interleaved float samples into per-channel int32 buffers.
    // LAME expects values in the upper 16 bits of int32 (i.e. shifted << 16).
    val left = IntArray(frameCount)
    val right = IntArray(frameCount)
    for (f in 0 until frameCount) {
      left[f] = (samples[f * channels].coerceIn(-1f, 1f) * 32767f).toInt() shl 16
      if (channels >= 2) {
        right[f] = (samples[f * channels + 1].coerceIn(-1f, 1f) * 32767f).toInt() shl 16
      } else {
        right[f] = left[f]
      }
    }

    // Initialize LAME engine (low-level API, no javax.sound dependency).
    val lame = Lame()
    val ga = GainAnalysis()
    val bs = BitStream()
    val p = Presets()
    val qupvt = QuantizePVT()
    val qu = Quantize()
    val vbr = VBRTag()
    val ver = Version()
    val id3 = ID3Tag()
    val rv = Reservoir()
    val tak = Takehiro()
    val mpg = MPGLib()
    val intf = Interface()
    val common = Common()

    lame.setModules(ga, bs, p, qupvt, qu, vbr, ver, id3, mpg)
    bs.setModules(ga, mpg, ver, vbr)
    id3.setModules(bs, ver)
    p.setModules(lame)
    qu.setModules(bs, rv, qupvt, tak)
    qupvt.setModules(tak, rv, lame.enc.psy)
    rv.setModules(bs)
    tak.setModules(qupvt)
    mpg.setModules(intf, common)
    intf.setModules(vbr, common)

    val gfp = lame.lame_init()
    gfp.num_channels = channels
    gfp.in_samplerate = sampleRate
    gfp.brate = 128
    gfp.quality = 5 // QUALITY_MIDDLE
    gfp.write_id3tag_automatic = false
    gfp.findReplayGain = true
    id3.id3tag_init(gfp)

    val rc = lame.lame_init_params(gfp)
    check(rc >= 0) { "LAME init_params failed ($rc)" }

    try {
      val mp3BufSize = ((1.25 * frameCount) + 7200).toInt()
      val mp3Buf = ByteArray(mp3BufSize)
      val out = ByteArrayOutputStream(mp3BufSize)

      val written = lame.lame_encode_buffer_int(
        gfp, left, right, frameCount, mp3Buf, 0, mp3BufSize,
      )
      if (written > 0) out.write(mp3Buf.copyOf(written))

      val flushed = lame.lame_encode_flush(gfp, mp3Buf, 0, mp3BufSize)
      if (flushed > 0) out.write(mp3Buf.copyOf(flushed))

      return out.toByteArray()
    } finally {
      lame.lame_close(gfp)
    }
  }
}

internal class AndroidOggDecoder : AndroidMediaCodecAudioDecoder(AudioFormat.OGG)

// ---------------------------------------------------------------------------
// Full codecs — formats we can both decode AND encode on Android.
// ---------------------------------------------------------------------------

/**
 * FLAC codec using Android's [MediaCodec].
 *
 * Decodes FLAC via the standard [MediaExtractor] / [MediaCodec] pipeline.
 * Encodes to FLAC using [MediaCodec] (available since API 21).
 */
internal class AndroidFlacCodec : AudioCodec {

  override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.FLAC)
  override val encodableFormats: Set<AudioFormat> = setOf(AudioFormat.FLAC)

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

  override suspend fun decode(source: ByteArray, context: ConversionContext): AudioIR =
    decodeWithMediaCodec(source, context)

  override suspend fun encode(ir: AudioIR, context: ConversionContext): ByteArray {
    val pcmBytes = floatToPcm16(ir.samples.data)

    val mime = "audio/flac"
    val format = MediaFormat.createAudioFormat(mime, ir.sampleRate, ir.channelCount).apply {
      setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, 5)
    }

    val codec = MediaCodec.createEncoderByType(mime)
    try {
      codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
      codec.start()

      val bufferInfo = MediaCodec.BufferInfo()
      val output = ByteArrayOutputStream()
      var inputOffset = 0
      var sawInputEos = false
      var sawOutputEos = false

      while (!sawOutputEos) {
        if (!sawInputEos) {
          val inputIndex = codec.dequeueInputBuffer(10_000)
          if (inputIndex >= 0) {
            val inputBuffer = codec.getInputBuffer(inputIndex) ?: continue
            inputBuffer.clear()

            val remaining = pcmBytes.size - inputOffset
            if (remaining <= 0) {
              codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              sawInputEos = true
            } else {
              val bytesToCopy = minOf(remaining, inputBuffer.remaining())
              inputBuffer.put(pcmBytes, inputOffset, bytesToCopy)
              val pts = (inputOffset.toLong() * 1_000_000L) / (ir.sampleRate * ir.channelCount * 2)
              codec.queueInputBuffer(inputIndex, 0, bytesToCopy, pts, 0)
              inputOffset += bytesToCopy
            }
          }
        }

        val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
        if (outputIndex >= 0) {
          val outputBuffer = codec.getOutputBuffer(outputIndex)
          if (outputBuffer != null && bufferInfo.size > 0) {
            outputBuffer.position(bufferInfo.offset)
            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

            val chunk = ByteArray(bufferInfo.size)
            outputBuffer.get(chunk)
            output.write(chunk)
          }

          if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            sawOutputEos = true
          }
          codec.releaseOutputBuffer(outputIndex, false)
        }
      }

      return output.toByteArray()
    } finally {
      runCatching { codec.stop() }
      runCatching { codec.release() }
    }
  }
}

/**
 * OPUS codec using Android's [MediaCodec].
 *
 * Decodes OPUS via the standard [MediaExtractor] / [MediaCodec] pipeline.
 * Encodes to OPUS in an OGG container via [MediaCodec] + [MediaMuxer].
 * OPUS encoding requires API 29+; the [canEncode] flag is checked at runtime.
 */
internal class AndroidOpusCodec : AudioCodec {

  companion object {
    /** OPUS encoding via MediaCodec requires API 29 (Android Q). */
    val canEncode: Boolean = Build.VERSION.SDK_INT >= 29
  }

  override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.OPUS)
  override val encodableFormats: Set<AudioFormat> =
    if (canEncode) setOf(AudioFormat.OPUS) else emptySet()

  override fun sniff(data: ByteArray): AudioFormat? {
    if (data.size < 4) return null
    // OGG container: "OggS" magic
    if (data[0] == 'O'.code.toByte() && data[1] == 'g'.code.toByte() &&
      data[2] == 'g'.code.toByte() && data[3] == 'S'.code.toByte()
    ) {
      // Could be OGG/Vorbis too — check for "OpusHead" in first page.
      if (data.size >= 36) {
        val header = String(data, 28, 8, Charsets.US_ASCII)
        if (header == "OpusHead") return AudioFormat.OPUS
      }
    }
    return null
  }

  override suspend fun decode(source: ByteArray, context: ConversionContext): AudioIR =
    decodeWithMediaCodec(source, context)

  override suspend fun encode(ir: AudioIR, context: ConversionContext): ByteArray {
    require(canEncode) {
      "OPUS encoding requires Android API 29+ (current: ${Build.VERSION.SDK_INT})"
    }

    val pcmBytes = floatToPcm16(ir.samples.data)

    val mime = "audio/opus"
    val format = MediaFormat.createAudioFormat(mime, ir.sampleRate, ir.channelCount).apply {
      setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
    }

    val tmpFile = File.createTempFile("transmute_opus_", ".ogg")
    try {
      val muxer = MediaMuxer(tmpFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
      val codec = MediaCodec.createEncoderByType(mime)

      try {
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val bufferInfo = MediaCodec.BufferInfo()
        var trackIndex = -1
        var muxerStarted = false
        var inputOffset = 0
        var sawInputEos = false
        var sawOutputEos = false

        while (!sawOutputEos) {
          if (!sawInputEos) {
            val inputIndex = codec.dequeueInputBuffer(10_000)
            if (inputIndex >= 0) {
              val inputBuffer = codec.getInputBuffer(inputIndex) ?: continue
              inputBuffer.clear()

              val remaining = pcmBytes.size - inputOffset
              if (remaining <= 0) {
                codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                sawInputEos = true
              } else {
                val bytesToCopy = minOf(remaining, inputBuffer.remaining())
                inputBuffer.put(pcmBytes, inputOffset, bytesToCopy)
                val pts = (inputOffset.toLong() * 1_000_000L) / (ir.sampleRate * ir.channelCount * 2)
                codec.queueInputBuffer(inputIndex, 0, bytesToCopy, pts, 0)
                inputOffset += bytesToCopy
              }
            }
          }

          val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
          when {
            outputIndex >= 0 -> {
              val outputBuffer = codec.getOutputBuffer(outputIndex)

              if (!muxerStarted && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                codec.releaseOutputBuffer(outputIndex, false)
                continue
              }

              if (trackIndex < 0) {
                trackIndex = muxer.addTrack(codec.outputFormat)
                muxer.start()
                muxerStarted = true
              }

              if (outputBuffer != null && bufferInfo.size > 0) {
                outputBuffer.position(bufferInfo.offset)
                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
              }

              if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                sawOutputEos = true
              }
              codec.releaseOutputBuffer(outputIndex, false)
            }

            outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
              if (trackIndex < 0) {
                trackIndex = muxer.addTrack(codec.outputFormat)
                muxer.start()
                muxerStarted = true
              }
            }
          }
        }

        if (muxerStarted) muxer.stop()
      } finally {
        runCatching { codec.stop() }
        runCatching { codec.release() }
        runCatching { muxer.release() }
      }

      return tmpFile.readBytes()
    } finally {
      tmpFile.delete()
    }
  }
}

/**
 * AAC codec using Android's hardware-accelerated [MediaCodec].
 *
 * Decodes any AAC stream (ADTS or raw) via [MediaExtractor] / [MediaCodec].
 * Encodes to a raw ADTS stream at 128 kbps AAC-LC.
 */
internal class AndroidAacCodec : AudioCodec {

  override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.AAC)
  override val encodableFormats: Set<AudioFormat> = setOf(AudioFormat.AAC)

  override fun sniff(data: ByteArray): AudioFormat? {
    if (data.size < 2) return null
    // ADTS sync word: 0xFFF (12 bits)
    if ((data[0].toInt() and 0xFF) == 0xFF && (data[1].toInt() and 0xF0) == 0xF0) {
      return AudioFormat.AAC
    }
    return null
  }

  override suspend fun decode(source: ByteArray, context: ConversionContext): AudioIR =
    decodeWithMediaCodec(source, context)

  override suspend fun encode(ir: AudioIR, context: ConversionContext): ByteArray {
    val pcmBytes = floatToPcm16(ir.samples.data)

    val mime = "audio/mp4a-latm"
    val format = MediaFormat.createAudioFormat(mime, ir.sampleRate, ir.channelCount).apply {
      setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
      setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
    }

    val codec = MediaCodec.createEncoderByType(mime)
    try {
      codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
      codec.start()

      val bufferInfo = MediaCodec.BufferInfo()
      val output = ByteArrayOutputStream()
      var inputOffset = 0
      var sawInputEos = false
      var sawOutputEos = false

      while (!sawOutputEos) {
        if (!sawInputEos) {
          val inputIndex = codec.dequeueInputBuffer(10_000)
          if (inputIndex >= 0) {
            val inputBuffer = codec.getInputBuffer(inputIndex) ?: continue
            inputBuffer.clear()

            val remaining = pcmBytes.size - inputOffset
            if (remaining <= 0) {
              codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              sawInputEos = true
            } else {
              val bytesToCopy = minOf(remaining, inputBuffer.remaining())
              inputBuffer.put(pcmBytes, inputOffset, bytesToCopy)
              val pts = (inputOffset.toLong() * 1_000_000L) / (ir.sampleRate * ir.channelCount * 2)
              codec.queueInputBuffer(inputIndex, 0, bytesToCopy, pts, 0)
              inputOffset += bytesToCopy
            }
          }
        }

        val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
        if (outputIndex >= 0) {
          val outputBuffer = codec.getOutputBuffer(outputIndex)
          if (outputBuffer != null && bufferInfo.size > 0) {
            outputBuffer.position(bufferInfo.offset)
            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

            val adtsHeader = buildAdtsHeader(bufferInfo.size, ir.sampleRate, ir.channelCount)
            output.write(adtsHeader)

            val chunk = ByteArray(bufferInfo.size)
            outputBuffer.get(chunk)
            output.write(chunk)
          }

          if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            sawOutputEos = true
          }
          codec.releaseOutputBuffer(outputIndex, false)
        }
      }

      return output.toByteArray()
    } finally {
      runCatching { codec.stop() }
      runCatching { codec.release() }
    }
  }
}

/**
 * M4A codec using Android's [MediaCodec] and [MediaMuxer].
 *
 * Decodes M4A (AAC in MP4 container) via [MediaExtractor] / [MediaCodec].
 * Encodes to M4A via [MediaCodec] AAC-LC → [MediaMuxer] MP4 container.
 */
internal class AndroidM4aCodec : AudioCodec {

  override val decodableFormats: Set<AudioFormat> = setOf(AudioFormat.M4A)
  override val encodableFormats: Set<AudioFormat> = setOf(AudioFormat.M4A)

  override fun sniff(data: ByteArray): AudioFormat? {
    if (data.size < 8) return null
    // ftyp box: bytes 4–7 == "ftyp"
    val ftyp = String(data, 4, 4, Charsets.US_ASCII)
    if (ftyp == "ftyp") return AudioFormat.M4A
    return null
  }

  override suspend fun decode(source: ByteArray, context: ConversionContext): AudioIR =
    decodeWithMediaCodec(source, context)

  override suspend fun encode(ir: AudioIR, context: ConversionContext): ByteArray {
    val pcmBytes = floatToPcm16(ir.samples.data)

    val mime = "audio/mp4a-latm"
    val format = MediaFormat.createAudioFormat(mime, ir.sampleRate, ir.channelCount).apply {
      setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
      setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
    }

    val tmpFile = File.createTempFile("transmute_m4a_", ".m4a")
    try {
      val muxer = MediaMuxer(tmpFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
      val codec = MediaCodec.createEncoderByType(mime)

      try {
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val bufferInfo = MediaCodec.BufferInfo()
        var trackIndex = -1
        var muxerStarted = false
        var inputOffset = 0
        var sawInputEos = false
        var sawOutputEos = false

        while (!sawOutputEos) {
          if (!sawInputEos) {
            val inputIndex = codec.dequeueInputBuffer(10_000)
            if (inputIndex >= 0) {
              val inputBuffer = codec.getInputBuffer(inputIndex) ?: continue
              inputBuffer.clear()

              val remaining = pcmBytes.size - inputOffset
              if (remaining <= 0) {
                codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                sawInputEos = true
              } else {
                val bytesToCopy = minOf(remaining, inputBuffer.remaining())
                inputBuffer.put(pcmBytes, inputOffset, bytesToCopy)
                val pts = (inputOffset.toLong() * 1_000_000L) / (ir.sampleRate * ir.channelCount * 2)
                codec.queueInputBuffer(inputIndex, 0, bytesToCopy, pts, 0)
                inputOffset += bytesToCopy
              }
            }
          }

          val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
          when {
            outputIndex >= 0 -> {
              val outputBuffer = codec.getOutputBuffer(outputIndex)

              if (!muxerStarted && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                codec.releaseOutputBuffer(outputIndex, false)
                continue
              }

              if (trackIndex < 0) {
                trackIndex = muxer.addTrack(codec.outputFormat)
                muxer.start()
                muxerStarted = true
              }

              if (outputBuffer != null && bufferInfo.size > 0) {
                outputBuffer.position(bufferInfo.offset)
                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
              }

              if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                sawOutputEos = true
              }
              codec.releaseOutputBuffer(outputIndex, false)
            }

            outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
              if (trackIndex < 0) {
                trackIndex = muxer.addTrack(codec.outputFormat)
                muxer.start()
                muxerStarted = true
              }
            }
          }
        }

        if (muxerStarted) muxer.stop()
      } finally {
        runCatching { codec.stop() }
        runCatching { codec.release() }
        runCatching { muxer.release() }
      }

      return tmpFile.readBytes()
    } finally {
      tmpFile.delete()
    }
  }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private class FloatArrayList(initialCapacity: Int = 16) {
  private var data = FloatArray(initialCapacity)
  private var size = 0

  fun add(value: Float) {
    if (size == data.size) data = data.copyOf(maxOf(16, data.size * 2))
    data[size++] = value
  }

  fun addAll(values: FloatArray) {
    ensureCapacity(size + values.size)
    values.copyInto(data, destinationOffset = size)
    size += values.size
  }

  private fun ensureCapacity(capacity: Int) {
    if (capacity <= data.size) return
    var newSize = data.size
    while (newSize < capacity) newSize *= 2
    data = data.copyOf(newSize)
  }

  fun toFloatArray(): FloatArray = data.copyOf(size)
}

private class ByteArrayOutputStream(initialCapacity: Int = 4096) {
  private var data = ByteArray(initialCapacity)
  private var size = 0

  fun write(bytes: ByteArray) {
    ensureCapacity(size + bytes.size)
    bytes.copyInto(data, size)
    size += bytes.size
  }

  private fun ensureCapacity(capacity: Int) {
    if (capacity <= data.size) return
    var newSize = data.size
    while (newSize < capacity) newSize *= 2
    data = data.copyOf(newSize)
  }

  fun toByteArray(): ByteArray = data.copyOf(size)
}
