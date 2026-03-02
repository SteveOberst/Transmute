package dev.transmute.gstreamer

import dev.transmute.audio.AudioFormat
import dev.transmute.audio.CanonicalAudioDecodeOptions
import dev.transmute.audio.CanonicalAudioEncodeOptions
import dev.transmute.gstreamer.GStreamerIosTestHelpers.requireGStreamer
import dev.transmute.gstreamer.GStreamerIosTestHelpers.testContext
import dev.transmute.model.core.Bytes
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end integration tests for GStreamer iOS audio codecs.
 *
 * Tests exercise the full encode (AudioIR -> GStreamer cinterop -> bytes)
 * and decode (bytes -> GStreamer cinterop -> AudioIR) pipelines for each
 * supported audio format: AAC, M4A, Opus, FLAC (encode only),
 * OGG/Vorbis (encode only).
 *
 * Soft-skipped when GStreamer.framework is not linked.
 *
 * Run: `./gradlew :transmute-gstreamer:iosSimulatorArm64Test`
 */
class GStreamerIosAudioCodecTest {

    private val ctx = testContext()

    // -- AAC ----------------------------------------------------------------

    private val aac = GstIosAacCodec()

    @Test
    fun aac_decodableFormats_containsAac() {
        assertTrue(AudioFormat.Aac in aac.decodableFormats)
    }

    @Test
    fun aac_encodableFormats_containsAac() {
        assertTrue(AudioFormat.Aac in aac.encodableFormats)
    }

