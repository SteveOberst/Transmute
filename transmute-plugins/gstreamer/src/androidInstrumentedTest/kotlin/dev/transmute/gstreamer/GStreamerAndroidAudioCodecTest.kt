package dev.transmute.gstreamer

import dev.transmute.audio.AudioFormat
import dev.transmute.audio.CanonicalAudioDecodeOptions
import dev.transmute.audio.CanonicalAudioEncodeOptions
import dev.transmute.gstreamer.GStreamerAndroidTestHelpers.codecOp
import dev.transmute.gstreamer.GStreamerAndroidTestHelpers.testContext
import dev.transmute.model.core.Bytes
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
 * Android instrumented tests for GStreamer audio codecs.
 *
 * Tests exercise the full encode (AudioIR → GStreamer JNI → bytes)
 * and decode (bytes → GStreamer JNI → AudioIR) pipelines for each
 * supported audio format: AAC, M4A, Opus, FLAC (encode only),
 * OGG/Vorbis (encode only).
 *
 * Soft-skipped when `libgstreamer_bridge.so` is not bundled.
 *
 * Run: `./gradlew :transmute-gstreamer:connectedAndroidTest`
 */
@RunWith(AndroidJUnit4::class)
class GStreamerAndroidAudioCodecTest {

    @get:Rule val timeout: Timeout = Timeout.seconds(180)

    private val ctx = testContext()

    // -- AAC ----------------------------------------------------------------

