package dev.transmute.gstreamer

import dev.transmute.audio.AudioFormat
import dev.transmute.audio.CanonicalAudioDecodeOptions
import dev.transmute.audio.CanonicalAudioEncodeOptions
import dev.transmute.gstreamer.GStreamerTestHelpers.testContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end integration tests for GStreamer audio codecs.
 *
 * Tests exercise the full encode (AudioIR -> GStreamer subprocess -> bytes)
 * and decode (bytes -> GStreamer subprocess -> AudioIR) pipelines for each
 * supported audio format: AAC, M4A, Opus, FLAC (encode only), OGG/Vorbis
 * (encode only).
 *
 * These tests are **soft-skipped** when GStreamer is not installed locally.
 * On CI the integration runner installs GStreamer so every test will execute.
 */
class GStreamerAudioCodecTest {

    private val ctx = testContext()

    // -- AAC ----------------------------------------------------------------

    private val aac = GstAacCodec()

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
        val ir = GStreamerTestHelpers.sineWave(durationMs = 500, sampleRate = 44100)
        val encoded = aac.encode(ir, AudioFormat.Aac, CanonicalAudioEncodeOptions(), ctx)
        assertTrue(encoded.isNotEmpty(), "Encoded AAC output must not be empty")

        val decoded = aac.decode(encoded, CanonicalAudioDecodeOptions(), ctx)
        assertNotNull(decoded, "Decoded AudioIR must not be null")
        assertTrue(decoded.sampleRate > 0, "Sample rate must be positive")
        assertTrue(decoded.durationMs > 0, "Duration must be positive")
    }

    // -- M4A ----------------------------------------------------------------

    private val m4a = GstM4aCodec()

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
        val ir = GStreamerTestHelpers.sineWave(durationMs = 500, sampleRate = 44100)
        val encoded = m4a.encode(ir, AudioFormat.M4a, CanonicalAudioEncodeOptions(), ctx)
        assertTrue(encoded.isNotEmpty(), "Encoded M4A output must not be empty")

        val decoded = m4a.decode(encoded, CanonicalAudioDecodeOptions(), ctx)
        assertNotNull(decoded, "Decoded AudioIR must not be null")
        assertTrue(decoded.sampleRate > 0, "Sample rate must be positive")
        assertTrue(decoded.durationMs > 0, "Duration must be positive")
    }

    // -- Opus ---------------------------------------------------------------

    private val opus = GstOpusCodec()

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
        val ir = GStreamerTestHelpers.sineWave(durationMs = 500, sampleRate = 48000)
        val encoded = opus.encode(ir, AudioFormat.Opus, CanonicalAudioEncodeOptions(), ctx)
        assertTrue(encoded.isNotEmpty(), "Encoded Opus output must not be empty")

        val decoded = opus.decode(encoded, CanonicalAudioDecodeOptions(), ctx)
        assertNotNull(decoded, "Decoded AudioIR must not be null")
        assertTrue(decoded.sampleRate > 0, "Sample rate must be positive")
        assertTrue(decoded.durationMs > 0, "Duration must be positive")
    }

    // -- FLAC (encode only) -------------------------------------------------

    private val flacEncoder = GstFlacEncoder()

    @Test
    fun flac_encodableFormats_containsFlac() {
        assertTrue(AudioFormat.Flac in flacEncoder.supportedFormats)
    }

    @Test
    fun flac_encode_producesNonEmptyOutput() = runTest {
        val ir = GStreamerTestHelpers.sineWave(durationMs = 500, sampleRate = 44100)
        val encoded = flacEncoder.encode(ir, AudioFormat.Flac, CanonicalAudioEncodeOptions(), ctx)
        assertTrue(encoded.isNotEmpty(), "Encoded FLAC output must not be empty")

        // Verify FLAC magic: "fLaC"
        val magic = encoded.data.sliceArray(0 until 4).decodeToString()
        assertEquals("fLaC", magic, "FLAC output must start with 'fLaC' magic")
    }

    // -- OGG/Vorbis (encode only) -------------------------------------------

    private val oggEncoder = GstOggVorbisEncoder()

    @Test
    fun ogg_encodableFormats_containsOgg() {
        assertTrue(AudioFormat.Ogg in oggEncoder.supportedFormats)
    }

    @Test
    fun ogg_encode_producesNonEmptyOutput() = runTest {
        val ir = GStreamerTestHelpers.sineWave(durationMs = 500, sampleRate = 44100)
        val encoded = oggEncoder.encode(ir, AudioFormat.Ogg, CanonicalAudioEncodeOptions(), ctx)
        assertTrue(encoded.isNotEmpty(), "Encoded OGG output must not be empty")

        // Verify OGG magic: "OggS"
        val magic = encoded.data.sliceArray(0 until 4).decodeToString()
        assertEquals("OggS", magic, "OGG output must start with 'OggS' magic")
    }
}