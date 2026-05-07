package dev.transmute.gstreamer

import dev.transmute.gstreamer.GStreamerIosTestHelpers.requireGStreamerElements
import dev.transmute.gstreamer.GStreamerIosTestHelpers.requireGStreamerOptionalElements
import dev.transmute.gstreamer.GStreamerIosTestHelpers.testContext
import dev.transmute.video.CanonicalVideoDecodeOptions
import dev.transmute.video.CanonicalVideoEncodeOptions
import dev.transmute.video.VideoFormat
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end integration tests for GStreamer iOS video codecs.
 *
 * Tests exercise the full encode (VideoIR -> GStreamer cinterop -> bytes)
 * and decode (bytes -> GStreamer cinterop -> VideoIR) pipelines for each
 * supported video format: MP4, MOV, WebM, AVI, MKV.
 *
 * Soft-skipped when GStreamer.framework is not linked.
 *
 * Run: `./gradlew :transmute-gstreamer:iosSimulatorArm64Test`
 */
class GStreamerIosVideoCodecIntegrationTest {

    private val ctx = testContext()

    // -- MP4 ----------------------------------------------------------------

    private val mp4 = GstIosMp4Codec()

    @Test
    fun mp4_decodableFormats_containsMp4() {
        assertTrue(VideoFormat.Mp4 in mp4.decodableFormats)
    }

    @Test
    fun mp4_encodableFormats_containsMp4() {
        assertTrue(VideoFormat.Mp4 in mp4.encodableFormats)
    }

    @Test
    fun mp4_encode_producesNonEmptyOutput() = runTest {
        requireGStreamerOptionalElements(iosH264VideoEncoderElementOrNull(), "h264parse", "mp4mux") {
            val video = GStreamerIosTestHelpers.syntheticVideo(
                width = 160, height = 120, frameRate = 10.0, durationMs = 500,
            )
            val encoded = mp4.encode(video, VideoFormat.Mp4, CanonicalVideoEncodeOptions(), ctx)
            assertTrue(encoded.isNotEmpty(), "Encoded MP4 output must not be empty")
        }
    }

    @Test
    fun mp4_encodeAndDecode_roundTrip() = runTest {
        requireGStreamerOptionalElements(iosH264VideoEncoderElementOrNull(), "h264parse", "mp4mux") {
            val video = GStreamerIosTestHelpers.syntheticVideo(
                width = 160, height = 120, frameRate = 10.0, durationMs = 500,
            )
            val encoded = mp4.encode(video, VideoFormat.Mp4, CanonicalVideoEncodeOptions(), ctx)
            assertTrue(encoded.isNotEmpty())

            val decoded = mp4.decode(encoded, CanonicalVideoDecodeOptions(), ctx)
            assertNotNull(decoded.videoTrack, "Decoded VideoIR must have a video track")
            assertTrue(decoded.videoTrack.width > 0)
            assertTrue(decoded.videoTrack.height > 0)
            assertTrue(decoded.durationMs > 0)
        }
    }

    // -- MOV ----------------------------------------------------------------

    private val mov = GstIosMovCodec()

    @Test
    fun mov_decodableFormats_containsMov() {
        assertTrue(VideoFormat.Mov in mov.decodableFormats)
    }

    @Test
    fun mov_encodableFormats_containsMov() {
        assertTrue(VideoFormat.Mov in mov.encodableFormats)
    }

    @Test
    fun mov_encodeAndDecode_roundTrip() = runTest {
        requireGStreamerOptionalElements(iosH264VideoEncoderElementOrNull(), "h264parse", "qtmux") {
            val video = GStreamerIosTestHelpers.syntheticVideo(
                width = 160, height = 120, frameRate = 10.0, durationMs = 500,
            )
            val encoded = mov.encode(video, VideoFormat.Mov, CanonicalVideoEncodeOptions(), ctx)
            assertTrue(encoded.isNotEmpty())

            val decoded = mov.decode(encoded, CanonicalVideoDecodeOptions(), ctx)
            assertTrue(decoded.videoTrack.width > 0)
            assertTrue(decoded.durationMs > 0)
        }
    }

    // -- WebM ---------------------------------------------------------------

    private val webm = GstIosWebmCodec()

