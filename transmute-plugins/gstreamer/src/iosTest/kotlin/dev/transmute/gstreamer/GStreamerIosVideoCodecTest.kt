package dev.transmute.gstreamer

import dev.transmute.gstreamer.GStreamerIosTestHelpers.requireGStreamer
import dev.transmute.gstreamer.GStreamerIosTestHelpers.testContext
import dev.transmute.model.core.Bytes
import dev.transmute.video.CanonicalVideoDecodeOptions
import dev.transmute.video.CanonicalVideoEncodeOptions
import dev.transmute.video.VideoFormat
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
class GStreamerIosVideoCodecTest {

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
    fun mp4_sniff_ftypIsom() {
        val header = byteArrayOf(
            0x00, 0x00, 0x00, 0x20,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'i'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
        )
        assertEquals(VideoFormat.Mp4, mp4.sniff(Bytes(header)))
    }

    @Test
    fun mp4_sniff_nonIsoBmff_returnsNull() {
        assertNull(mp4.sniff(Bytes(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00))))
    }

    @Test
    fun mp4_encode_producesNonEmptyOutput() = runTest {
        requireGStreamer {
            val video = GStreamerIosTestHelpers.syntheticVideo(
                width = 160, height = 120, frameRate = 10.0, durationMs = 500,
            )
            val encoded = mp4.encode(video, VideoFormat.Mp4, CanonicalVideoEncodeOptions(), ctx)
            assertTrue(encoded.isNotEmpty(), "Encoded MP4 output must not be empty")
        }
    }

    @Test
    fun mp4_encodeAndDecode_roundTrip() = runTest {
        requireGStreamer {
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
    fun mov_sniff_ftypQt() {
        val header = byteArrayOf(
            0x00, 0x00, 0x00, 0x20,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'q'.code.toByte(), 't'.code.toByte(), ' '.code.toByte(), ' '.code.toByte(),
        )
        assertEquals(VideoFormat.Mov, mov.sniff(Bytes(header)))
    }

    @Test
    fun mov_sniff_mp4Brand_returnsNull() {
        val header = byteArrayOf(
            0x00, 0x00, 0x00, 0x20,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'i'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
        )
        assertNull(mov.sniff(Bytes(header)))
    }

    @Test
    fun mov_encodeAndDecode_roundTrip() = runTest {
        requireGStreamer {
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
    fun webm_sniff_ebmlDocTypeWebm() {
        // EBML header with DocType "webm"
        val data = byteArrayOf(
            0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), // EBML ID
            0x93.toByte(),                              // size (VINT)
            0x42, 0x82.toByte(),                        // DocType element ID
            0x84.toByte(),                              // size = 4
            'w'.code.toByte(), 'e'.code.toByte(), 'b'.code.toByte(), 'm'.code.toByte(),
        )
        assertEquals(VideoFormat.Webm, webm.sniff(Bytes(data)))
    }

    @Test
    fun webm_encodeAndDecode_roundTrip() = runTest {
        requireGStreamer {
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
    fun avi_sniff_riffAvi() {
        val header = byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
            0x00, 0x00, 0x00, 0x00,
            'A'.code.toByte(), 'V'.code.toByte(), 'I'.code.toByte(), ' '.code.toByte(),
        )
        assertEquals(VideoFormat.Avi, avi.sniff(Bytes(header)))
    }

    @Test
    fun avi_encodeAndDecode_roundTrip() = runTest {
        requireGStreamer {
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
    fun mkv_sniff_ebmlDocTypeMatroska() {
        val data = byteArrayOf(
            0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(),
            0x93.toByte(),
            0x42, 0x82.toByte(),
            0x88.toByte(),
            'm'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'r'.code.toByte(),
            'o'.code.toByte(), 's'.code.toByte(), 'k'.code.toByte(), 'a'.code.toByte(),
        )
        assertEquals(VideoFormat.Mkv, mkv.sniff(Bytes(data)))
    }

    @Test
    fun mkv_encodeAndDecode_roundTrip() = runTest {
        requireGStreamer {
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
