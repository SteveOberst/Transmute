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
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSData
import platform.Foundation.writeToFile
import platform.Foundation.dataWithContentsOfFile

/**
 * Audio encode / decode engine for iOS via the GStreamer cinterop bridge.
 *
 * Uses the same temp-file + pipeline-descriptor pattern as Desktop/Android,
 * but uses [NSTemporaryDirectory] for temp files and
 * [GStreamerIosBridge.runPipelineChecked] for pipeline execution.
 */
internal object GStreamerIosAudioEngine {

    val available: Boolean get() = GStreamerIosBridge.available

    private val wavDecoder = WavDecoder()
    private val wavEncoder = WavEncoder()

    suspend fun encode(
        ir: AudioIR,
        encoderElement: String,
        tailElements: String = "",
        ext: String,
        context: PipelineContext,
    ): ByteArray = withContext(Dispatchers.Default) {
        check(available) { "GStreamer is not available on this device" }

        val wavBytes = wavEncoder.encode(ir, AudioFormat.Wav, CanonicalAudioEncodeOptions(), context).data
        val tmpDir = NSTemporaryDirectory()
        val inPath = "${tmpDir}transmute_gst_in.wav"
        val outPath = "${tmpDir}transmute_gst_out.$ext"

        try {
            wavBytes.writeToTmpFile(inPath)
            val desc = buildIosPipelineDesc(
                "filesrc location=$inPath",
                "! wavparse",
                "! audioconvert",
                "! audioresample",
                "! $encoderElement",
                tailElements,
                "! filesink location=$outPath",
            )
            GStreamerIosBridge.runPipelineChecked(desc)
            readTmpFile(outPath)
        } finally {
            deleteTmpFile(inPath)
            deleteTmpFile(outPath)
        }
    }

    suspend fun decode(
        source: ByteArray,
        ext: String,
        options: AudioDecodeOptions,
        context: PipelineContext,
    ): AudioIR = withContext(Dispatchers.Default) {
        check(available) { "GStreamer is not available on this device" }

        val tmpDir = NSTemporaryDirectory()
        val inPath = "${tmpDir}transmute_gst_in.$ext"
        val outPath = "${tmpDir}transmute_gst_out.wav"

        try {
            source.writeToTmpFile(inPath)
            val desc = buildIosPipelineDesc(
                "filesrc location=$inPath",
                "! decodebin",
                "! audioconvert",
                "! audioresample",
                "! audio/x-raw,format=S16LE",
                "! wavenc",
                "! filesink location=$outPath",
            )
            GStreamerIosBridge.runPipelineChecked(desc)
            wavDecoder.decode(readTmpFile(outPath).asBytes(), CanonicalAudioDecodeOptions(), context)
        } finally {
            deleteTmpFile(inPath)
            deleteTmpFile(outPath)
        }
    }
}

// ---------------------------------------------------------------------------
// iOS file helpers  (shared by all iOS engines)
// ---------------------------------------------------------------------------

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
internal fun ByteArray.writeToTmpFile(path: String) {
    val data = this.toNSData()
    data.writeToFile(path, atomically = true)
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
internal fun readTmpFile(path: String): ByteArray {
    val data = NSData.dataWithContentsOfFile(path)
        ?: error("Failed to read file: $path")
    return data.toByteArray()
}

internal fun deleteTmpFile(path: String) {
    try {
        platform.Foundation.NSFileManager.defaultManager.removeItemAtPath(path, null)
    } catch (_: Throwable) { /* best effort */ }
}

internal fun buildIosPipelineDesc(vararg parts: String): List<String> =
    parts.filter { it.isNotBlank() }.flatMap { it.trim().split("\\s+".toRegex()) }

// ---------------------------------------------------------------------------
// NSData <-> ByteArray conversions
// ---------------------------------------------------------------------------

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
internal fun ByteArray.toNSData(): NSData = kotlinx.cinterop.memScoped {
    if (isEmpty()) return NSData()
    kotlinx.cinterop.usePinned {
        NSData.create(bytes = it.addressOf(0), length = size.toULong())
    }
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val len = length.toInt()
    if (len == 0) return ByteArray(0)
    val result = ByteArray(len)
    kotlinx.cinterop.usePinned {
        platform.posix.memcpy(it.addressOf(0), bytes, length)
    }
    return result
}
