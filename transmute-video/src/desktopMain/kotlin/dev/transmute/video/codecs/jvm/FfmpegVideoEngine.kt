package dev.transmute.video.codecs.jvm

import dev.transmute.audio.AudioIR
import dev.transmute.audio.CanonicalAudioEncodeOptions
import dev.transmute.audio.AudioFormat
import dev.transmute.audio.AudioSamples
import dev.transmute.audio.codecs.WavDecoder
import dev.transmute.audio.codecs.WavEncoder
import dev.transmute.core.TransmuteContext
import dev.transmute.core.FfmpegResolver
import dev.transmute.core.asBytes
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.PixelFormat
import dev.transmute.video.AudioTrack
import dev.transmute.video.FrameStream
import dev.transmute.video.VideoFrame
import dev.transmute.video.VideoIR
import dev.transmute.video.VideoTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * FFmpeg-based video engine for JVM/Desktop.
 *
 * FFmpeg is resolved via [FfmpegResolver] which prefers the bundled
 * binary, falls back to PATH lookup, and can be overridden via
 * [dev.transmute.core.TransmuteConfig.ffmpeg].
 *
 * **Decode**: ffprobe for metadata → ffmpeg pipes raw RGBA frames via stdout →
 * audio extracted as WAV separately.
 *
 * **Encode**: raw RGBA frames written to temp file → optional WAV audio →
 * ffmpeg muxes/encodes to target container.
 */
internal object FfmpegVideoEngine {

  val available: Boolean get() = FfmpegResolver.available

  private val ffmpeg: String get() = FfmpegResolver.ffmpegPath
  private val ffprobe: String get() = FfmpegResolver.ffprobePath

  private val wavDecoder = WavDecoder()
  private val wavEncoder = WavEncoder()

  // -----------------------------------------------------------------------
  // Probe
  // -----------------------------------------------------------------------

  data class VideoInfo(
    val width: Int,
    val height: Int,
    val frameRate: Double,
    val frameCount: Long,
    val durationMs: Long,
    val hasAudio: Boolean,
    val audioSampleRate: Int?,
    val audioChannels: Int?,
  )

  fun probe(input: File): VideoInfo {
    // Video stream info
    val videoInfo = runCommand(
      ffprobe, "-v", "quiet",
      "-select_streams", "v:0",
      "-show_entries", "stream=width,height,r_frame_rate,nb_frames",
      "-of", "csv=p=0",
      input.absolutePath,
    )
    val videoParts = videoInfo.trim().split(",")
    require(videoParts.size >= 3) { "ffprobe: unexpected video stream info: $videoInfo" }

    val width = videoParts[0].trim().toInt()
    val height = videoParts[1].trim().toInt()
    val fpsStr = videoParts[2].trim()
    val frameRate = if ("/" in fpsStr) {
      val (num, den) = fpsStr.split("/")
      num.toDouble() / den.toDouble()
    } else {
      fpsStr.toDouble()
    }
    val frameCountRaw = videoParts.getOrNull(3)?.trim()?.toLongOrNull()

    // Duration
    val durationRaw = runCommand(
      ffprobe, "-v", "quiet",
      "-show_entries", "format=duration",
      "-of", "csv=p=0",
      input.absolutePath,
    )
    val durationSec = durationRaw.trim().toDoubleOrNull() ?: 0.0
    val durationMs = (durationSec * 1000).toLong()
    val frameCount = frameCountRaw
      ?: ((durationSec * frameRate).toLong()).coerceAtLeast(1)

    // Audio stream check
    val audioInfo = runCommand(
      ffprobe, "-v", "quiet",
      "-select_streams", "a:0",
      "-show_entries", "stream=sample_rate,channels",
      "-of", "csv=p=0",
      input.absolutePath,
    )
    val hasAudio = audioInfo.isNotBlank()
    val audioParts = if (hasAudio) audioInfo.trim().split(",") else null

    return VideoInfo(
      width = width,
      height = height,
      frameRate = frameRate,
      frameCount = frameCount,
      durationMs = durationMs,
      hasAudio = hasAudio,
      audioSampleRate = audioParts?.getOrNull(0)?.trim()?.toIntOrNull(),
      audioChannels = audioParts?.getOrNull(1)?.trim()?.toIntOrNull(),
    )
  }

