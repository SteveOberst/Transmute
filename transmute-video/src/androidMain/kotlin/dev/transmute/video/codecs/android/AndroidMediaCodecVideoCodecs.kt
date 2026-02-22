package dev.transmute.video.codecs.android

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import dev.transmute.audio.AudioSamples
import dev.transmute.core.Bytes
import dev.transmute.core.TransmuteContext
import dev.transmute.core.asBytes
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.PixelFormat
import dev.transmute.video.AudioTrack
import dev.transmute.video.ListFrameStream
import dev.transmute.video.VideoCodec
import dev.transmute.video.VideoFrame
import dev.transmute.video.VideoFormat
import dev.transmute.video.VideoIR
import dev.transmute.video.VideoMetadata
import dev.transmute.video.VideoTrack
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import dev.transmute.video.VideoDecodeOptions
import dev.transmute.video.VideoEncodeOptions

// ---------------------------------------------------------------------------
// ByteArray -> MediaDataSource helper (private, same pattern as audio module)
// ---------------------------------------------------------------------------

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
// YUV <-> RGBA conversion (BT.601)
// ---------------------------------------------------------------------------

private fun yuvToRgba(
  yPlane: ByteArray, uPlane: ByteArray, vPlane: ByteArray,
  yRowStride: Int, uvRowStride: Int, uvPixelStride: Int,
  width: Int, height: Int,
): ByteArray {
  val rgba = ByteArray(width * height * 4)
  for (row in 0 until height) {
    for (col in 0 until width) {
      val y = (yPlane[row * yRowStride + col].toInt() and 0xFF)
      val uvRow = row / 2
      val uvCol = col / 2
      val u = (uPlane[uvRow * uvRowStride + uvCol * uvPixelStride].toInt() and 0xFF) - 128
      val v = (vPlane[uvRow * uvRowStride + uvCol * uvPixelStride].toInt() and 0xFF) - 128

      val r = (y + 1.402 * v).toInt().coerceIn(0, 255)
      val g = (y - 0.344136 * u - 0.714136 * v).toInt().coerceIn(0, 255)
      val b = (y + 1.772 * u).toInt().coerceIn(0, 255)

      val idx = (row * width + col) * 4
      rgba[idx] = r.toByte()
      rgba[idx + 1] = g.toByte()
      rgba[idx + 2] = b.toByte()
      rgba[idx + 3] = 0xFF.toByte()
    }
  }
  return rgba
}

private fun rgbaToNv12(rgba: ByteArray, width: Int, height: Int): ByteArray {
  val ySize = width * height
  val uvSize = width * height / 2
  val nv12 = ByteArray(ySize + uvSize)

  for (row in 0 until height) {
    for (col in 0 until width) {
      val idx = (row * width + col) * 4
      val r = rgba[idx].toInt() and 0xFF
      val g = rgba[idx + 1].toInt() and 0xFF
      val b = rgba[idx + 2].toInt() and 0xFF

      val y = (0.299 * r + 0.587 * g + 0.114 * b).toInt().coerceIn(0, 255)
      nv12[row * width + col] = y.toByte()

      if (row % 2 == 0 && col % 2 == 0) {
        val u = (-0.169 * r - 0.331 * g + 0.500 * b + 128).toInt().coerceIn(0, 255)
        val v = (0.500 * r - 0.419 * g - 0.081 * b + 128).toInt().coerceIn(0, 255)
        val uvIdx = ySize + (row / 2) * width + col
        nv12[uvIdx] = u.toByte()
        nv12[uvIdx + 1] = v.toByte()
      }
    }
  }
  return nv12
}

// ---------------------------------------------------------------------------
// Shared decode logic - extract all video frames + audio via MediaCodec
// ---------------------------------------------------------------------------

