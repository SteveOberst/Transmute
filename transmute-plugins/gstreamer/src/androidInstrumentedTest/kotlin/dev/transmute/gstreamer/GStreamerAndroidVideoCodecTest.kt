package dev.transmute.gstreamer

import dev.transmute.gstreamer.GStreamerAndroidTestHelpers.codecOp
import dev.transmute.gstreamer.GStreamerAndroidTestHelpers.testContext
import dev.transmute.model.core.Bytes
import dev.transmute.video.CanonicalVideoDecodeOptions
import dev.transmute.video.CanonicalVideoEncodeOptions
import dev.transmute.video.VideoFormat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Android instrumented tests for GStreamer video codecs.
 *
 * Tests exercise the full encode (VideoIR -> GStreamer JNI -> bytes)
 * and decode (bytes -> GStreamer JNI -> VideoIR) pipelines for each
 * supported video format: MP4, MOV, WebM, AVI, MKV.
 *
 * Soft-skipped when `libgstreamer_bridge.so` is not bundled.
 *
 * Run: `./gradlew :transmute-gstreamer:connectedAndroidTest`
 */
@RunWith(AndroidJUnit4::class)
class GStreamerAndroidVideoCodecTest {

    @get:Rule val timeout: Timeout = Timeout.seconds(180)

    private val ctx = testContext()

    // -- MP4 ----------------------------------------------------------------

    private val mp4 = GstAndroidMp4Codec()

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
    fun mp4_encodeAndDecode_roundTrip() = runBlocking {
        if (!GStreamerAndroidTestHelpers.gstreamerAvailable) {
            println("SKIP: GStreamer not available – test skipped")
            return@runBlocking
        }
        val video = GStreamerAndroidTestHelpers.syntheticVideo(
            width = 160, height = 120, frameRate = 10.0, durationMs = 500,
        )
        val encoded = codecOp("MP4 encode") {
            mp4.encode(video, VideoFormat.Mp4, CanonicalVideoEncodeOptions(), ctx)
        } ?: return@runBlocking
        assertTrue(encoded.isNotEmpty(), "Encoded MP4 output must not be empty")

        val decoded = codecOp("MP4 decode") {
            mp4.decode(encoded, CanonicalVideoDecodeOptions(), ctx)
        } ?: return@runBlocking
        assertNotNull(decoded.videoTrack)
        assertTrue(decoded.videoTrack.width > 0)
        assertTrue(decoded.videoTrack.height > 0)
        assertTrue(decoded.durationMs > 0)
    }

    // -- MOV ----------------------------------------------------------------

    private val mov = GstAndroidMovCodec()

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
    fun mov_encodeAndDecode_roundTrip() = runBlocking {
        if (!GStreamerAndroidTestHelpers.gstreamerAvailable) {
            println("SKIP: GStreamer not available – test skipped")
            return@runBlocking
        }
        val video = GStreamerAndroidTestHelpers.syntheticVideo(
            width = 160, height = 120, frameRate = 10.0, durationMs = 500,
        )
        val encoded = codecOp("MOV encode") {
            mov.encode(video, VideoFormat.Mov, CanonicalVideoEncodeOptions(), ctx)
        } ?: return@runBlocking
        assertTrue(encoded.isNotEmpty())

        val decoded = codecOp("MOV decode") {
            mov.decode(encoded, CanonicalVideoDecodeOptions(), ctx)
        } ?: return@runBlocking
        assertTrue(decoded.videoTrack.width > 0)
        assertTrue(decoded.durationMs > 0)
    }

    // -- WebM ---------------------------------------------------------------