  // -----------------------------------------------------------------------
  // Decode
  // -----------------------------------------------------------------------

  suspend fun decode(
    source: ByteArray,
    ext: String,
    context: TransmuteContext,
  ): VideoIR = withContext(Dispatchers.IO) {
    check(available) { "FFmpeg is not available on this system" }

    val tmpIn = File.createTempFile("transmute_vid_in_", ".$ext")
    tmpIn.deleteOnExit()
    tmpIn.writeBytes(source)

    val info = probe(tmpIn)

    // Extract audio (if present)
    val audioTrack = if (info.hasAudio) {
      val tmpAudio = File.createTempFile("transmute_vid_audio_", ".wav")
      try {
        runFfmpeg(
          "-i", tmpIn.absolutePath,
          "-vn", "-codec:a", "pcm_s16le",
          "-f", "wav", tmpAudio.absolutePath,
        )
        val audioIR = wavDecoder.decode(tmpAudio.readBytes().asBytes(), dev.transmute.audio.CanonicalAudioDecodeOptions(), context)
        AudioTrack(samples = audioIR.samples, sampleStream = null)
      } finally {
        tmpAudio.delete()
      }
    } else null

    // Create streaming frame reader (owns tmpIn - deletes when exhausted)
    val frameStream = FfmpegFrameStream(
      inputFile = tmpIn,
      width = info.width,
      height = info.height,
      frameCount = info.frameCount,
      frameRate = info.frameRate,
    )

    VideoIR(
      videoTrack = VideoTrack(
        width = info.width,
        height = info.height,
        frameRate = info.frameRate,
        frames = frameStream,
      ),
      audioTrack = audioTrack,
      durationMs = info.durationMs,
    )
  }

  // -----------------------------------------------------------------------
  // Encode
  // -----------------------------------------------------------------------