    private val aac = GstAndroidAacCodec()

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
        val wav = Bytes(byteArrayOf(0x52, 0x49, 0x46, 0x46))
        assertNull(aac.sniff(wav))
    }

    @Test
    fun aac_sniff_shortData_returnsNull() {
        assertNull(aac.sniff(Bytes(byteArrayOf(0xFF.toByte()))))
    }

    @Test
    fun aac_encodeAndDecode_roundTrip() = runBlocking {
        if (!GStreamerAndroidTestHelpers.gstreamerAvailable) {
            println("SKIP: GStreamer not available – test skipped")
            return@runBlocking
        }
        val ir = GStreamerAndroidTestHelpers.sineWave(durationMs = 500, sampleRate = 44100)
        val encoded = codecOp("AAC encode") {
            aac.encode(ir, AudioFormat.Aac, CanonicalAudioEncodeOptions(), ctx)
        } ?: return@runBlocking
        assertTrue(encoded.isNotEmpty(), "Encoded AAC output must not be empty")

        val decoded = codecOp("AAC decode") {
            aac.decode(encoded, CanonicalAudioDecodeOptions(), ctx)
        } ?: return@runBlocking
        assertNotNull(decoded)
        assertTrue(decoded.sampleRate > 0)
        assertTrue(decoded.durationMs > 0)
    }

    // -- M4A ----------------------------------------------------------------

    private val m4a = GstAndroidM4aCodec()

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
            0x4D, 0x34, 0x41, 0x20,
        )
        assertEquals(AudioFormat.M4a, m4a.sniff(Bytes(header)))
    }

    @Test
    fun m4a_sniff_nonIsoBmff_returnsNull() {
        assertNull(m4a.sniff(Bytes(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00))))
    }

    @Test
    fun m4a_encodeAndDecode_roundTrip() = runBlocking {
        if (!GStreamerAndroidTestHelpers.gstreamerAvailable) {
            println("SKIP: GStreamer not available – test skipped")
            return@runBlocking
        }
        val ir = GStreamerAndroidTestHelpers.sineWave(durationMs = 500, sampleRate = 44100)
        val encoded = codecOp("M4A encode") {
            m4a.encode(ir, AudioFormat.M4a, CanonicalAudioEncodeOptions(), ctx)
        } ?: return@runBlocking
        assertTrue(encoded.isNotEmpty())

        val decoded = codecOp("M4A decode") {
            m4a.decode(encoded, CanonicalAudioDecodeOptions(), ctx)
        } ?: return@runBlocking
        assertNotNull(decoded)
        assertTrue(decoded.sampleRate > 0)
        assertTrue(decoded.durationMs > 0)
    }

    // -- Opus ---------------------------------------------------------------

    private val opus = GstAndroidOpusCodec()

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
    fun opus_encodeAndDecode_roundTrip() = runBlocking {
        if (!GStreamerAndroidTestHelpers.gstreamerAvailable) {
            println("SKIP: GStreamer not available – test skipped")
            return@runBlocking
        }
        val ir = GStreamerAndroidTestHelpers.sineWave(durationMs = 500, sampleRate = 48000)
        val encoded = codecOp("Opus encode") {
            opus.encode(ir, AudioFormat.Opus, CanonicalAudioEncodeOptions(), ctx)
        } ?: return@runBlocking
        assertTrue(encoded.isNotEmpty())

        val decoded = codecOp("Opus decode") {
            opus.decode(encoded, CanonicalAudioDecodeOptions(), ctx)
        } ?: return@runBlocking
        assertNotNull(decoded)
        assertTrue(decoded.sampleRate > 0)
        assertTrue(decoded.durationMs > 0)
    }

    // -- FLAC (encode only) -------------------------------------------------

    private val flacEncoder = GstAndroidFlacEncoder()

    @Test
    fun flac_encodableFormats_containsFlac() {
        assertTrue(AudioFormat.Flac in flacEncoder.supportedFormats)
    }

    @Test
    fun flac_encode_producesNonEmptyOutput() = runBlocking {
        if (!GStreamerAndroidTestHelpers.gstreamerAvailable) {
            println("SKIP: GStreamer not available – test skipped")
            return@runBlocking
        }
        val ir = GStreamerAndroidTestHelpers.sineWave(durationMs = 500, sampleRate = 44100)
        val encoded = codecOp("FLAC encode") {
            flacEncoder.encode(ir, AudioFormat.Flac, CanonicalAudioEncodeOptions(), ctx)
        } ?: return@runBlocking
        assertTrue(encoded.isNotEmpty(), "Encoded FLAC output must not be empty")

        val magic = encoded.data.sliceArray(0 until 4).decodeToString()
        assertEquals("fLaC", magic, "FLAC output must start with 'fLaC' magic")
    }

    // -- OGG/Vorbis (encode only) -------------------------------------------

    private val oggEncoder = GstAndroidOggVorbisEncoder()

    @Test
    fun ogg_encodableFormats_containsOgg() {
        assertTrue(AudioFormat.Ogg in oggEncoder.supportedFormats)
    }

    @Test
    fun ogg_encode_producesNonEmptyOutput() = runBlocking {
        if (!GStreamerAndroidTestHelpers.gstreamerAvailable) {
            println("SKIP: GStreamer not available – test skipped")
            return@runBlocking
        }
        val ir = GStreamerAndroidTestHelpers.sineWave(durationMs = 500, sampleRate = 44100)
        val encoded = codecOp("OGG encode") {
            oggEncoder.encode(ir, AudioFormat.Ogg, CanonicalAudioEncodeOptions(), ctx)
        } ?: return@runBlocking
        assertTrue(encoded.isNotEmpty(), "Encoded OGG output must not be empty")

        val magic = encoded.data.sliceArray(0 until 4).decodeToString()
        assertEquals("OggS", magic, "OGG output must start with 'OggS' magic")
    }

    // -- Cross-format checks -----------------------------------------------

    @Test
    fun allCodecsReportCorrectFormats() {
        assertTrue(AudioFormat.Aac in GstAndroidAacCodec().decodableFormats)
        assertTrue(AudioFormat.Aac in GstAndroidAacCodec().encodableFormats)
        assertTrue(AudioFormat.M4a in GstAndroidM4aCodec().decodableFormats)
        assertTrue(AudioFormat.M4a in GstAndroidM4aCodec().encodableFormats)
        assertTrue(AudioFormat.Opus in GstAndroidOpusCodec().decodableFormats)
        assertTrue(AudioFormat.Opus in GstAndroidOpusCodec().encodableFormats)
        assertTrue(AudioFormat.Flac in GstAndroidFlacEncoder().supportedFormats)
        assertTrue(AudioFormat.Ogg in GstAndroidOggVorbisEncoder().supportedFormats)
    }
}