private fun decodeVideoWithMediaCodec(source: ByteArray): Pair<List<VideoFrame>, Pair<Int, MediaFormat?>> {
  val extractor = MediaExtractor()
  val dataSource = ByteArrayMediaDataSource(source)
  val frames = mutableListOf<VideoFrame>()

  try {
    extractor.setDataSource(dataSource)

    // Find video track
    val videoTrackIdx = (0 until extractor.trackCount).firstOrNull { idx ->
      extractor.getTrackFormat(idx).getString(MediaFormat.KEY_MIME)
        ?.startsWith("video/") == true
    } ?: error("No video track found")

    val audioTrackIdx = (0 until extractor.trackCount).firstOrNull { idx ->
      extractor.getTrackFormat(idx).getString(MediaFormat.KEY_MIME)
        ?.startsWith("audio/") == true
    }
    val audioFormat = audioTrackIdx?.let { extractor.getTrackFormat(it) }

    extractor.selectTrack(videoTrackIdx)
    val videoFormat = extractor.getTrackFormat(videoTrackIdx)
    val mime = videoFormat.getString(MediaFormat.KEY_MIME) ?: error("No video MIME")
    val width = videoFormat.getInteger(MediaFormat.KEY_WIDTH)
    val height = videoFormat.getInteger(MediaFormat.KEY_HEIGHT)

    val codec = MediaCodec.createDecoderByType(mime)
    try {
      codec.configure(videoFormat, null, null, 0)
      codec.start()

      val bufferInfo = MediaCodec.BufferInfo()
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
              codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              sawInputEos = true
            } else {
              val pts = extractor.sampleTime
              codec.queueInputBuffer(inputIndex, 0, sampleSize, pts, 0)
              extractor.advance()
            }
          }
        }

        val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
        when {
          outputIndex >= 0 -> {
            if (bufferInfo.size > 0) {
              val image = codec.getOutputImage(outputIndex)
              if (image != null) {
                val yPlane = ByteArray(image.planes[0].buffer.remaining())
                image.planes[0].buffer.get(yPlane)
                val uPlane = ByteArray(image.planes[1].buffer.remaining())
                image.planes[1].buffer.get(uPlane)
                val vPlane = ByteArray(image.planes[2].buffer.remaining())
                image.planes[2].buffer.get(vPlane)

                val rgbaData = yuvToRgba(
                  yPlane, uPlane, vPlane,
                  yRowStride = image.planes[0].rowStride,
                  uvRowStride = image.planes[1].rowStride,
                  uvPixelStride = image.planes[1].pixelStride,
                  width = image.width, height = image.height,
                )

                val timestampMs = bufferInfo.presentationTimeUs / 1000
                frames.add(
                  VideoFrame(
                    buffer = ByteArrayPixelBuffer(rgbaData),
                    width = image.width,
                    height = image.height,
                    pixelFormat = PixelFormat.RGBA_8888,
                    timestampMs = timestampMs,
                  ),
                )
                image.close()
              }
            }
            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
              sawOutputEos = true
            }
            codec.releaseOutputBuffer(outputIndex, false)
          }
          outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> { /* retry */ }
        }
      }
    } finally {
      runCatching { codec.stop() }
      runCatching { codec.release() }
    }

    return Pair(frames, Pair(audioTrackIdx ?: -1, audioFormat))
  } finally {
    runCatching { extractor.release() }
    runCatching { dataSource.close() }
  }
}

