package dev.transmute.gstreamer

import dev.transmute.audio.AudioFormat
import dev.transmute.audio.CanonicalAudioDecodeOptions
import dev.transmute.audio.CanonicalAudioEncodeOptions
import dev.transmute.gstreamer.GStreamerTestHelpers.requireGStreamer
import dev.transmute.gstreamer.GStreamerTestHelpers.requireGStreamerElement
import dev.transmute.gstreamer.GStreamerTestHelpers.testContext
import dev.transmute.image.ByteArrayPixelBuffer
import dev.transmute.image.CanonicalImageDecodeOptions
import dev.transmute.image.HeifEncodeOptions
import dev.transmute.image.ImageFormat
import dev.transmute.video.CanonicalVideoDecodeOptions
import dev.transmute.video.CanonicalVideoEncodeOptions
import dev.transmute.video.VideoFormat
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Phase 2 — Content fidelity integration tests.
 *
 * These tests go beyond "encode produces bytes / decode produces IR" (Phase 1)
 * and verify that the *content* survives the encode → decode roundtrip:
 *
 * - Audio waveform preservation (RMS within tolerance)
 * - Audio duration accuracy (±50 ms)
 * - Video dimension roundtrip (exact match)
 * - Video frame count (within ±1 of expected)
 * - Image pixel fidelity (mean absolute error < threshold)
 *
 * All tests are **soft-skipped** when GStreamer is not installed locally.
 */
class ContentFidelityTest {

    private val ctx = testContext()

    // ───────────────────────────────────────────────────────────────────────
    // Audio waveform preservation
    // ───────────────────────────────────────────────────────────────────────

    /**
     * Encode a 440 Hz sine, decode it back, and verify the RMS of the decoded
     * signal is within ±5 dB of the original. Lossy codecs (AAC, Opus) will
     * attenuate somewhat but should not destroy the signal entirely.
     */
    @Test
    fun aac_waveformPreservation_rmsWithinTolerance() = runTest {
        requireGStreamer {
            val original = GStreamerTestHelpers.sineWave(durationMs = 500, sampleRate = 44100)
            val originalRmsDb = rmsDb(original.samples.data)

            val codec = GstAacCodec()
            val encoded = codec.encode(original, AudioFormat.Aac, CanonicalAudioEncodeOptions(), ctx)
            val decoded = codec.decode(encoded, CanonicalAudioDecodeOptions(), ctx)

            val decodedRmsDb = rmsDb(decoded.samples.data)
            val diff = abs(originalRmsDb - decodedRmsDb)
            assertTrue(
                diff < 5.0,
                "AAC waveform RMS should be within 5 dB of original. " +
                    "Original=${originalRmsDb} dB, Decoded=${decodedRmsDb} dB, Diff=${diff} dB",
            )
        }
    }

    @Test
    fun m4a_waveformPreservation_rmsWithinTolerance() = runTest {
        requireGStreamer {
            val original = GStreamerTestHelpers.sineWave(durationMs = 500, sampleRate = 44100)
            val originalRmsDb = rmsDb(original.samples.data)

            val codec = GstM4aCodec()
            val encoded = codec.encode(original, AudioFormat.M4a, CanonicalAudioEncodeOptions(), ctx)
            val decoded = codec.decode(encoded, CanonicalAudioDecodeOptions(), ctx)

            val decodedRmsDb = rmsDb(decoded.samples.data)
            val diff = abs(originalRmsDb - decodedRmsDb)
            assertTrue(
                diff < 5.0,
                "M4A waveform RMS should be within 5 dB. " +
                    "Original=${originalRmsDb} dB, Decoded=${decodedRmsDb} dB, Diff=${diff} dB",
            )
        }
    }