    private val webm = GstAndroidWebmCodec()

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
        val data = byteArrayOf(
            0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(),
            0x93.toByte(),
            0x42, 0x82.toByte(),
            0x84.toByte(),
            'w'.code.toByte(), 'e'.code.toByte(), 'b'.code.toByte(), 'm'.code.toByte(),
        )
        assertEquals(VideoFormat.Webm, webm.sniff(Bytes(data)))
    }

    @Test
    fun webm_encodeAndDecode_roundTrip() = runBlocking {
        if (!GStreamerAndroidTestHelpers.gstreamerAvailable) {
            println("SKIP: GStreamer not available – test skipped")
            return@runBlocking
        }
        val video = GStreamerAndroidTestHelpers.syntheticVideo(
            width = 160, height = 120, frameRate = 10.0, durationMs = 500,
        )
        val encoded = codecOp("WebM encode") {
            webm.encode(video, VideoFormat.Webm, CanonicalVideoEncodeOptions(), ctx)
        } ?: return@runBlocking
        assertTrue(encoded.isNotEmpty())

        val decoded = codecOp("WebM decode") {
            webm.decode(encoded, CanonicalVideoDecodeOptions(), ctx)
        } ?: return@runBlocking
        assertTrue(decoded.videoTrack.width > 0)
        assertTrue(decoded.durationMs > 0)
    }

    // -- AVI ----------------------------------------------------------------

    private val avi = GstAndroidAviCodec()

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
    fun avi_encodeAndDecode_roundTrip() = runBlocking {
        if (!GStreamerAndroidTestHelpers.gstreamerAvailable) {
            println("SKIP: GStreamer not available – test skipped")
            return@runBlocking
        }
        val video = GStreamerAndroidTestHelpers.syntheticVideo(
            width = 160, height = 120, frameRate = 10.0, durationMs = 500,
        )
        val encoded = codecOp("AVI encode") {
            avi.encode(video, VideoFormat.Avi, CanonicalVideoEncodeOptions(), ctx)
        } ?: return@runBlocking
        assertTrue(encoded.isNotEmpty())

        val decoded = codecOp("AVI decode") {
            avi.decode(encoded, CanonicalVideoDecodeOptions(), ctx)
        } ?: return@runBlocking
        assertTrue(decoded.videoTrack.width > 0)
        assertTrue(decoded.durationMs > 0)
    }

    // -- MKV ----------------------------------------------------------------

    private val mkv = GstAndroidMkvCodec()

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
    fun mkv_encodeAndDecode_roundTrip() = runBlocking {
        if (!GStreamerAndroidTestHelpers.gstreamerAvailable) {
            println("SKIP: GStreamer not available – test skipped")
            return@runBlocking
        }
        val video = GStreamerAndroidTestHelpers.syntheticVideo(
            width = 160, height = 120, frameRate = 10.0, durationMs = 500,
        )
        val encoded = codecOp("MKV encode") {
            mkv.encode(video, VideoFormat.Mkv, CanonicalVideoEncodeOptions(), ctx)
        } ?: return@runBlocking
        assertTrue(encoded.isNotEmpty())

        val decoded = codecOp("MKV decode") {
            mkv.decode(encoded, CanonicalVideoDecodeOptions(), ctx)
        } ?: return@runBlocking
        assertTrue(decoded.videoTrack.width > 0)
        assertTrue(decoded.durationMs > 0)
    }

    // -- Cross-format checks -----------------------------------------------

    @Test
    fun allCodecsReportCorrectFormats() {
        assertTrue(VideoFormat.Mp4 in GstAndroidMp4Codec().decodableFormats)
        assertTrue(VideoFormat.Mp4 in GstAndroidMp4Codec().encodableFormats)
        assertTrue(VideoFormat.Mov in GstAndroidMovCodec().decodableFormats)
        assertTrue(VideoFormat.Mov in GstAndroidMovCodec().encodableFormats)
        assertTrue(VideoFormat.Webm in GstAndroidWebmCodec().decodableFormats)
        assertTrue(VideoFormat.Webm in GstAndroidWebmCodec().encodableFormats)
        assertTrue(VideoFormat.Avi in GstAndroidAviCodec().decodableFormats)
        assertTrue(VideoFormat.Avi in GstAndroidAviCodec().encodableFormats)
        assertTrue(VideoFormat.Mkv in GstAndroidMkvCodec().decodableFormats)
        assertTrue(VideoFormat.Mkv in GstAndroidMkvCodec().encodableFormats)
    }
}
