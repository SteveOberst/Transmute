package dev.transmute.gstreamer

import dev.transmute.gstreamer.GStreamerTestHelpers.requireGStreamer
import dev.transmute.gstreamer.GStreamerTestHelpers.testContext
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
 * End-to-end integration tests for GStreamer video codecs.
 *
 * Tests exercise the full encode (VideoIR -> GStreamer subprocess -> bytes)
 * and decode (bytes -> GStreamer subprocess -> VideoIR) pipelines for each
 * supported video format: MP4, MOV, WebM, AVI, MKV.
 *
 * Soft-skipped when GStreamer is not installed locally.
 */
class GStreamerVideoCodecTest {

    private val ctx = testContext()

    // -- MP4 ----------------------------------------------------------------

    private val mp4 = GstMp4Codec()

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
            val video = GStreamerTestHelpers.syntheticVideo(
                width = 160, height = 120, frameRate = 10.0, durationMs = 500,
            )
            val encoded = mp4.encode(video, VideoFormat.Mp4, CanonicalVideoEncodeOptions(), ctx)
            assertTrue(encoded.isNotEmpty(), "Encoded MP4 output must not be empty")
        }
    }

    @Test
    fun mp4_encodeAndDecode_roundTrip() = runTest {
        requireGStreamer {
            val video = GStreamerTestHelpers.syntheticVideo(
                width = 160, height = 120, frameRate = 10.0, durationMs = 500,
            )
            val encoded = mp4.encode(video, VideoFormat.Mp4, CanonicalVideoEncodeOptions(), ctx)
            assertTrue(encoded.isNotEmpty())

            val decoded = mp4.decode(encoded, CanonicalVideoDecodeOptions(), ctx)
            assertNotNull(decoded.videoTrack, "Decoded VideoIR must have a video track")
            assertTrue(decoded.videoTrack!!.width > 0, "Video width must be positive")
            assertTrue(decoded.videoTrack!!.height > 0, "Video height must be positive")
            assertTrue(decoded.durationMs > 0, "Duration must be positive")
        }
    }

    // -- MOV ----------------------------------------------------------------

    private val mov = GstMovCodec()

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
        // Should not match MP4 brand "isom"
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
            val video = GStreamerTestHelpers.syntheticVideo(
                width = 160, height = 120, frameRate = 10.0, durationMs = 500,
            )
            val encoded = mov.encode(video, VideoFormat.Mov, CanonicalVideoEncodeOptions(), ctx)
            assertTrue(encoded.isNotEmpty(), "Encoded MOV output must not be empty")

            val decoded = mov.decode(encoded, CanonicalVideoDecodeOptions(), ctx)
            assertNotNull(decoded.videoTrack, "Decoded VideoIR must have a video track")
            assertTrue(decoded.durationMs > 0, "Duration must be positive")
        }
    }

    // -- WebM ---------------------------------------------------------------

    private val webm = GstWebmCodec()

    @Test
    fun webm_decodableFormats_containsWebm() {
        assertTrue(VideoFormat.Webm in webm.decodableFormats)
    }

    @Test
    fun webm_encodableFormats_containsWebm() {
        assertTrue(VideoFormat.Webm in webm.encodableFormats)
    }

    @Test
    fun webm_sniff_ebmlHeader() {
        // EBML header: 0x1A45DFA3
        val header = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())
        // Short data, no matroska marker -> defaults to WebM
        assertEquals(VideoFormat.Webm, webm.sniff(Bytes(header)))
    }

    @Test
    fun webm_sniff_nonEbml_returnsNull() {
        assertNull(webm.sniff(Bytes(byteArrayOf(0x00, 0x00, 0x00, 0x00))))
    }

    @Test
    fun webm_encodeAndDecode_roundTrip() = runTest {
        requireGStreamer {
            val video = GStreamerTestHelpers.syntheticVideo(
                width = 160, height = 120, frameRate = 10.0, durationMs = 500,
            )
            val encoded = webm.encode(video, VideoFormat.Webm, CanonicalVideoEncodeOptions(), ctx)
            assertTrue(encoded.isNotEmpty(), "Encoded WebM output must not be empty")

            val decoded = webm.decode(encoded, CanonicalVideoDecodeOptions(), ctx)
            assertNotNull(decoded.videoTrack, "Decoded VideoIR must have a video track")
            assertTrue(decoded.durationMs > 0, "Duration must be positive")
        }
    }

    // -- AVI ----------------------------------------------------------------

    private val avi = GstAviCodec()

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
    fun avi_sniff_riffWav_returnsNull() {
        val header = byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
            0x00, 0x00, 0x00, 0x00,
            'W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte(),
        )
        assertNull(avi.sniff(Bytes(header)))
    }

    @Test
    fun avi_encodeAndDecode_roundTrip() = runTest {
        requireGStreamer {
            val video = GStreamerTestHelpers.syntheticVideo(
                width = 160, height = 120, frameRate = 10.0, durationMs = 500,
            )
            val encoded = avi.encode(video, VideoFormat.Avi, CanonicalVideoEncodeOptions(), ctx)
            assertTrue(encoded.isNotEmpty(), "Encoded AVI output must not be empty")

            val decoded = avi.decode(encoded, CanonicalVideoDecodeOptions(), ctx)
            assertNotNull(decoded.videoTrack, "Decoded VideoIR must have a video track")
            assertTrue(decoded.durationMs > 0, "Duration must be positive")
        }
    }

    // -- MKV ----------------------------------------------------------------

    private val mkv = GstMkvCodec()

    @Test
    fun mkv_decodableFormats_containsMkv() {
        assertTrue(VideoFormat.Mkv in mkv.decodableFormats)
    }

    @Test
    fun mkv_encodableFormats_containsMkv() {
        assertTrue(VideoFormat.Mkv in mkv.encodableFormats)
    }

    @Test
    fun mkv_sniff_matroskaDocType() {
        // EBML header followed by enough data containing "matroska" doc type.
        // Build a minimal buffer with EBML magic + "matroska" string.
        val ebml = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())
        val padding = ByteArray(24) // Fill to 28 bytes
        val docType = "matroska".encodeToByteArray()
        val data = ebml + padding + docType + ByteArray(32)
        assertEquals(VideoFormat.Mkv, mkv.sniff(Bytes(data)))
    }

    @Test
    fun mkv_sniff_shortEbml_returnsNull() {
        // EBML header but < 40 bytes, no matroska marker -> null for MKV
        val header = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())
        assertNull(mkv.sniff(Bytes(header)))
    }

    @Test
    fun mkv_encodeAndDecode_roundTrip() = runTest {
        requireGStreamer {
            val video = GStreamerTestHelpers.syntheticVideo(
                width = 160, height = 120, frameRate = 10.0, durationMs = 500,
            )
            val encoded = mkv.encode(video, VideoFormat.Mkv, CanonicalVideoEncodeOptions(), ctx)
            assertTrue(encoded.isNotEmpty(), "Encoded MKV output must not be empty")

            val decoded = mkv.decode(encoded, CanonicalVideoDecodeOptions(), ctx)
            assertNotNull(decoded.videoTrack, "Decoded VideoIR must have a video track")
            assertTrue(decoded.durationMs > 0, "Duration must be positive")
        }
    }

    // -- Encode with audio --------------------------------------------------

    @Test
    fun mp4_encodeWithAudio_roundTrip() = runTest {
        requireGStreamer {
            val video = GStreamerTestHelpers.syntheticVideo(
                width = 160, height = 120, frameRate = 10.0, durationMs = 500,
                includeAudio = true,
            )
            val encoded = mp4.encode(video, VideoFormat.Mp4, CanonicalVideoEncodeOptions(), ctx)
            assertTrue(encoded.isNotEmpty(), "Encoded MP4 (with audio) must not be empty")

            val decoded = mp4.decode(encoded, CanonicalVideoDecodeOptions(), ctx)
            assertNotNull(decoded.videoTrack, "Must have video track")
            // Audio may or may not survive the roundtrip depending on GStreamer
            // pipeline; just verify the decode doesn't crash.
            assertTrue(decoded.durationMs > 0, "Duration must be positive")
        }
    }
}
