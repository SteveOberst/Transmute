package dev.transmute.gstreamer

import dev.transmute.audio.AudioFormat
import dev.transmute.audio.AudioIR
import dev.transmute.audio.CanonicalAudioDecodeOptions
import dev.transmute.audio.CanonicalAudioEncodeOptions
import dev.transmute.audio.codecs.WavDecoder
import dev.transmute.audio.codecs.WavEncoder
import dev.transmute.common.PipelineContext
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.model.core.asBytes
import dev.transmute.video.AudioTrack
import dev.transmute.video.VideoDecodeOptions
import dev.transmute.video.VideoIR
import dev.transmute.video.VideoTrack
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Video engine using GStreamer subprocess (`gst-launch-1.0` / `gst-discoverer-1.0`).
 *
 * **Probe**: gst-discoverer-1.0 -> structured media info.
 *
 * **Decode**: gst-launch-1.0 pipes raw RGBA frames to stdout, audio extracted as WAV.
 *
 * **Encode**: raw RGBA frames + WAV audio temp files -> gst-launch-1.0 -> target container.
 */
internal object GStreamerVideoEngine {

  val available: Boolean get() = GStreamerResolver.available

  private val wavDecoder = WavDecoder()
  private val wavEncoder = WavEncoder()

  // ---
  // Probe
  // ---

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
    check(available) { "GStreamer is not available" }
    val discoverer = GStreamerResolver.gstDiscovererPath

    val process = ProcessBuilder(discoverer, input.toURI().toString())
      .redirectErrorStream(true)
      .start()
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor()

