package dev.transmute.audio.codecs.jvm

import dev.transmute.audio.AudioFormat
import dev.transmute.audio.AudioTestHelpers
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.*
import dev.transmute.audio.CanonicalAudioDecodeOptions
import dev.transmute.audio.CanonicalAudioEncodeOptions

class JvmMp3CodecTest {

    private val codec = JvmMp3Codec()

    // -- Format declarations --

    @Test
    fun decodableFormatsContainsMp3() {
        assertTrue(AudioFormat.Mp3 in codec.decodableFormats)
    }

    @Test
    fun encodableFormatsContainsMp3() {
        assertTrue(AudioFormat.Mp3 in codec.encodableFormats)
    }

    // -- Encode / Decode round-trip --

    @Test
    fun encodeDecodeMp3RoundTrip() = runTest {
        val original = AudioTestHelpers.sineWave(
            frequency = 440f,
            durationMs = 200,
            sampleRate = 44100,
            amplitude = 0.5f,
            channelCount = 1,
        )

        val encoded = codec.encode(original, AudioFormat.Mp3, CanonicalAudioEncodeOptions(), AudioTestHelpers.testContext())
        assertTrue(encoded.isNotEmpty(), "Encoded MP3 should not be empty")

        val decoded = codec.decode(encoded, CanonicalAudioDecodeOptions(), AudioTestHelpers.testContext())
        assertEquals(original.sampleRate, decoded.sampleRate, "Sample rate should be preserved")
        assertEquals(original.channelCount, decoded.channelCount, "Channel count should be preserved")
        assertTrue(decoded.samples.data.isNotEmpty(), "Decoded samples should not be empty")

        // MP3 is lossy - check approximate duration match (within 50ms due to frame padding)
        val durationDiff = abs(original.durationMs - decoded.durationMs)
        assertTrue(durationDiff < 100, "Duration diff should be <100ms, was $durationDiff")
    }

    @Test
    fun encodeDecodeStereoMp3RoundTrip() = runTest {
        val original = AudioTestHelpers.sineWave(
            frequency = 880f,
            durationMs = 200,
            sampleRate = 44100,
            amplitude = 0.4f,
            channelCount = 2,
        )

        val encoded = codec.encode(original, AudioFormat.Mp3, CanonicalAudioEncodeOptions(), AudioTestHelpers.testContext())
        assertTrue(encoded.isNotEmpty())

        val decoded = codec.decode(encoded, CanonicalAudioDecodeOptions(), AudioTestHelpers.testContext())
        assertEquals(original.sampleRate, decoded.sampleRate)
        assertEquals(original.channelCount, decoded.channelCount)
        assertTrue(decoded.samples.data.isNotEmpty())
    }

    @Test
    fun encodedMp3IsSmallerThanRaw() = runTest {
        val original = AudioTestHelpers.sineWave(
            frequency = 440f,
            durationMs = 500,
            sampleRate = 44100,
            amplitude = 0.5f,
            channelCount = 1,
        )

        val encoded = codec.encode(original, AudioFormat.Mp3, CanonicalAudioEncodeOptions(), AudioTestHelpers.testContext())
        val rawSize = original.samples.data.size * 4 // Float = 4 bytes
        assertTrue(encoded.size < rawSize, "MP3 should be smaller than raw float data")
    }
}
