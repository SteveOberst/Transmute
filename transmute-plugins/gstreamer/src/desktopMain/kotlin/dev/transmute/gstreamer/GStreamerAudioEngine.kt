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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Audio engine using GStreamer subprocess (`gst-launch-1.0`).
 *
 * **Decode**: source -> temp file -> gst-launch-1.0 (-> WAV) -> temp -> WavDecoder -> AudioIR
 *
 * **Encode**: AudioIR -> WavEncoder (-> WAV) -> temp -> gst-launch-1.0 -> temp -> bytes
 */
internal object GStreamerAudioEngine {

    val available: Boolean get() = GStreamerResolver.available

    private val wavDecoder = WavDecoder()
    private val wavEncoder = WavEncoder()

    /**
     * Encode [ir] to the target audio format using GStreamer.
     *
     * @param encoderElement GStreamer encoder element, e.g. `"fdkaacenc"`, `"opusenc"`.
     * @param tailElements   Elements after the encoder (parser, muxer), e.g. `"! aacparse"`.
     * @param ext            File extension for the output temp file.
     */
    suspend fun encode(
        ir: AudioIR,
        encoderElement: String,
        tailElements: String = "",
        ext: String,
        context: PipelineContext,
    ): ByteArray = withContext(Dispatchers.IO) {
        check(available) { "GStreamer is not available on this system" }

        val wavBytes = wavEncoder.encode(ir, AudioFormat.Wav, CanonicalAudioEncodeOptions(), context).data
        val tmpIn = File.createTempFile("transmute_gst_in_", ".wav")
        val tmpOut = File.createTempFile("transmute_gst_out_", ".$ext")
        try {
            tmpIn.writeBytes(wavBytes)

            val args = buildGstPipeline(
                "filesrc", "location=${tmpIn.absolutePath.toGstPath()}",
                "!", "wavparse",
                "!", "audioconvert",
                "!", "audioresample",
                "!", encoderElement,
            ) + parseTailElements(tailElements) + listOf(
                "!", "filesink", "location=${tmpOut.absolutePath.toGstPath()}",
            )

            runGstLaunch(args)
            tmpOut.readBytes()
        } finally {
            tmpIn.delete()
            tmpOut.delete()
        }
    }

    /**
     * Decode [source] to an [AudioIR] by converting to WAV via GStreamer
     * and then parsing the WAV with the pure-Kotlin WavDecoder.
     */
    suspend fun decode(
        source: ByteArray,
        ext: String,
        options: AudioDecodeOptions,
        context: PipelineContext,
    ): AudioIR = withContext(Dispatchers.IO) {
        check(available) { "GStreamer is not available on this system" }

        val tmpIn = File.createTempFile("transmute_gst_in_", ".$ext")
        val tmpOut = File.createTempFile("transmute_gst_out_", ".wav")
        try {
            tmpIn.writeBytes(source)

            val args = buildGstPipeline(
                "filesrc", "location=${tmpIn.absolutePath.toGstPath()}",
                "!", "decodebin",
                "!", "audioconvert",
                "!", "audioresample",
                "!", "audio/x-raw,format=S16LE",
                "!", "wavenc",
                "!", "filesink", "location=${tmpOut.absolutePath.toGstPath()}",
            )

            runGstLaunch(args)
            wavDecoder.decode(tmpOut.readBytes().asBytes(), CanonicalAudioDecodeOptions(), context)
        } finally {
            tmpIn.delete()
            tmpOut.delete()
        }
    }
}

// ---------------------------------------------------------------------------
// GStreamer subprocess helpers (shared by all engines)
// ---------------------------------------------------------------------------

/** Build the argument list for `gst-launch-1.0 -e --quiet ...`. */
internal fun buildGstPipeline(vararg elements: String): List<String> = buildList {
    add(GStreamerResolver.gstLaunchPath)
    add("-e")  // send EOS before shutting down
    add("--quiet")
    addAll(elements)
}

/** Parse tail element strings like `"! aacparse ! mp4mux"` into a flat arg list. */
internal fun parseTailElements(tail: String): List<String> {
    if (tail.isBlank()) return emptyList()
    return tail.trim().split("\\s+".toRegex())
}

/** Run `gst-launch-1.0` with the given args and check for errors. */
internal fun runGstLaunch(args: List<String>) {
    val process = ProcessBuilder(args).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    check(process.waitFor() == 0) {
        "GStreamer pipeline failed (exit ${process.exitValue()}): ${output.takeLast(500)}"
    }
}

/** Convert a native path to forward slashes for GStreamer's `filesrc location=`. */
internal fun String.toGstPath(): String = replace("\\", "/")
