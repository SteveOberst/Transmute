package dev.transmute.audio.codecs.jvm

import dev.transmute.audio.AudioFormat
import dev.transmute.audio.AudioTestHelpers
import dev.transmute.audio.CanonicalAudioEncodeOptions
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class JvmFlacCodecTest {

    private val codec = JvmFlacCodec()

    // -- Format declarations --

    @Test
    fun decodableFormatsContainsFlac() {
        assertTrue(AudioFormat.Flac in codec.decodableFormats)
    }

    @Test
    fun encodableFormatsIsEmpty() {
        // Native FLAC encoding requires the transmute-gstreamer module.
        assertTrue(codec.encodableFormats.isEmpty())
    }

    // -- Encode (requires transmute-gstreamer) --

    @Test
    fun encodeThrowsWithoutGstreamer() = runTest {
        val ir = AudioTestHelpers.sineWave(durationMs = 50)
        assertFailsWith<IllegalStateException> {
            codec.encode(ir, AudioFormat.Flac, CanonicalAudioEncodeOptions(), AudioTestHelpers.testContext())
        }
    }
}
