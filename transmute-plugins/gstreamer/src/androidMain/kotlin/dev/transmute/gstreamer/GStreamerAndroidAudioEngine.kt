package dev.transmute.gstreamer

import dev.transmute.audio.AudioDecodeOptions
import dev.transmute.audio.AudioFormat
import dev.transmute.audio.AudioIR
import dev.transmute.audio.CanonicalAudioDecodeOptions
import dev.transmute.audio.CanonicalAudioEncodeOptions
import dev.transmute.audio.codecs.WavDecoder
import dev.transmute.audio.codecs.WavEncoder
import dev.transmute.common.PipelineContext
import dev.transmute.model.core.asBytes
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Audio encode / decode engine for Android via the GStreamer JNI bridge.
 *
 * Mirrors the Desktop subprocess pattern but uses [GStreamerJni.runPipeline]
 * instead of spawning a `gst-launch-1.0` process.
 */
internal object GStreamerAndroidAudioEngine {

  val available: Boolean get() = GStreamerJni.available

  private val wavDecoder = WavDecoder()
  private val wavEncoder = WavEncoder()

  /**
   * Encode [ir] to the target audio format using a GStreamer pipeline.
   */
  suspend fun encode(ir: AudioIR, encoderElement: String, tailElements: String = "", ext: String, context: PipelineContext): ByteArray =
    withContext(Dispatchers.IO) {
      check(available) { "GStreamer is not available on this device" }

      val wavBytes = wavEncoder.encode(ir, AudioFormat.Wav, CanonicalAudioEncodeOptions(), context).data
      val tmpIn = File.createTempFile("transmute_gst_in_", ".wav")
      val tmpOut = File.createTempFile("transmute_gst_out_", ".$ext")
      try {
        tmpIn.writeBytes(wavBytes)
        val desc = buildPipelineDesc(
          "filesrc location=${tmpIn.absolutePath}",
          "! wavparse",
          "! audioconvert",
          "! audioresample",
          "! $encoderElement",
          tailElements,
          "! filesink location=${tmpOut.absolutePath}",
        )
        GStreamerJni.runPipeline(desc)
        tmpOut.readBytes()
      } finally {
        tmpIn.delete()
        tmpOut.delete()
      }
    }

  /**
   * Decode [source] to an [AudioIR] via GStreamer -> WAV -> WavDecoder.
   */
  suspend fun decode(source: ByteArray, ext: String, options: AudioDecodeOptions, context: PipelineContext): AudioIR =
    withContext(Dispatchers.IO) {
      check(available) { "GStreamer is not available on this device" }

      val tmpIn = File.createTempFile("transmute_gst_in_", ".$ext")
      val tmpOut = File.createTempFile("transmute_gst_out_", ".wav")
      try {
        tmpIn.writeBytes(source)
        val desc = buildPipelineDesc(
          "filesrc location=${tmpIn.absolutePath}",
          "! decodebin",
          "! audioconvert",
          "! audioresample",
          "! audio/x-raw,format=S16LE",
          "! wavenc",
          "! filesink location=${tmpOut.absolutePath}",
        )
        GStreamerJni.runPipeline(desc)
        wavDecoder.decode(tmpOut.readBytes().asBytes(), CanonicalAudioDecodeOptions(), context)
      } finally {
        tmpIn.delete()
        tmpOut.delete()
      }
    }
}

// ---
// Helpers shared by all Android engines
// ---

/** Join pipeline tokens into a single descriptor, filtering blanks. */
internal fun buildPipelineDesc(vararg parts: String): List<String> =
  parts.filter { it.isNotBlank() }.flatMap { it.trim().split("\\s+".toRegex()) }