private fun decodeAudioWithMediaCodec(source: ByteArray): AudioSamples? {
  val extractor = MediaExtractor()
  val dataSource = ByteArrayMediaDataSource(source)

  try {
    extractor.setDataSource(dataSource)

    val audioTrackIdx = (0 until extractor.trackCount).firstOrNull { idx ->
      extractor.getTrackFormat(idx).getString(MediaFormat.KEY_MIME)
        ?.startsWith("audio/") == true
    } ?: return null

    extractor.selectTrack(audioTrackIdx)
    val audioFormat = extractor.getTrackFormat(audioTrackIdx)
    val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: return null
    var sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    var channels = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

    val codec = MediaCodec.createDecoderByType(mime)
    try {
      codec.configure(audioFormat, null, null, 0)
      codec.start()

      val bufferInfo = MediaCodec.BufferInfo()
      val floats = mutableListOf<Float>()
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
              codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              sawInputEos = true
            } else {
              codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
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
              val shorts = outputBuffer.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
              for (i in 0 until shorts.remaining()) {
                floats.add(shorts.get(i) / 32768f)
              }
            }
            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
              sawOutputEos = true
            }
            codec.releaseOutputBuffer(outputIndex, false)
          }
          outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
            val fmt = codec.outputFormat
            if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
              sampleRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            }
            if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
              channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            }
          }
        }
      }

      return AudioSamples(
        data = floats.toFloatArray(),
        sampleRate = sampleRate,
        channelCount = channels,
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
// Shared encode logic
// ---------------------------------------------------------------------------

private fun encodeVideoWithMediaCodec(
  ir: VideoIR,
  outputFormat: Int, // MediaMuxer.OutputFormat.*
  videoMime: String,
  audioMime: String?,
  ext: String,
): ByteArray {
  val width = ir.videoTrack.width
  val height = ir.videoTrack.height
  val fps = ir.videoTrack.frameRate

  val tmpOut = File.createTempFile("transmute_vid_enc_", ".$ext")
  try {
    val muxer = MediaMuxer(tmpOut.absolutePath, outputFormat)

    // --- Video encoder ---
    val vFormat = MediaFormat.createVideoFormat(videoMime, width, height).apply {
      setInteger(MediaFormat.KEY_BIT_RATE, width * height * 4)
      setFloat(MediaFormat.KEY_FRAME_RATE, fps.toFloat())
      setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
      setInteger(
        MediaFormat.KEY_COLOR_FORMAT,
        @Suppress("DEPRECATION")
        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
      )
    }
    val videoCodec = MediaCodec.createEncoderByType(videoMime)

    // --- Audio encoder (optional) ---
    val audioCodec = if (ir.audioTrack != null && audioMime != null) {
      MediaCodec.createEncoderByType(audioMime)
    } else null

    try {
      videoCodec.configure(vFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
      videoCodec.start()

      // Audio encoder setup
      val pcmBytes: ByteArray?
      if (audioCodec != null && ir.audioTrack != null) {
        val aFormat = MediaFormat.createAudioFormat(
          audioMime!!,
          ir.audioTrack.samples.sampleRate,
          ir.audioTrack.samples.channelCount,
        ).apply {
          setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
          if (audioMime == "audio/mp4a-latm") {
            setInteger(
              MediaFormat.KEY_AAC_PROFILE,
              MediaCodecInfo.CodecProfileLevel.AACObjectLC,
            )
          }
        }
        audioCodec.configure(aFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        audioCodec.start()
        pcmBytes = floatToPcm16(ir.audioTrack.samples.data)
      } else {
        pcmBytes = null
      }

      val bufferInfo = MediaCodec.BufferInfo()
      var videoTrackIdx = -1
      var audioTrackIdx = -1
      var muxerStarted = false
      var tracksAdded = 0
      val totalTracksExpected = 1 + (if (audioCodec != null) 1 else 0)

      // Buffer for encoded video data produced before the muxer starts
      // (when both video+audio tracks are needed, the muxer can't start
      // until both tracks are added, but video is encoded first)
      val pendingVideoSamples = mutableListOf<Pair<ByteArray, MediaCodec.BufferInfo>>()

      // Collect all frames first (for simplicity)
      val allFrames = mutableListOf<ByteArray>()
      val frames = ir.videoTrack.frames
      while (true) {
        val f = kotlinx.coroutines.runBlocking { frames.nextFrame() } ?: break
        val rgbaData = (f.buffer as ByteArrayPixelBuffer).data
        allFrames.add(rgbaToNv12(rgbaData, f.width, f.height))
      }

      // Encode video frames
      var frameIdx = 0
      var sawVideoOutputEos = false

      while (!sawVideoOutputEos) {
        // Feed input
        if (frameIdx <= allFrames.size) {
          val inputIndex = videoCodec.dequeueInputBuffer(10_000)
          if (inputIndex >= 0) {
            val inputBuffer = videoCodec.getInputBuffer(inputIndex) ?: continue
            inputBuffer.clear()
            if (frameIdx >= allFrames.size) {
              videoCodec.queueInputBuffer(
                inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM,
              )
              frameIdx++
            } else {
              val nv12 = allFrames[frameIdx]
              inputBuffer.put(nv12)
              val pts = (frameIdx * 1_000_000L / fps).toLong()
              videoCodec.queueInputBuffer(inputIndex, 0, nv12.size, pts, 0)
              frameIdx++
            }
          }
        }

        // Read output
        val outputIndex = videoCodec.dequeueOutputBuffer(bufferInfo, 10_000)
        when {
          outputIndex >= 0 -> {
            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
              videoCodec.releaseOutputBuffer(outputIndex, false)
              continue
            }
            if (videoTrackIdx < 0) {
              videoTrackIdx = muxer.addTrack(videoCodec.outputFormat)
              tracksAdded++
              if (tracksAdded >= totalTracksExpected) {
                muxer.start()
                muxerStarted = true
              }
            }
            val outputBuffer = videoCodec.getOutputBuffer(outputIndex)
            if (outputBuffer != null && bufferInfo.size > 0) {
              if (muxerStarted) {
                outputBuffer.position(bufferInfo.offset)
                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                muxer.writeSampleData(videoTrackIdx, outputBuffer, bufferInfo)
              } else {
                // Buffer encoded data until muxer starts (waiting for audio track)
                val copy = ByteArray(bufferInfo.size)
                outputBuffer.position(bufferInfo.offset)
                outputBuffer.get(copy)
                val infoCopy = MediaCodec.BufferInfo().apply {
                  set(0, copy.size, bufferInfo.presentationTimeUs, bufferInfo.flags)
                }
                pendingVideoSamples.add(copy to infoCopy)
              }
            }
            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
              sawVideoOutputEos = true
            }
            videoCodec.releaseOutputBuffer(outputIndex, false)
          }
          outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
            if (videoTrackIdx < 0) {
              videoTrackIdx = muxer.addTrack(videoCodec.outputFormat)
              tracksAdded++
              if (tracksAdded >= totalTracksExpected) {
                muxer.start()
                muxerStarted = true
              }
            }
          }
        }
      }

      // Encode audio (if present)
      if (audioCodec != null && pcmBytes != null) {
        val aBufferInfo = MediaCodec.BufferInfo()
        var audioInputOffset = 0
        var sawAudioInputEos = false
        var sawAudioOutputEos = false

        while (!sawAudioOutputEos) {
          if (!sawAudioInputEos) {
            val inputIndex = audioCodec.dequeueInputBuffer(10_000)
            if (inputIndex >= 0) {
              val inputBuffer = audioCodec.getInputBuffer(inputIndex) ?: continue
              inputBuffer.clear()
              val remaining = pcmBytes.size - audioInputOffset
              if (remaining <= 0) {
                audioCodec.queueInputBuffer(
                  inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                )
                sawAudioInputEos = true
              } else {
                val bytesToCopy = minOf(remaining, inputBuffer.remaining())
                inputBuffer.put(pcmBytes, audioInputOffset, bytesToCopy)
                val sr = ir.audioTrack!!.samples.sampleRate
                val ch = ir.audioTrack.samples.channelCount
                val pts = (audioInputOffset.toLong() * 1_000_000L) / (sr * ch * 2)
                audioCodec.queueInputBuffer(inputIndex, 0, bytesToCopy, pts, 0)
                audioInputOffset += bytesToCopy
              }
            }
          }

          val outputIndex = audioCodec.dequeueOutputBuffer(aBufferInfo, 10_000)
          when {
            outputIndex >= 0 -> {
              if ((aBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                audioCodec.releaseOutputBuffer(outputIndex, false)
                continue
              }
              if (audioTrackIdx < 0) {
                audioTrackIdx = muxer.addTrack(audioCodec.outputFormat)
                tracksAdded++
                if (!muxerStarted && tracksAdded >= totalTracksExpected) {
                  muxer.start()
                  muxerStarted = true
                  // Flush buffered video samples now that muxer is started
                  for ((data, info) in pendingVideoSamples) {
                    muxer.writeSampleData(videoTrackIdx, java.nio.ByteBuffer.wrap(data), info)
                  }
                  pendingVideoSamples.clear()
                }
              }
              val outputBuffer = audioCodec.getOutputBuffer(outputIndex)
              if (muxerStarted && outputBuffer != null && aBufferInfo.size > 0) {
                outputBuffer.position(aBufferInfo.offset)
                outputBuffer.limit(aBufferInfo.offset + aBufferInfo.size)
                muxer.writeSampleData(audioTrackIdx, outputBuffer, aBufferInfo)
              }
              if ((aBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                sawAudioOutputEos = true
              }
              audioCodec.releaseOutputBuffer(outputIndex, false)
            }
            outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
              if (audioTrackIdx < 0) {
                audioTrackIdx = muxer.addTrack(audioCodec.outputFormat)
                tracksAdded++
                if (!muxerStarted && tracksAdded >= totalTracksExpected) {
                  muxer.start()
                  muxerStarted = true
                  // Flush buffered video samples now that muxer is started
                  for ((data, info) in pendingVideoSamples) {
                    muxer.writeSampleData(videoTrackIdx, java.nio.ByteBuffer.wrap(data), info)
                  }
                  pendingVideoSamples.clear()
                }
              }
            }
          }
        }
      }

      if (muxerStarted) muxer.stop()

      return tmpOut.readBytes()
    } finally {
      runCatching { videoCodec.stop() }
      runCatching { videoCodec.release() }
      runCatching { audioCodec?.stop() }
      runCatching { audioCodec?.release() }
      runCatching { muxer.release() }
    }
  } finally {
    tmpOut.delete()
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

// ---------------------------------------------------------------------------
// MP4 Codec (H.264 + AAC)
// ---------------------------------------------------------------------------

internal class AndroidMp4Codec : VideoCodec {
  override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.Mp4)
  override val encodableFormats: Set<VideoFormat> = setOf(VideoFormat.Mp4)

  override fun sniff(data: Bytes): VideoFormat? {
    val bytes = data.data
    if (bytes.size < 12) return null
    if (bytes[4] != 0x66.toByte() || bytes[5] != 0x74.toByte() ||
      bytes[6] != 0x79.toByte() || bytes[7] != 0x70.toByte()) return null
    val brand = (8 until 12).map { bytes[it].toInt().toChar() }.joinToString("")
    return when {
      brand.startsWith("mp4") || brand == "isom" || brand == "M4V " ||
        brand == "avc1" || brand == "iso2" || brand == "iso5" ||
        brand == "iso6" || brand == "mmp4" -> VideoFormat.Mp4
      brand.startsWith("3gp") || brand.startsWith("3g2") -> VideoFormat.Mp4
      else -> null
    }
  }

  override suspend fun decode(source: Bytes, options: VideoDecodeOptions, context: TransmuteContext): VideoIR {
    val bytes = source.data
    val (frames, audioInfo) = decodeVideoWithMediaCodec(bytes)
    require(frames.isNotEmpty()) { "No video frames decoded" }

    val audioSamples = if (audioInfo.first >= 0) {
      decodeAudioWithMediaCodec(bytes)
    } else null

    val width = frames.first().width
    val height = frames.first().height
    val durationMs = frames.last().timestampMs.coerceAtLeast(1)
    val frameRate = if (durationMs > 0) {
      frames.size.toDouble() * 1000.0 / durationMs
    } else 30.0

    return VideoIR(
      videoTrack = VideoTrack(
        width = width, height = height,
        frameRate = frameRate,
        frames = ListFrameStream(frames),
      ),
      audioTrack = audioSamples?.let { AudioTrack(samples = it, sampleStream = null) },
      durationMs = durationMs,
    )
  }

  override suspend fun encode(ir: VideoIR, format: VideoFormat, options: VideoEncodeOptions, context: TransmuteContext): Bytes =
    encodeVideoWithMediaCodec(
      ir,
      outputFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
      videoMime = "video/avc",
      audioMime = if (ir.audioTrack != null) "audio/mp4a-latm" else null,
      ext = "mp4",
    ).asBytes()
}

// ---------------------------------------------------------------------------
// MOV Codec (same container as MP4 on Android)
// ---------------------------------------------------------------------------

internal class AndroidMovCodec : VideoCodec {
  override val decodableFormats: Set<VideoFormat> = setOf(VideoFormat.Mov)
  override val encodableFormats: Set<VideoFormat> = setOf(VideoFormat.Mov)

  override fun sniff(data: Bytes): VideoFormat? {
    val bytes = data.data
    if (bytes.size < 12) return null
    if (bytes[4] != 0x66.toByte() || bytes[5] != 0x74.toByte() ||
      bytes[6] != 0x79.toByte() || bytes[7] != 0x70.toByte()) return null
    val brand = (8 until 12).map { bytes[it].toInt().toChar() }.joinToString("")
    return if (brand == "qt  ") VideoFormat.Mov else null
  }

  override suspend fun decode(source: Bytes, options: VideoDecodeOptions, context: TransmuteContext): VideoIR {
    // MOV and MP4 share the same container on Android
    val bytes = source.data
    val (frames, audioInfo) = decodeVideoWithMediaCodec(bytes)
    require(frames.isNotEmpty()) { "No video frames decoded" }

    val audioSamples = if (audioInfo.first >= 0) {
      decodeAudioWithMediaCodec(bytes)
    } else null

    val width = frames.first().width
    val height = frames.first().height
    val durationMs = frames.last().timestampMs.coerceAtLeast(1)
    val frameRate = if (durationMs > 0) {
      frames.size.toDouble() * 1000.0 / durationMs
    } else 30.0

    return VideoIR(
      videoTrack = VideoTrack(
        width = width, height = height,
        frameRate = frameRate,
        frames = ListFrameStream(frames),
      ),
      audioTrack = audioSamples?.let { AudioTrack(samples = it, sampleStream = null) },
      durationMs = durationMs,
    )
  }

  override suspend fun encode(ir: VideoIR, format: VideoFormat, options: VideoEncodeOptions, context: TransmuteContext): Bytes =
    encodeVideoWithMediaCodec(
      ir,
      outputFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
      videoMime = "video/avc",
      audioMime = if (ir.audioTrack != null) "audio/mp4a-latm" else null,
      ext = "mov",
    ).asBytes()
}

// ---------------------------------------------------------------------------
// WebM Decoder (VP8 decode - encode not reliably supported on all devices)
// ---------------------------------------------------------------------------

internal class AndroidWebmDecoder : dev.transmute.video.VideoDecoder {
  override val supportedFormats: Set<VideoFormat> = setOf(VideoFormat.Webm)

  override fun sniff(data: Bytes): VideoFormat? {
    val bytes = data.data
    if (bytes.size < 4) return null
    if (bytes[0] != 0x1A.toByte() || bytes[1] != 0x45.toByte() ||
      bytes[2] != 0xDF.toByte() || bytes[3] != 0xA3.toByte()) return null
    if (bytes.size >= 40) {
      val content = bytes.copyOfRange(0, minOf(bytes.size, 64)).decodeToString()
      if (content.contains("matroska")) return null // MKV, not WebM
      if (content.contains("webm")) return VideoFormat.Webm
    }
    return VideoFormat.Webm
  }

  override suspend fun decode(source: Bytes, options: VideoDecodeOptions, context: TransmuteContext): VideoIR {
    val bytes = source.data
    val (frames, audioInfo) = decodeVideoWithMediaCodec(bytes)
    require(frames.isNotEmpty()) { "No video frames decoded from WebM" }

    val audioSamples = if (audioInfo.first >= 0) {
      decodeAudioWithMediaCodec(bytes)
    } else null

    val width = frames.first().width
    val height = frames.first().height
    val durationMs = frames.last().timestampMs.coerceAtLeast(1)
    val frameRate = if (durationMs > 0) {
      frames.size.toDouble() * 1000.0 / durationMs
    } else 30.0

    return VideoIR(
      videoTrack = VideoTrack(
        width = width, height = height,
        frameRate = frameRate,
        frames = ListFrameStream(frames),
      ),
      audioTrack = audioSamples?.let { AudioTrack(samples = it, sampleStream = null) },
      durationMs = durationMs,
    )
  }
}