    @Test
    fun webm_decodableFormats_containsWebm() {
        assertTrue(VideoFormat.Webm in webm.decodableFormats)
    }

    @Test
    fun webm_encodableFormats_containsWebm() {
        assertTrue(VideoFormat.Webm in webm.encodableFormats)
    }

    @Test
    fun webm_encodeAndDecode_roundTrip() = runTest {
        requireGStreamerElements("vp8enc", "webmmux") {
            val video = GStreamerIosTestHelpers.syntheticVideo(
                width = 160, height = 120, frameRate = 10.0, durationMs = 500,
            )
            val encoded = webm.encode(video, VideoFormat.Webm, CanonicalVideoEncodeOptions(), ctx)
            assertTrue(encoded.isNotEmpty())

            val decoded = webm.decode(encoded, CanonicalVideoDecodeOptions(), ctx)
            assertTrue(decoded.videoTrack.width > 0)
            assertTrue(decoded.durationMs > 0)
        }
    }

    // -- AVI ----------------------------------------------------------------

    private val avi = GstIosAviCodec()

    @Test
    fun avi_decodableFormats_containsAvi() {
        assertTrue(VideoFormat.Avi in avi.decodableFormats)
    }

    @Test
    fun avi_encodableFormats_containsAvi() {
        assertTrue(VideoFormat.Avi in avi.encodableFormats)
    }

    @Test
    fun avi_encodeAndDecode_roundTrip() = runTest {
        requireGStreamerElements("avenc_mpeg4", "avimux") {
            val video = GStreamerIosTestHelpers.syntheticVideo(
                width = 160, height = 120, frameRate = 10.0, durationMs = 500,
            )
            val encoded = avi.encode(video, VideoFormat.Avi, CanonicalVideoEncodeOptions(), ctx)
            assertTrue(encoded.isNotEmpty())

            val decoded = avi.decode(encoded, CanonicalVideoDecodeOptions(), ctx)
            assertTrue(decoded.videoTrack.width > 0)
            assertTrue(decoded.durationMs > 0)
        }
    }

    // -- MKV ----------------------------------------------------------------

    private val mkv = GstIosMkvCodec()

    @Test
    fun mkv_decodableFormats_containsMkv() {
        assertTrue(VideoFormat.Mkv in mkv.decodableFormats)
    }

    @Test
    fun mkv_encodableFormats_containsMkv() {
        assertTrue(VideoFormat.Mkv in mkv.encodableFormats)
    }

    @Test
    fun mkv_encodeAndDecode_roundTrip() = runTest {
        requireGStreamerOptionalElements(iosH264VideoEncoderElementOrNull(), "h264parse", "matroskamux") {
            val video = GStreamerIosTestHelpers.syntheticVideo(
                width = 160, height = 120, frameRate = 10.0, durationMs = 500,
            )
            val encoded = mkv.encode(video, VideoFormat.Mkv, CanonicalVideoEncodeOptions(), ctx)
            assertTrue(encoded.isNotEmpty())

            val decoded = mkv.decode(encoded, CanonicalVideoDecodeOptions(), ctx)
            assertTrue(decoded.videoTrack.width > 0)
            assertTrue(decoded.durationMs > 0)
        }
    }

    // -- Cross-format checks -----------------------------------------------

    @Test
    fun allCodecsReportCorrectFormats() {
        assertTrue(VideoFormat.Mp4 in GstIosMp4Codec().decodableFormats)
        assertTrue(VideoFormat.Mp4 in GstIosMp4Codec().encodableFormats)
        assertTrue(VideoFormat.Mov in GstIosMovCodec().decodableFormats)
        assertTrue(VideoFormat.Mov in GstIosMovCodec().encodableFormats)
        assertTrue(VideoFormat.Webm in GstIosWebmCodec().decodableFormats)
        assertTrue(VideoFormat.Webm in GstIosWebmCodec().encodableFormats)
        assertTrue(VideoFormat.Avi in GstIosAviCodec().decodableFormats)
        assertTrue(VideoFormat.Avi in GstIosAviCodec().encodableFormats)
        assertTrue(VideoFormat.Mkv in GstIosMkvCodec().decodableFormats)
        assertTrue(VideoFormat.Mkv in GstIosMkvCodec().encodableFormats)
    }
}