  suspend fun encode(
    ir: VideoIR,
    videoCodec: String,
    audioCodec: String?,
    format: String,
    ext: String,
    extraArgs: List<String> = emptyList(),
    context: TransmuteContext,
  ): ByteArray = withContext(Dispatchers.IO) {
    check(available) { "FFmpeg is not available on this system" }

    val width = ir.videoTrack.width
    val height = ir.videoTrack.height
    val fps = ir.videoTrack.frameRate
    val frames = ir.videoTrack.frames

    val tmpFrames = File.createTempFile("transmute_vid_frames_", ".raw")
    val tmpAudio = if (ir.audioTrack != null) {
      File.createTempFile("transmute_vid_audio_enc_", ".wav")
    } else null
    val tmpOut = File.createTempFile("transmute_vid_out_", ".$ext")

    try {
      // Write raw RGBA frames
      tmpFrames.outputStream().buffered().use { os ->
        while (true) {
          val frame = frames.nextFrame() ?: break
          val pixelData = (frame.buffer as ByteArrayPixelBuffer).data
          os.write(pixelData)
        }
      }

      // Write audio WAV (if present)
      if (ir.audioTrack != null && tmpAudio != null) {
        val audioIR = AudioIR(
          samples = ir.audioTrack.samples,
          sampleRate = ir.audioTrack.samples.sampleRate,
          channelCount = ir.audioTrack.samples.channelCount,
          durationMs = ir.durationMs,
        )
        val wavBytes = wavEncoder.encode(audioIR, AudioFormat.Wav, CanonicalAudioEncodeOptions(), context).data
        tmpAudio.writeBytes(wavBytes)
      }

      // Build ffmpeg command
      val cmd = buildList {
        add(ffmpeg); add("-y"); add("-loglevel"); add("error")
        // Video input (raw RGBA)
        add("-f"); add("rawvideo")
        add("-pix_fmt"); add("rgba")
        add("-s"); add("${width}x${height}")
        add("-r"); add(fps.toString())
        add("-i"); add(tmpFrames.absolutePath)
        // Audio input
        if (tmpAudio != null) {
          add("-i"); add(tmpAudio.absolutePath)
        }
        // Video output codec
        add("-c:v"); add(videoCodec)
        add("-pix_fmt"); add("yuv420p")
        // Audio output codec
        if (tmpAudio != null && audioCodec != null) {
          add("-c:a"); add(audioCodec)
        }
        addAll(extraArgs)
        add("-f"); add(format)
        add(tmpOut.absolutePath)
      }

      runFfmpegCmd(cmd)
      tmpOut.readBytes()
    } finally {
      tmpFrames.delete()
      tmpAudio?.delete()
      tmpOut.delete()
    }
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  private fun runCommand(vararg args: String): String {
    val process = ProcessBuilder(args.toList()).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor()
    return output
  }

  private fun runFfmpeg(vararg args: String) {
    runFfmpegCmd(listOf(ffmpeg, "-y", "-loglevel", "error") + args.toList())
  }

  private fun runFfmpegCmd(cmd: List<String>) {
    val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    check(process.waitFor() == 0) {
      "FFmpeg command failed (exit ${process.exitValue()}): ${output.takeLast(500)}"
    }
  }
}

// ---------------------------------------------------------------------------
// Streaming frame reader backed by an FFmpeg subprocess.
// ---------------------------------------------------------------------------

/**
 * Reads raw RGBA frames lazily from an FFmpeg pipe.
 *
 * On the first call to [nextFrame] an `ffmpeg` process is spawned that decodes
 * the video to raw RGBA and pipes frames to stdout. Each frame is exactly
 * `width × height × 4` bytes.
 *
 * The [inputFile] is owned by this stream and deleted when frames are exhausted
 * or when cleanup is triggered.
 */
internal class FfmpegFrameStream(
  private val inputFile: File,
  private val width: Int,
  private val height: Int,
  override val frameCount: Long,
  private val frameRate: Double,
) : FrameStream {

  private var currentFrame = 0L
  private var process: Process? = null
  private var stream: InputStream? = null
  private val bytesPerFrame = width * height * 4
  @Volatile private var closed = false

  private fun ensureStarted() {
    if (process != null) return
    val cmd = listOf(
      FfmpegResolver.ffmpegPath, "-loglevel", "error",
      "-i", inputFile.absolutePath,
      "-f", "rawvideo", "-pix_fmt", "rgba",
      "pipe:1",
    )
    val pb = ProcessBuilder(cmd)
    pb.redirectError(ProcessBuilder.Redirect.DISCARD)
    val p = pb.start()
    process = p
    stream = p.inputStream.buffered()
  }

  override suspend fun nextFrame(): VideoFrame? = withContext(Dispatchers.IO) {
    if (closed || currentFrame >= frameCount) {
      cleanup()
      return@withContext null
    }
    ensureStarted()

    val buffer = ByteArray(bytesPerFrame)
    var read = 0
    while (read < bytesPerFrame) {
      val n = stream!!.read(buffer, read, bytesPerFrame - read)
      if (n == -1) {
        cleanup()
        return@withContext null
      }
      read += n
    }

    val timestampMs = (currentFrame * 1000.0 / frameRate).toLong()
    currentFrame++
    if (currentFrame >= frameCount) cleanup()

    VideoFrame(
      buffer = ByteArrayPixelBuffer(buffer),
      width = width,
      height = height,
      pixelFormat = PixelFormat.RGBA_8888,
      timestampMs = timestampMs,
    )
  }

  private fun cleanup() {
    if (closed) return
    closed = true
    runCatching { stream?.close() }
    runCatching { process?.destroyForcibly() }
    runCatching { inputFile.delete() }
  }
}
