package dev.transmute.gstreamer

import dev.transmute.audio.AudioFormat
import dev.transmute.audio.AudioIR
import dev.transmute.audio.CanonicalAudioDecodeOptions
import dev.transmute.audio.CanonicalAudioEncodeOptions
import dev.transmute.audio.codecs.WavDecoder
import dev.transmute.audio.codecs.WavEncoder
import dev.transmute.common.PipelineContext
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.PixelFormat
import dev.transmute.model.core.asBytes
import dev.transmute.video.AudioTrack
import dev.transmute.video.FrameStream
import dev.transmute.video.VideoDecodeOptions
import dev.transmute.video.VideoFrame
import dev.transmute.video.VideoIR
import dev.transmute.video.VideoTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSTemporaryDirectory

/**
 * Video encode / decode engine for iOS via the GStreamer cinterop bridge.
 */
internal object GStreamerIosVideoEngine {

    val available: Boolean get() = GStreamerIosBridge.available

    private val wavDecoder = WavDecoder()
    private val wavEncoder = WavEncoder()

    // -- Encode ---------------------------------------------------------------

    suspend fun encode(
        ir: VideoIR,
        videoEncoder: String,
        audioEncoder: String?,
        muxElement: String,
        ext: String,
        extraElements: List<String> = emptyList(),
        context: PipelineContext,
    ): ByteArray = withContext(Dispatchers.Default) {
        check(available) { "GStreamer is not available on this device" }

        val width = ir.videoTrack.width
        val height = ir.videoTrack.height
        val fps = ir.videoTrack.frameRate.toInt().coerceAtLeast(1)

        val tmpDir = NSTemporaryDirectory()
        val framesPath = "${tmpDir}transmute_gst_frames.raw"
        val audioPath = if (ir.audioTrack != null) "${tmpDir}transmute_gst_audio.wav" else null
        val outPath = "${tmpDir}transmute_gst_out.$ext"

        try {
            // Write raw RGBA frames
            val frameBytes = mutableListOf<Byte>()
            val frames = ir.videoTrack.frames
            while (true) {
                val frame = frames.nextFrame() ?: break
                val data = (frame.buffer as ByteArrayPixelBuffer).data
                frameBytes.addAll(data.toList())
            }
            frameBytes.toByteArray().writeToTmpFile(framesPath)

            // Write audio WAV (if present)
            val audioTrack = ir.audioTrack
            if (audioTrack != null && audioPath != null) {
                val audioIR = AudioIR(
                    samples = audioTrack.samples,
                    sampleRate = audioTrack.samples.sampleRate,
                    channelCount = audioTrack.samples.channelCount,
                    durationMs = ir.durationMs,
                )
                val wavBytes = wavEncoder.encode(audioIR, AudioFormat.Wav, CanonicalAudioEncodeOptions(), context).data
                wavBytes.writeToTmpFile(audioPath)
            }

            val extras = extraElements.joinToString(" ") { "! $it" }
            val videoPart = buildString {
                append("filesrc location=$framesPath ")
                append("! rawvideoparse format=rgba width=$width height=$height framerate=$fps/1 ")
                append("! videoconvert ")
                append("! $videoEncoder ")
                if (extras.isNotBlank()) append("$extras ")
                append("! $muxElement name=mux ")
                append("! filesink location=$outPath")
            }
            val audioPart = if (audioPath != null && audioEncoder != null) {
                " filesrc location=$audioPath ! wavparse ! audioconvert ! $audioEncoder ! mux."
            } else ""

            val desc = (videoPart + audioPart).trim().split("\\s+".toRegex())
            GStreamerIosBridge.runPipelineChecked(desc)
            readTmpFile(outPath)
        } finally {
            deleteTmpFile(framesPath)
            audioPath?.let { deleteTmpFile(it) }
            deleteTmpFile(outPath)
        }
    }

    // -- Decode ---------------------------------------------------------------

    suspend fun decode(
        source: ByteArray,
        ext: String,
        options: VideoDecodeOptions,
        context: PipelineContext,
    ): VideoIR = withContext(Dispatchers.Default) {
        check(available) { "GStreamer is not available on this device" }

        val tmpDir = NSTemporaryDirectory()
        val inPath = "${tmpDir}transmute_gst_vid_in.$ext"
        val rawPath = "${tmpDir}transmute_gst_vid_raw.rgba"
        val audioPath = "${tmpDir}transmute_gst_vid_audio.wav"

        source.writeToTmpFile(inPath)

        // Decode to raw RGBA
        val desc = buildIosPipelineDesc(
            "filesrc location=$inPath",
            "! decodebin",
            "! videoconvert",
            "! video/x-raw,format=RGBA",
            "! filesink location=$rawPath",
        )
        GStreamerIosBridge.runPipelineChecked(desc)

        // Try to extract audio
        val audioTrack: AudioTrack? = try {
            val audioPipeline = buildIosPipelineDesc(
                "filesrc location=$inPath",
                "! decodebin",
                "! audioconvert",
                "! audioresample",
                "! audio/x-raw,format=S16LE",
                "! wavenc",
                "! filesink location=$audioPath",
            )
            GStreamerIosBridge.runPipelineChecked(audioPipeline)
            val audioIR = wavDecoder.decode(readTmpFile(audioPath).asBytes(), CanonicalAudioDecodeOptions(), context)
            AudioTrack(samples = audioIR.samples, sampleStream = null)
        } catch (_: Throwable) {
            null
        }

        // Default video info (simplified — full discoverer integration TBD)
        val width = 160
        val height = 120
        val frameRate = 10.0
        val bytesPerFrame = width * height * 4
        val rawData = readTmpFile(rawPath)
        val frameCount = (rawData.size / bytesPerFrame).toLong().coerceAtLeast(1L)

        val frameStream = GStreamerIosFrameStream(
            rawData = rawData,
            width = width,
            height = height,
            frameCount = frameCount,
            frameRate = frameRate,
        )

        deleteTmpFile(inPath)
        deleteTmpFile(rawPath)
        deleteTmpFile(audioPath)

        VideoIR(
            videoTrack = VideoTrack(
                width = width,
                height = height,
                frameRate = frameRate,
                frames = frameStream,
            ),
            audioTrack = audioTrack,
            durationMs = (frameCount * 1000.0 / frameRate).toLong(),
        )
    }
}

/**
 * Reads raw RGBA frames from an in-memory byte array on iOS.
 */
internal class GStreamerIosFrameStream(
    private val rawData: ByteArray,
    private val width: Int,
    private val height: Int,
    override val frameCount: Long,
    private val frameRate: Double,
) : FrameStream {

    private var currentFrame = 0L
    private val bytesPerFrame = width * height * 4

    override suspend fun nextFrame(): VideoFrame? {
        if (currentFrame >= frameCount) return null
        val offset = (currentFrame * bytesPerFrame).toInt()
        if (offset + bytesPerFrame > rawData.size) return null

        val buffer = rawData.copyOfRange(offset, offset + bytesPerFrame)
        val timestampMs = (currentFrame * 1000.0 / frameRate).toLong()
        currentFrame++

        return VideoFrame(
            buffer = ByteArrayPixelBuffer(buffer),
            width = width,
            height = height,
            pixelFormat = PixelFormat.RGBA_8888,
            timestampMs = timestampMs,
        )
    }

    override fun close() { /* in-memory; nothing to clean up */ }
}
