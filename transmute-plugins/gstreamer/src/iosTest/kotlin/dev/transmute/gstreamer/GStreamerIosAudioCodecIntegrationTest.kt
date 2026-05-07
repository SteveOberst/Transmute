package dev.transmute.gstreamer

import dev.transmute.audio.AudioFormat
import dev.transmute.audio.CanonicalAudioDecodeOptions
import dev.transmute.audio.CanonicalAudioEncodeOptions
import dev.transmute.gstreamer.GStreamerIosTestHelpers.requireGStreamerElement
import dev.transmute.gstreamer.GStreamerIosTestHelpers.requireGStreamerElements
import dev.transmute.gstreamer.GStreamerIosTestHelpers.requireGStreamerOptionalElements
import dev.transmute.gstreamer.GStreamerIosTestHelpers.testContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
class GStreamerIosAudioCodecIntegrationTest {

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
    fun aac_encodeAndDecode_roundTrip() = runTest {
        requireGStreamerOptionalElements(iosAacEncoderElementOrNull(), "aacparse") {
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
    fun m4a_encodeAndDecode_roundTrip() = runTest {
        requireGStreamerOptionalElements(iosAacEncoderElementOrNull(), "mp4mux") {
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
    fun opus_encodeAndDecode_roundTrip() = runTest {
        requireGStreamerElements("opusenc", "oggmux") {
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
        requireGStreamerElement("flacenc") {
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
        requireGStreamerElements("vorbisenc", "oggmux") {
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
