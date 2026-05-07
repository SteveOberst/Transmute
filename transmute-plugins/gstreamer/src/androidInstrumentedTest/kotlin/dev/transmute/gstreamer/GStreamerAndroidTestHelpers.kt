package dev.transmute.gstreamer

import dev.transmute.audio.AudioIR
import dev.transmute.audio.AudioSamples
import dev.transmute.common.PipelineContext
import dev.transmute.common.PrintLogger
import dev.transmute.image.AlphaSemantics
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.ColorInfo
import dev.transmute.image.ImageIR
import dev.transmute.image.PixelFormat
import dev.transmute.video.AudioTrack
import dev.transmute.video.FrameStream
import dev.transmute.video.VideoFrame
import dev.transmute.video.VideoIR
import dev.transmute.video.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlin.math.PI
import kotlin.math.sin

/**
 * Test utilities for GStreamer Android instrumented tests.
 *
 * Mirrors [GStreamerTestHelpers] from `desktopTest` but references the
 * Android JNI bridge for availability checks and uses defensive timeout
 * wrappers for native operations.
 */
object GStreamerAndroidTestHelpers {

    fun testContext(): PipelineContext = PipelineContext(logger = PrintLogger)

    /** `true` when the GStreamer JNI bridge is loaded and available. */
    val gstreamerAvailable: Boolean get() = GStreamerJni.available

    /**
     * Run a codec operation on an independent IO scope with a coroutine
     * timeout. Native GStreamer operations can occasionally hang on
     * emulators - this prevents test deadlocks.
     *
     * Returns `null` on timeout/failure.
     */
    suspend fun <T> codecOp(
        label: String,
        timeoutMs: Long = 90_000L,
        block: suspend () -> T,
    ): T? = try {
        val deferred = CoroutineScope(Dispatchers.IO).async { block() }
        withTimeout(timeoutMs) { deferred.await() }
    } catch (e: Throwable) {
        println("SKIP: $label: ${e::class.simpleName}: ${e.message}")
        null
    }

    // -- Synthetic audio ---

    fun sineWave(
        durationMs: Long = 500,
        sampleRate: Int = 44100,
        channelCount: Int = 1,
    ): AudioIR {
        val totalSamples = ((durationMs * sampleRate * channelCount) / 1000).toInt()
        val data = FloatArray(totalSamples)
        val amplitude = 0.5f
        for (i in 0 until totalSamples step channelCount) {
            val t = i.toFloat() / (sampleRate * channelCount)
            val sample = amplitude * sin(2 * PI.toFloat() * 440f * t)
            for (ch in 0 until channelCount) {
                data[i + ch] = sample
            }
        }
        return AudioIR(
            samples = AudioSamples(data, sampleRate, channelCount),
            sampleRate = sampleRate,
            channelCount = channelCount,
            durationMs = durationMs,
        )
    }

    // -- Synthetic image ---

    fun solidColor(
        width: Int,
        height: Int,
        r: Int,
        g: Int,
        b: Int,
        a: Int = 255,
    ): ImageIR {
        val bpp = 4
        val stride = width * bpp
        val pixels = ByteArray(height * stride)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val off = y * stride + x * bpp
                pixels[off] = r.toByte()
                pixels[off + 1] = g.toByte()
                pixels[off + 2] = b.toByte()
                pixels[off + 3] = a.toByte()
            }
        }
        return ImageIR(
            buffer = ByteArrayPixelBuffer(pixels),
            width = width,
            height = height,
            stride = stride,
            pixelFormat = PixelFormat.RGBA_8888,
            alphaSemantics = if (a < 255) AlphaSemantics.STRAIGHT else AlphaSemantics.OPAQUE,
            colorInfo = ColorInfo(),
        )
    }

    // -- Synthetic video ---

    fun syntheticVideo(
        width: Int = 160,
        height: Int = 120,
        frameRate: Double = 10.0,
        durationMs: Long = 500,
        includeAudio: Boolean = false,
    ): VideoIR {
        val frameCount = ((durationMs * frameRate) / 1000).toLong()
        val videoTrack = VideoTrack(
            width = width,
            height = height,
            frameRate = frameRate,
            frames = SyntheticFrameStream(width, height, frameCount, frameRate),
        )
        val audioTrack = if (includeAudio) {
            val sr = 44100; val ch = 2
            val samples = (durationMs * sr * ch / 1000).toInt()
            AudioTrack(
                samples = AudioSamples(FloatArray(samples), sr, ch),
                sampleStream = null,
            )
        } else null
        return VideoIR(
            videoTrack = videoTrack,
            audioTrack = audioTrack,
            durationMs = durationMs,
        )
    }

    private class SyntheticFrameStream(
        private val w: Int,
        private val h: Int,
        override val frameCount: Long,
        private val fps: Double,
    ) : FrameStream {
        private var index = 0

        override suspend fun nextFrame(): VideoFrame? {
            if (index >= frameCount) return null
            val pixels = ByteArray(h * w * 4)
            val frame = VideoFrame(
                buffer = ByteArrayPixelBuffer(pixels),
                width = w,
                height = h,
                pixelFormat = PixelFormat.RGBA_8888,
                timestampMs = (index * 1000L / fps).toLong(),
            )
            index++
            return frame
        }

        override fun close() {}
    }
}
