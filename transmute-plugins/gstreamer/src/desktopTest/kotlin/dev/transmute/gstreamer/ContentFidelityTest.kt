package dev.transmute.gstreamer

import dev.transmute.audio.AudioFormat
import dev.transmute.audio.CanonicalAudioDecodeOptions
import dev.transmute.audio.CanonicalAudioEncodeOptions
import dev.transmute.gstreamer.GStreamerTestHelpers.testContext
import dev.transmute.video.CanonicalVideoDecodeOptions
import dev.transmute.video.CanonicalVideoEncodeOptions
import dev.transmute.video.VideoFormat
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 2 - Content fidelity integration tests.
 *
 * These tests go beyond "encode produces bytes / decode produces IR" (Phase 1)
 * and verify that the *content* survives the encode -> decode roundtrip:
 *
 * - Audio waveform preservation (RMS within tolerance)
 * - Audio duration accuracy (50 ms)
 * - Video dimension roundtrip (exact match)
 * - Video frame count (within 1 of expected)
 *
 * The `desktopTest` task is disabled at the Gradle level when GStreamer
 * is not installed, so these tests always run against real GStreamer.
 */
class ContentFidelityTest : GStreamerTestBase() {

    private val ctx = testContext()

    // -----------------------------------------------------------------------
    // Audio waveform preservation
    // -----------------------------------------------------------------------

    /**
     * Encode a 440 Hz sine, decode it back, and verify the RMS of the decoded
     * signal is within 5 dB of the original. Lossy codecs (AAC, Opus) will
     * attenuate somewhat but should not destroy the signal entirely.
     */
    @Test
    fun aac_waveformPreservation_rmsWithinTolerance() = runTest {
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

    @Test
    fun m4a_waveformPreservation_rmsWithinTolerance() = runTest {
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

    @Test
    fun opus_waveformPreservation_rmsWithinTolerance() = runTest {
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

    // -----------------------------------------------------------------------
    // Audio duration accuracy
    // -----------------------------------------------------------------------

    @Test
    fun aac_durationAccuracy_1s() = runTest {
        assertDurationWithin(GstAacCodec(), AudioFormat.Aac, inputMs = 1000, toleranceMs = 50)
    }

    @Test
    fun aac_durationAccuracy_100ms() = runTest {
        assertDurationWithin(GstAacCodec(), AudioFormat.Aac, inputMs = 100, toleranceMs = 80)
    }

    @Test
    fun aac_durationAccuracy_5s() = runTest {
        assertDurationWithin(GstAacCodec(), AudioFormat.Aac, inputMs = 5000, toleranceMs = 50)
    }

    @Test
    fun m4a_durationAccuracy_1s() = runTest {
        assertDurationWithin(GstM4aCodec(), AudioFormat.M4a, inputMs = 1000, toleranceMs = 50)
    }

    @Test
    fun opus_durationAccuracy_1s() = runTest {
        assertDurationWithin(GstOpusCodec(), AudioFormat.Opus, inputMs = 1000, toleranceMs = 50)
    }

    // -----------------------------------------------------------------------
    // Video dimension roundtrip
    // -----------------------------------------------------------------------

    @Test
    fun mp4_dimensionRoundtrip_320x240() = runTest {
        assertVideoDimensions(GstMp4Codec(), VideoFormat.Mp4, 320, 240)
    }

    @Test
    fun webm_dimensionRoundtrip_320x240() = runTest {
        assertVideoDimensions(GstWebmCodec(), VideoFormat.Webm, 320, 240)
    }

    @Test
    fun mkv_dimensionRoundtrip_320x240() = runTest {
        assertVideoDimensions(GstMkvCodec(), VideoFormat.Mkv, 320, 240)
    }

    @Test
    fun avi_dimensionRoundtrip_320x240() = runTest {
        assertVideoDimensions(GstAviCodec(), VideoFormat.Avi, 320, 240)
    }

    @Test
    fun mov_dimensionRoundtrip_320x240() = runTest {
        assertVideoDimensions(GstMovCodec(), VideoFormat.Mov, 320, 240)
    }

    // -----------------------------------------------------------------------
    // Video frame count
    // -----------------------------------------------------------------------

    @Test
    fun mp4_frameCount_10fps1s() = runTest {
        assertFrameCount(GstMp4Codec(), VideoFormat.Mp4, fps = 10.0, durationMs = 1000, expectedMin = 9, expectedMax = 11)
    }

    @Test
    fun webm_frameCount_10fps1s() = runTest {
        assertFrameCount(GstWebmCodec(), VideoFormat.Webm, fps = 10.0, durationMs = 1000, expectedMin = 9, expectedMax = 11)
    }

    @Test
    fun mkv_frameCount_10fps1s() = runTest {
        assertFrameCount(GstMkvCodec(), VideoFormat.Mkv, fps = 10.0, durationMs = 1000, expectedMin = 9, expectedMax = 11)
    }

    // =======================================================================
    // Helpers
    // =======================================================================

    /** Compute RMS amplitude in dB (relative to 1.0 full scale). */
    private fun rmsDb(samples: FloatArray): Double {
        if (samples.isEmpty()) return -100.0
        var sumSq = 0.0
        for (s in samples) sumSq += s * s
        val rms = sqrt(sumSq / samples.size)
        return if (rms < 1e-10) -100.0 else 20.0 * ln(rms) / ln(10.0)
    }

    /** Assert that encode->decode preserves duration within tolerance. */
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
            "Duration should be within +/-${toleranceMs}ms of ${inputMs}ms. " +
                "Got ${decoded.durationMs}ms (diff=${diff}ms)",
        )
    }

    /** Assert that encode->decode preserves video dimensions exactly. */
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

    /** Assert that encode->decode yields a frame count within expected range. */
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
}