    @Test
    fun aac_sniff_adtsSyncWord() {
        val adts = Bytes(byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x50, 0x80.toByte()))
        assertEquals(AudioFormat.Aac, aac.sniff(adts))
    }

    @Test
    fun aac_sniff_nonAac_returnsNull() {
        val wav = Bytes(byteArrayOf(0x52, 0x49, 0x46, 0x46)) // "RIFF"
        assertNull(aac.sniff(wav))
    }

    @Test
    fun aac_sniff_shortData_returnsNull() {
        assertNull(aac.sniff(Bytes(byteArrayOf(0xFF.toByte()))))
    }

    @Test
    fun aac_encodeAndDecode_roundTrip() = runTest {
        requireGStreamer {
            val ir = GStreamerIosTestHelpers.sineWave(durationMs = 500, sampleRate = 44100)
            val encoded = aac.encode(ir, AudioFormat.Aac, CanonicalAudioEncodeOptions(), ctx)
            assertTrue(encoded.isNotEmpty(), "Encoded AAC output must not be empty")

            val decoded = aac.decode(encoded, CanonicalAudioDecodeOptions(), ctx)
            assertNotNull(decoded, "Decoded AudioIR must not be null")
            assertTrue(decoded.sampleRate > 0)
            assertTrue(decoded.durationMs > 0)
        }
    }

    // -- M4A ----------------------------------------------------------------

    private val m4a = GstIosM4aCodec()

    @Test
    fun m4a_decodableFormats_containsM4a() {
        assertTrue(AudioFormat.M4a in m4a.decodableFormats)
    }

    @Test
    fun m4a_encodableFormats_containsM4a() {
        assertTrue(AudioFormat.M4a in m4a.encodableFormats)
    }

    @Test
    fun m4a_sniff_ftypM4A() {
        val header = byteArrayOf(
            0x00, 0x00, 0x00, 0x20,
            0x66, 0x74, 0x79, 0x70,
            0x4D, 0x34, 0x41, 0x20, // "M4A "
        )
        assertEquals(AudioFormat.M4a, m4a.sniff(Bytes(header)))
    }

    @Test
    fun m4a_sniff_nonIsoBmff_returnsNull() {
        assertNull(m4a.sniff(Bytes(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00))))
    }

    @Test
    fun m4a_encodeAndDecode_roundTrip() = runTest {
        requireGStreamer {
            val ir = GStreamerIosTestHelpers.sineWave(durationMs = 500, sampleRate = 44100)
            val encoded = m4a.encode(ir, AudioFormat.M4a, CanonicalAudioEncodeOptions(), ctx)
            assertTrue(encoded.isNotEmpty(), "Encoded M4A output must not be empty")

            val decoded = m4a.decode(encoded, CanonicalAudioDecodeOptions(), ctx)
            assertNotNull(decoded)
            assertTrue(decoded.sampleRate > 0)
            assertTrue(decoded.durationMs > 0)
        }
    }

    // -- Opus ---------------------------------------------------------------

    private val opus = GstIosOpusCodec()

    @Test
    fun opus_decodableFormats_containsOpus() {
        assertTrue(AudioFormat.Opus in opus.decodableFormats)
    }

    @Test
    fun opus_encodableFormats_containsOpus() {
        assertTrue(AudioFormat.Opus in opus.encodableFormats)
    }

    @Test
    fun opus_sniff_oggOpusHead() {
        val data = ByteArray(36)
        data[0] = 'O'.code.toByte()
        data[1] = 'g'.code.toByte()
        data[2] = 'g'.code.toByte()
        data[3] = 'S'.code.toByte()
        "OpusHead".forEachIndexed { i, c -> data[28 + i] = c.code.toByte() }
        assertEquals(AudioFormat.Opus, opus.sniff(Bytes(data)))
    }

    @Test
    fun opus_sniff_nonOgg_returnsNull() {
        assertNull(opus.sniff(Bytes(byteArrayOf(0x00, 0x01, 0x02, 0x03))))
    }

    @Test
    fun opus_encodeAndDecode_roundTrip() = runTest {
        requireGStreamer {
            val ir = GStreamerIosTestHelpers.sineWave(durationMs = 500, sampleRate = 48000)
            val encoded = opus.encode(ir, AudioFormat.Opus, CanonicalAudioEncodeOptions(), ctx)
            assertTrue(encoded.isNotEmpty(), "Encoded Opus output must not be empty")

            val decoded = opus.decode(encoded, CanonicalAudioDecodeOptions(), ctx)
            assertNotNull(decoded)
            assertTrue(decoded.sampleRate > 0)
            assertTrue(decoded.durationMs > 0)
        }
    }

    // -- FLAC (encode only) -------------------------------------------------

    private val flacEncoder = GstIosFlacEncoder()

    @Test
    fun flac_encodableFormats_containsFlac() {
        assertTrue(AudioFormat.Flac in flacEncoder.supportedFormats)
    }

    @Test
    fun flac_encode_producesNonEmptyOutput() = runTest {
        requireGStreamer {
            val ir = GStreamerIosTestHelpers.sineWave(durationMs = 500, sampleRate = 44100)
            val encoded = flacEncoder.encode(ir, AudioFormat.Flac, CanonicalAudioEncodeOptions(), ctx)
            assertTrue(encoded.isNotEmpty(), "Encoded FLAC output must not be empty")

            val magic = encoded.data.sliceArray(0 until 4).decodeToString()
            assertEquals("fLaC", magic, "FLAC output must start with 'fLaC' magic")
        }
    }

    // -- OGG/Vorbis (encode only) -------------------------------------------

    private val oggEncoder = GstIosOggVorbisEncoder()

    @Test
    fun ogg_encodableFormats_containsOgg() {
        assertTrue(AudioFormat.Ogg in oggEncoder.supportedFormats)
    }

    @Test
    fun ogg_encode_producesNonEmptyOutput() = runTest {
        requireGStreamer {
            val ir = GStreamerIosTestHelpers.sineWave(durationMs = 500, sampleRate = 44100)
            val encoded = oggEncoder.encode(ir, AudioFormat.Ogg, CanonicalAudioEncodeOptions(), ctx)
            assertTrue(encoded.isNotEmpty(), "Encoded OGG output must not be empty")

            val magic = encoded.data.sliceArray(0 until 4).decodeToString()
            assertEquals("OggS", magic, "OGG output must start with 'OggS' magic")
        }
    }

    // -- Cross-format check -------------------------------------------------

    @Test
    fun allCodecsReportCorrectFormats() {
        assertTrue(AudioFormat.Aac in GstIosAacCodec().decodableFormats)
        assertTrue(AudioFormat.Aac in GstIosAacCodec().encodableFormats)
        assertTrue(AudioFormat.M4a in GstIosM4aCodec().decodableFormats)
        assertTrue(AudioFormat.M4a in GstIosM4aCodec().encodableFormats)
        assertTrue(AudioFormat.Opus in GstIosOpusCodec().decodableFormats)
        assertTrue(AudioFormat.Opus in GstIosOpusCodec().encodableFormats)
        assertTrue(AudioFormat.Flac in GstIosFlacEncoder().supportedFormats)
        assertTrue(AudioFormat.Ogg in GstIosOggVorbisEncoder().supportedFormats)
    }
}