    return parseDiscovererOutput(output)
  }

  internal fun parseDiscovererOutput(output: String): VideoInfo {
    // Parse duration: "Duration: H:MM:SS.NNNNNNNNN" or "Duration: 0:00:05.000000000"
    val durationRegex = Regex("""Duration:\s*(\d+):(\d+):(\d+)\.(\d+)""")
    val durationMatch = durationRegex.find(output)
    val durationMs = if (durationMatch != null) {
      val (h, m, s, ns) = durationMatch.destructured
      h.toLong() * 3600_000 + m.toLong() * 60_000 + s.toLong() * 1000 +
        (ns.toLong() / 1_000_000)
    } else {
      0L
    }

    // Parse video properties from either caps strings or the human-readable
    // gst-discoverer output used by newer Linux packages.
    val width = firstIntMatch(output, listOf(
      Regex("""width=\(int\)(\d+)""", RegexOption.IGNORE_CASE),
      Regex("""Width:\s*(\d+)""", RegexOption.IGNORE_CASE),
    ))
    val height = firstIntMatch(output, listOf(
      Regex("""height=\(int\)(\d+)""", RegexOption.IGNORE_CASE),
      Regex("""Height:\s*(\d+)""", RegexOption.IGNORE_CASE),
    ))
    val frameRate = firstFractionMatch(output, listOf(
      Regex("""framerate=\(fraction\)(\d+)/(\d+)""", RegexOption.IGNORE_CASE),
      Regex("""Frame rate:\s*(\d+)/(\d+)""", RegexOption.IGNORE_CASE),
      Regex("""Framerate:\s*(\d+)/(\d+)""", RegexOption.IGNORE_CASE),
    ))

    val frameCount = if (frameRate > 0 && durationMs > 0) {
      ((durationMs * frameRate) / 1000.0).toLong().coerceAtLeast(1L)
    } else {
      1L
    }

    // Parse audio properties
    val hasAudio = output.contains("audio #", ignoreCase = true) ||
      output.contains("audio:", ignoreCase = true) ||
      output.contains("audio/", ignoreCase = true)
    val audioSampleRate = firstIntMatch(output, listOf(
      Regex("""rate=\(int\)(\d+)""", RegexOption.IGNORE_CASE),
      Regex("""Sample rate:\s*(\d+)""", RegexOption.IGNORE_CASE),
    )).takeIf { hasAudio }
    val audioChannels = firstIntMatch(output, listOf(
      Regex("""channels=\(int\)(\d+)""", RegexOption.IGNORE_CASE),
      Regex("""Channels:\s*(\d+)""", RegexOption.IGNORE_CASE),
    )).takeIf { hasAudio }

    return VideoInfo(
      width = width,
      height = height,
      frameRate = frameRate,
      frameCount = frameCount,
      durationMs = durationMs,
      hasAudio = hasAudio,
      audioSampleRate = audioSampleRate,
      audioChannels = audioChannels,
    )
  }

  private fun firstIntMatch(output: String, patterns: List<Regex>): Int {
    for (pattern in patterns) {
      val value = pattern.find(output)?.groupValues?.getOrNull(1)?.toIntOrNull()
      if (value != null) return value
    }
    return 0
  }

  private fun firstFractionMatch(output: String, patterns: List<Regex>): Double {
    for (pattern in patterns) {
      val match = pattern.find(output) ?: continue
      val numerator = match.groupValues.getOrNull(1)?.toDoubleOrNull() ?: continue
      val denominator = match.groupValues.getOrNull(2)?.toDoubleOrNull() ?: continue
      if (denominator > 0.0) return numerator / denominator
    }
    return 0.0
  }

  // ---
  // Decode
  // ---

  suspend fun decode(source: ByteArray, ext: String, options: VideoDecodeOptions, context: PipelineContext): VideoIR =
    withContext(Dispatchers.IO) {
      check(available) { "GStreamer is not available on this system" }

      val tmpIn = File.createTempFile("transmute_gst_vid_in_", ".$ext")
      tmpIn.deleteOnExit()
      tmpIn.writeBytes(source)

      val info = probe(tmpIn)
      val timeRange = options.decodeRange?.timeframe()

      // Extract audio (if present)
      val audioTrack = if (info.hasAudio) {
        val tmpAudio = File.createTempFile("transmute_gst_vid_audio_", ".wav")
        try {
          val args = buildGstPipeline(
            "filesrc", "location=${tmpIn.absolutePath.toGstPath()}",
            "!", "decodebin",
            "!", "audioconvert",
            "!", "audioresample",
            "!", "audio/x-raw,format=S16LE",
            "!", "wavenc",
            "!", "filesink", "location=${tmpAudio.absolutePath.toGstPath()}",
          )
          runGstLaunch(args)
          val audioIR = wavDecoder.decode(
            tmpAudio.readBytes().asBytes(),
            CanonicalAudioDecodeOptions(),
            context,
          )
          AudioTrack(samples = audioIR.samples, sampleStream = null)
        } finally {
          tmpAudio.delete()
        }
      } else {
        null
      }

      val durationMs = timeRange?.durationMs ?: info.durationMs
      val frameCount = if (timeRange != null) {
        ((durationMs * info.frameRate) / 1000.0).toLong().coerceAtLeast(1L)
      } else {
        info.frameCount
      }

      // Create streaming frame reader (owns tmpIn - deletes when exhausted)
      val frameStream = GStreamerFrameStream(
        inputFile = tmpIn,
        width = info.width,
        height = info.height,
        frameCount = frameCount,
        frameRate = info.frameRate,
        timestampOffsetMs = timeRange?.startMs ?: 0,
      )

      VideoIR(
        videoTrack = VideoTrack(
          width = info.width,
          height = info.height,
          frameRate = info.frameRate,
          frames = frameStream,
        ),
        audioTrack = audioTrack,
        durationMs = durationMs,
      )
    }

  // ---
  // Encode
  // ---

  suspend fun encode(
    ir: VideoIR,
    videoEncoder: String,
    audioEncoder: String?,
    muxElement: String,
    ext: String,
    extraElements: List<String> = emptyList(),
    context: PipelineContext,
  ): ByteArray = withContext(Dispatchers.IO) {
    check(available) { "GStreamer is not available on this system" }

    val width = ir.videoTrack.width
    val height = ir.videoTrack.height
    val fps = ir.videoTrack.frameRate
    val frames = ir.videoTrack.frames

    val tmpFrames = File.createTempFile("transmute_gst_vid_frames_", ".raw")
    val tmpAudio = if (ir.audioTrack != null) {
      File.createTempFile("transmute_gst_vid_audio_enc_", ".wav")
    } else {
      null
    }
    val tmpOut = File.createTempFile("transmute_gst_vid_out_", ".$ext")

    try {
      // Write raw RGBA frames to temp file
      tmpFrames.outputStream().buffered().use { os ->
        while (true) {
          val frame = frames.nextFrame() ?: break
          val pixelData = (frame.buffer as ByteArrayPixelBuffer).data
          os.write(pixelData)
        }
      }

      // Write audio WAV (if present)
      val audio = ir.audioTrack
      if (audio != null && tmpAudio != null) {
        val audioIR = AudioIR(
          samples = audio.samples,
          sampleRate = audio.samples.sampleRate,
          channelCount = audio.samples.channelCount,
          durationMs = ir.durationMs,
        )
        val wavBytes = wavEncoder.encode(
          audioIR,
          AudioFormat.Wav,
          CanonicalAudioEncodeOptions(),
          context,
        ).data
        tmpAudio.writeBytes(wavBytes)
      }

      // Build gst-launch-1.0 pipeline
      val fpsNum = fps.toInt().coerceAtLeast(1)
      val args = buildList {
        add(GStreamerResolver.gstLaunchPath)
        add("-e")
        add("--quiet")

        // Video input (raw RGBA from file)
        add("filesrc")
        add("location=${tmpFrames.absolutePath.toGstPath()}")
        add("!")
        add("rawvideoparse")
        add("format=rgba")
        add("width=$width")
        add("height=$height")
        add("framerate=$fpsNum/1")
        add("!")
        add("videoconvert")
        add("!")
        add(videoEncoder)
        addAll(extraElements)
        add("!")
        add("$muxElement")
        add("name=mux")
        add("!")
        add("filesink")
        add("location=${tmpOut.absolutePath.toGstPath()}")

        // Audio input
        if (tmpAudio != null && audioEncoder != null) {
          add("filesrc")
          add("location=${tmpAudio.absolutePath.toGstPath()}")
          add("!")
          add("wavparse")
          add("!")
          add("audioconvert")
          add("!")
          add(audioEncoder)
          add("!")
          add("mux.")
        }
      }

      runGstLaunch(args)
      tmpOut.readBytes()
    } finally {
      tmpFrames.delete()
      tmpAudio?.delete()
      tmpOut.delete()
    }
  }
}
