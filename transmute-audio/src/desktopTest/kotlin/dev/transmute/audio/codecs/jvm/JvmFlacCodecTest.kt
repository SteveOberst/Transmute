package dev.transmute.audio.codecs.jvm

import dev.transmute.audio.AudioEncodeOptions
import dev.transmute.audio.AudioTestHelpers
import dev.transmute.core.AudioFormat
import dev.transmute.core.PrintLogger
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.*
import dev.transmute.audio.CanonicalAudioDecodeOptions
import dev.transmute.audio.CanonicalAudioEncodeOptions

class JvmFlacCodecTest {

    private val log = PrintLogger
    private val codec = JvmFlacCodec()

    // -- Format declarations --

    @Test
    fun decodableFormatsContainsFlac() {
        assertTrue(AudioFormat.FLAC in codec.decodableFormats)
    }

    @Test
    fun encodableFormatsReflectsFfmpegAvailability() {
        if (FfmpegAudioEngine.available) {
            assertTrue(AudioFormat.FLAC in codec.encodableFormats)
        } else {
            assertTrue(codec.encodableFormats.isEmpty())
        }
    }

    // -- Sniff --

    @Test
    fun sniffDetectsFlacMagic() {
        // "fLaC"
        val data = byteArrayOf(0x66, 0x4C, 0x61, 0x43, 0x00, 0x00, 0x00)
        assertEquals(AudioFormat.FLAC, codec.sniff(data))
    }

    @Test
    fun sniffReturnsNullForMp3() {
        val data = byteArrayOf(0x49, 0x44, 0x33, 0x04, 0x00, 0x00)
        assertNull(codec.sniff(data))
    }

    @Test
    fun sniffReturnsNullForShortData() {
        assertNull(codec.sniff(byteArrayOf(0x66, 0x4C, 0x61)))
    }

    // -- Encode + decode round-trip (FFmpeg required for encode) --

    @Test
    fun encodeDecodeFlacRoundTrip() = runTest {
        if (!FfmpegAudioEngine.available) {
            log.warn("Skipping FLAC encode/decode test - FFmpeg not available")
            return@runTest
        }

        val original = AudioTestHelpers.sineWave(
            frequency = 440f,
            durationMs = 200,
            sampleRate = 44100,
            amplitude = 0.5f,
            channelCount = 1,
        )

        val encoded = codec.encode(original, AudioFormat.FLAC, CanonicalAudioEncodeOptions(), AudioTestHelpers.testContext())
        assertTrue(encoded.isNotEmpty(), "Encoded FLAC should not be empty")

        val sniffResult = codec.sniff(encoded)
        assertEquals(AudioFormat.FLAC, sniffResult, "Encoded data should be recognized as FLAC")

        val decoded = codec.decode(encoded, CanonicalAudioDecodeOptions(), AudioTestHelpers.testContext())
        assertEquals(original.sampleRate, decoded.sampleRate)
        assertEquals(original.channelCount, decoded.channelCount)

        // FLAC is lossless - samples should match closely (quantization diff only)
        assertEquals(original.samples.data.size, decoded.samples.data.size,
            "Sample count should match for lossless codec")
        for (i in original.samples.data.indices) {
            val diff = abs(original.samples.data[i] - decoded.samples.data[i])
            assertTrue(diff < 0.001f, "Lossless sample $i differs by $diff")
        }
    }

    @Test
    fun encodeThrowsWithoutFfmpeg() = runTest {
        if (FfmpegAudioEngine.available) {
            log.warn("Skipping - FFmpeg is available, cannot test failure path")
            return@runTest
        }

        val ir = AudioTestHelpers.sineWave(durationMs = 50)
        assertFailsWith<IllegalStateException> {
            codec.encode(ir, AudioFormat.FLAC, CanonicalAudioEncodeOptions(), AudioTestHelpers.testContext())
        }
    }
}