    @Test
    fun opus_waveformPreservation_rmsWithinTolerance() = runTest {
        requireGStreamer {
            val original = GStreamerTestHelpers.sineWave(durationMs = 500, sampleRate = 48000)
            val originalRmsDb = rmsDb(original.samples.data)

            val codec = GstOpusCodec()
            val encoded = codec.encode(original, AudioFormat.Opus, CanonicalAudioEncodeOptions(), ctx)
            val decoded = codec.decode(encoded, CanonicalAudioDecodeOptions(), ctx)

            val decodedRmsDb = rmsDb(decoded.samples.data)
            val diff = abs(originalRmsDb - decodedRmsDb)
            assertTrue(
                diff < 5.0,
                "Opus waveform RMS should be within 5 dB. " +
                    "Original=${originalRmsDb} dB, Decoded=${decodedRmsDb} dB, Diff=${diff} dB",
            )
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // Audio duration accuracy
    // ───────────────────────────────────────────────────────────────────────

    @Test
    fun aac_durationAccuracy_1s() = runTest {
        requireGStreamer {
            assertDurationWithin(GstAacCodec(), AudioFormat.Aac, inputMs = 1000, toleranceMs = 50)
        }
    }

    @Test
    fun aac_durationAccuracy_100ms() = runTest {
        requireGStreamer {
            assertDurationWithin(GstAacCodec(), AudioFormat.Aac, inputMs = 100, toleranceMs = 80)
        }
    }

    @Test
    fun aac_durationAccuracy_5s() = runTest {
        requireGStreamer {
            assertDurationWithin(GstAacCodec(), AudioFormat.Aac, inputMs = 5000, toleranceMs = 50)
        }
    }

    @Test
    fun m4a_durationAccuracy_1s() = runTest {
        requireGStreamer {
            assertDurationWithin(GstM4aCodec(), AudioFormat.M4a, inputMs = 1000, toleranceMs = 50)
        }
    }

    @Test
    fun opus_durationAccuracy_1s() = runTest {
        requireGStreamer {
            assertDurationWithin(GstOpusCodec(), AudioFormat.Opus, inputMs = 1000, toleranceMs = 50)
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // Video dimension roundtrip
    // ───────────────────────────────────────────────────────────────────────

    @Test
    fun mp4_dimensionRoundtrip_320x240() = runTest {
        requireGStreamer {
            assertVideoDimensions(GstMp4Codec(), VideoFormat.Mp4, 320, 240)
        }
    }

    @Test
    fun webm_dimensionRoundtrip_320x240() = runTest {
        requireGStreamer {
            assertVideoDimensions(GstWebmCodec(), VideoFormat.Webm, 320, 240)
        }
    }

    @Test
    fun mkv_dimensionRoundtrip_320x240() = runTest {
        requireGStreamer {
            assertVideoDimensions(GstMkvCodec(), VideoFormat.Mkv, 320, 240)
        }
    }

    @Test
    fun avi_dimensionRoundtrip_320x240() = runTest {
        requireGStreamer {
            assertVideoDimensions(GstAviCodec(), VideoFormat.Avi, 320, 240)
        }
    }

    @Test
    fun mov_dimensionRoundtrip_320x240() = runTest {
        requireGStreamer {
            assertVideoDimensions(GstMovCodec(), VideoFormat.Mov, 320, 240)
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // Video frame count
    // ───────────────────────────────────────────────────────────────────────

    @Test
    fun mp4_frameCount_10fps1s() = runTest {
        requireGStreamer {
            assertFrameCount(GstMp4Codec(), VideoFormat.Mp4, fps = 10.0, durationMs = 1000, expectedMin = 9, expectedMax = 11)
        }
    }

    @Test
    fun webm_frameCount_10fps1s() = runTest {
        requireGStreamer {
            assertFrameCount(GstWebmCodec(), VideoFormat.Webm, fps = 10.0, durationMs = 1000, expectedMin = 9, expectedMax = 11)
        }
    }

    @Test
    fun mkv_frameCount_10fps1s() = runTest {
        requireGStreamer {
            assertFrameCount(GstMkvCodec(), VideoFormat.Mkv, fps = 10.0, durationMs = 1000, expectedMin = 9, expectedMax = 11)
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // Image pixel fidelity
    // ───────────────────────────────────────────────────────────────────────

    @Test
    fun heif_pixelFidelity_solidRed() = runTest {
        requireGStreamerElement("x265enc") {
            assertImagePixelFidelity(
                format = ImageFormat.Heif,
                options = HeifEncodeOptions(quality = 0.95f),
                r = 255, g = 0, b = 0,
                // HEIF is lossy; allow fairly generous per-channel MAE
                maxMae = 15.0,
            )
        }
    }

    @Test
    fun heic_pixelFidelity_solidGreen() = runTest {
        requireGStreamerElement("x265enc") {
            assertImagePixelFidelity(
                format = ImageFormat.Heic,
                options = HeifEncodeOptions(quality = 0.95f, format = ImageFormat.Heic),
                r = 0, g = 255, b = 0,
                maxMae = 15.0,
            )
        }
    }

    @Test
    fun avif_pixelFidelity_solidBlue() = runTest {
        requireGStreamerElement("av1enc") {
            assertImagePixelFidelity(
                format = ImageFormat.Avif,
                options = HeifEncodeOptions(quality = 0.95f, format = ImageFormat.Avif),
                r = 0, g = 0, b = 255,
                maxMae = 15.0,
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    /** Compute RMS amplitude in dB (relative to 1.0 full scale). */
    private fun rmsDb(samples: FloatArray): Double {
        if (samples.isEmpty()) return -100.0
        var sumSq = 0.0
        for (s in samples) sumSq += s * s
        val rms = sqrt(sumSq / samples.size)
        return if (rms < 1e-10) -100.0 else 20.0 * ln(rms) / ln(10.0)
    }

    /** Assert that encode→decode preserves duration within tolerance. */
    private suspend fun assertDurationWithin(
        codec: dev.transmute.audio.AudioCodec,
        format: AudioFormat,
        inputMs: Long,
        toleranceMs: Long,
    ) {
        val ir = GStreamerTestHelpers.sineWave(durationMs = inputMs, sampleRate = 44100)
        val encoded = codec.encode(ir, format, CanonicalAudioEncodeOptions(), ctx)
        val decoded = codec.decode(encoded, CanonicalAudioDecodeOptions(), ctx)
        val diff = abs(decoded.durationMs - inputMs)
        assertTrue(
            diff <= toleranceMs,
            "Duration should be within ±${toleranceMs}ms of ${inputMs}ms. " +
                "Got ${decoded.durationMs}ms (diff=${diff}ms)",
        )
    }

    /** Assert that encode→decode preserves video dimensions exactly. */
    private suspend fun assertVideoDimensions(
        codec: dev.transmute.video.VideoCodec,
        format: VideoFormat,
        width: Int,
        height: Int,
    ) {
        val video = GStreamerTestHelpers.syntheticVideo(
            width = width, height = height, frameRate = 10.0, durationMs = 500,
        )
        val encoded = codec.encode(video, format, CanonicalVideoEncodeOptions(), ctx)
        assertTrue(encoded.isNotEmpty(), "Encoded output must not be empty")

        val decoded = codec.decode(encoded, CanonicalVideoDecodeOptions(), ctx)
        assertEquals(width, decoded.videoTrack.width, "$format width must survive roundtrip")
        assertEquals(height, decoded.videoTrack.height, "$format height must survive roundtrip")
    }

    /** Assert that encode→decode yields a frame count within expected range. */
    private suspend fun assertFrameCount(
        codec: dev.transmute.video.VideoCodec,
        format: VideoFormat,
        fps: Double,
        durationMs: Long,
        expectedMin: Long,
        expectedMax: Long,
    ) {
        val video = GStreamerTestHelpers.syntheticVideo(
            width = 160, height = 120, frameRate = fps, durationMs = durationMs,
        )
        val encoded = codec.encode(video, format, CanonicalVideoEncodeOptions(), ctx)
        val decoded = codec.decode(encoded, CanonicalVideoDecodeOptions(), ctx)

        val frameCount = decoded.videoTrack.frames.frameCount
        assertTrue(
            frameCount in expectedMin..expectedMax,
            "$format frame count should be in [$expectedMin, $expectedMax], got $frameCount",
        )
    }

    /**
     * Assert that encoding a solid-colour image and decoding it back
     * produces pixels whose mean absolute error (per channel) is below [maxMae].
     */
    private suspend fun assertImagePixelFidelity(
        format: ImageFormat,
        options: HeifEncodeOptions,
        r: Int,
        g: Int,
        b: Int,
        maxMae: Double,
    ) {
        val encoder = GstImageEncoder()
        val decoder = GstImageDecoder()

        val original = GStreamerTestHelpers.solidColor(64, 64, r, g, b)
        val encoded = encoder.encode(original, format, options, ctx)
        assertTrue(encoded.isNotEmpty(), "Encoded $format must not be empty")

        val decoded = decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)
        assertNotNull(decoded, "Decoded ImageIR must not be null")
        assertEquals(64, decoded.width, "Width must survive roundtrip")
        assertEquals(64, decoded.height, "Height must survive roundtrip")

        // Compute per-channel MAE
        val pixels = (decoded.buffer as ByteArrayPixelBuffer).data
        val pixelCount = decoded.width * decoded.height
        var errR = 0L; var errG = 0L; var errB = 0L
        for (i in 0 until pixelCount) {
            val off = i * 4  // RGBA_8888
            errR += abs((pixels[off].toInt() and 0xFF) - r)
            errG += abs((pixels[off + 1].toInt() and 0xFF) - g)
            errB += abs((pixels[off + 2].toInt() and 0xFF) - b)
        }
        val maeR = errR.toDouble() / pixelCount
        val maeG = errG.toDouble() / pixelCount
        val maeB = errB.toDouble() / pixelCount
        val avgMae = (maeR + maeG + maeB) / 3.0

        assertTrue(
            avgMae < maxMae,
            "$format pixel fidelity: avg MAE=${"%.2f".format(avgMae)} " +
                "(R=${"%.2f".format(maeR)}, G=${"%.2f".format(maeG)}, B=${"%.2f".format(maeB)}), " +
                "expected < $maxMae",
        )
    }
}
