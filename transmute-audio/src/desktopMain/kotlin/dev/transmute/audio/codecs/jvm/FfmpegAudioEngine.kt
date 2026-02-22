package dev.transmute.audio.codecs.jvm

import dev.transmute.audio.CanonicalAudioDecodeOptions
import dev.transmute.audio.CanonicalAudioEncodeOptions
import dev.transmute.audio.AudioFormat
import dev.transmute.audio.AudioIR
import dev.transmute.audio.codecs.WavDecoder
import dev.transmute.audio.codecs.WavEncoder
import dev.transmute.core.TransmuteContext
import dev.transmute.core.FfmpegResolver
import dev.transmute.core.asBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Thin wrapper around the `ffmpeg` binary for encoding and decoding
 * audio formats that lack a pure-Java implementation.
 *
 * FFmpeg is resolved via [FfmpegResolver] which prefers the bundled
 * binary, falls back to PATH lookup, and can be overridden via
 * [dev.transmute.core.TransmuteConfig.ffmpeg].
 *
 * Codecs using this engine should guard on [available] so the application
 * gracefully degrades when FFmpeg is not installed.
 */
internal object FfmpegAudioEngine {

  /** `true` when a usable `ffmpeg` binary has been resolved. */
  val available: Boolean get() = FfmpegResolver.available

  /** Resolved path to the `ffmpeg` binary. */
  private val ffmpeg: String get() = FfmpegResolver.ffmpegPath

  private val wavDecoder = WavDecoder()
  private val wavEncoder = WavEncoder()

  /**
   * Encodes [ir] to the target format using FFmpeg.
   *
   * Flow: AudioIR → WAV (pure-Kotlin) → temp file → ffmpeg → temp file → bytes.
   *
   * @param codec     FFmpeg codec name, e.g. `"aac"`, `"libvorbis"`, `"flac"`.
   * @param format    FFmpeg output format (container), e.g. `"adts"`, `"ogg"`, `"flac"`.
   * @param ext       File extension for the temp output file.
   * @param bitrate   Target bitrate string, e.g. `"128k"`. Pass `null` for lossless codecs.
   * @param extraArgs Additional FFmpeg arguments.
   */
  suspend fun encode(
    ir: AudioIR,
    codec: String,
    format: String,
    ext: String,
    bitrate: String? = "128k",
    extraArgs: List<String> = emptyList(),
    context: TransmuteContext,
  ): ByteArray = withContext(Dispatchers.IO) {
    check(available) { "FFmpeg is not available on this system" }

    val wavBytes = wavEncoder.encode(ir, AudioFormat.Wav, CanonicalAudioEncodeOptions(), context).data
    val tmpIn = File.createTempFile("transmute_in_", ".wav")
    val tmpOut = File.createTempFile("transmute_out_", ".$ext")
    try {
      tmpIn.writeBytes(wavBytes)

      val cmd = buildList {
        add(ffmpeg); add("-y"); add("-loglevel"); add("error")
        add("-i"); add(tmpIn.absolutePath)
        add("-codec:a"); add(codec)
        if (bitrate != null) { add("-b:a"); add(bitrate) }
        addAll(extraArgs)
        add("-f"); add(format)
        add(tmpOut.absolutePath)
      }

      val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
      val output = process.inputStream.bufferedReader().readText()

      check(process.waitFor() == 0) {
        "FFmpeg encode to $ext failed (exit ${process.exitValue()}): ${output.takeLast(500)}"
      }

      tmpOut.readBytes()
    } finally {
      tmpIn.delete()
      tmpOut.delete()
    }
  }

  /**
   * Decodes [source] to an [AudioIR] by converting to WAV via FFmpeg
   * and then parsing the WAV with our pure-Kotlin decoder.
   *
   * Flow: bytes → temp file → ffmpeg → WAV temp file → WavDecoder → AudioIR.
   *
   * @param ext  File extension hint for the input, e.g. `"aac"`, `"m4a"`.
   */
  suspend fun decode(
    source: ByteArray,
    ext: String,
    context: TransmuteContext,
  ): AudioIR = withContext(Dispatchers.IO) {
    check(available) { "FFmpeg is not available on this system" }

    val tmpIn = File.createTempFile("transmute_in_", ".$ext")
    val tmpOut = File.createTempFile("transmute_out_", ".wav")
    try {
      tmpIn.writeBytes(source)

      val cmd = listOf(
        ffmpeg, "-y", "-loglevel", "error",
        "-i", tmpIn.absolutePath,
        "-codec:a", "pcm_s16le",
        "-f", "wav",
        tmpOut.absolutePath,
      )

      val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
      val output = process.inputStream.bufferedReader().readText()

      check(process.waitFor() == 0) {
        "FFmpeg decode from $ext failed (exit ${process.exitValue()}): ${output.takeLast(500)}"
      }

      wavDecoder.decode(tmpOut.readBytes().asBytes(), CanonicalAudioDecodeOptions(), context)
    } finally {
      tmpIn.delete()
      tmpOut.delete()
    }
  }
}
