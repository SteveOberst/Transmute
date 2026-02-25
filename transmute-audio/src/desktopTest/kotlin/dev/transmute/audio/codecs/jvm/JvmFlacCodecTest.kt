package dev.transmute.audio.codecs.jvm

import dev.transmute.audio.AudioFormat
import dev.transmute.audio.AudioTestHelpers
import dev.transmute.audio.CanonicalAudioEncodeOptions
import dev.transmute.model.core.asBytes
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

    // -- Sniff --

    @Test
    fun sniffDetectsFlacMagic() {
        // "fLaC"
        val data = byteArrayOf(0x66, 0x4C, 0x61, 0x43, 0x00, 0x00, 0x00)
        assertEquals(AudioFormat.Flac, codec.sniff(data.asBytes()))
    }

    @Test
    fun sniffReturnsNullForMp3() {
        val data = byteArrayOf(0x49, 0x44, 0x33, 0x04, 0x00, 0x00)
        assertNull(codec.sniff(data.asBytes()))
    }

    @Test
    fun sniffReturnsNullForShortData() {
        assertNull(codec.sniff(byteArrayOf(0x66, 0x4C, 0x61).asBytes()))
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
