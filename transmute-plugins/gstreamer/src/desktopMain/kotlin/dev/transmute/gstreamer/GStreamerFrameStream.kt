package dev.transmute.gstreamer

import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.PixelFormat
import dev.transmute.video.FrameStream
import dev.transmute.video.VideoFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * Reads raw RGBA frames lazily from a GStreamer pipe.
 *
 * On the first call to [nextFrame] a `gst-launch-1.0` process is spawned that
 * decodes the video to raw RGBA and pipes frames to stdout via `fdsink`.
 * Each frame is exactly `width × height × 4` bytes.
 *
 * The [inputFile] is owned by this stream and deleted when frames are exhausted
 * or when cleanup is triggered.
 */
internal class GStreamerFrameStream(
    private val inputFile: File,
    private val width: Int,
    private val height: Int,
    override val frameCount: Long,
    private val frameRate: Double,
    private val timestampOffsetMs: Long = 0,
) : FrameStream {

    private var currentFrame = 0L
    private var process: Process? = null
    private var stream: InputStream? = null
    private val bytesPerFrame = width * height * 4
    @Volatile private var closed = false

    private fun ensureStarted() {
        if (process != null) return
        val args = buildList {
            add(GStreamerResolver.gstLaunchPath)
            add("-e")
            add("--quiet")
            add("filesrc")
            add("location=${inputFile.absolutePath.toGstPath()}")
            add("!")
            add("decodebin")
            add("!")
            add("videoconvert")
            add("!")
            add("video/x-raw,format=RGBA")
            add("!")
            add("fdsink")
            add("fd=1")
        }
        val pb = ProcessBuilder(args)
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

        val timestampMs = timestampOffsetMs + (currentFrame * 1000.0 / frameRate).toLong()
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

    override fun close() = cleanup()
}
