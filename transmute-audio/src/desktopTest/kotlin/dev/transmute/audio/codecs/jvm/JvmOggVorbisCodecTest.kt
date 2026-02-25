package dev.transmute.audio.codecs.jvm

import dev.transmute.audio.AudioFormat
import dev.transmute.audio.AudioTestHelpers
import dev.transmute.audio.CanonicalAudioEncodeOptions
import dev.transmute.model.core.asBytes
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class JvmOggVorbisCodecTest {

    private val codec = JvmOggVorbisCodec()

    // -- Format declarations --

    @Test
    fun decodableFormatsContainsOgg() {
        assertTrue(AudioFormat.Ogg in codec.decodableFormats)
    }

    @Test
    fun encodableFormatsIsEmpty() {
        // Native OGG/Vorbis encoding requires the transmute-gstreamer module.
        assertTrue(codec.encodableFormats.isEmpty())
    }

    // -- Sniff --

    @Test
    fun sniffDetectsOggVorbisMagic() {
        // "OggS" + page header bytes + "\x01vorbis" at offset 28
        val data = ByteArray(35)
        data[0] = 0x4F; data[1] = 0x67; data[2] = 0x67; data[3] = 0x53
        data[28] = 0x01
        data[29] = 0x76; data[30] = 0x6F; data[31] = 0x72
        data[32] = 0x62; data[33] = 0x69; data[34] = 0x73
        assertEquals(AudioFormat.Ogg, codec.sniff(data.asBytes()))
    }

    @Test
    fun sniffReturnsNullForNonOgg() {
        val data = byteArrayOf(0x49, 0x44, 0x33, 0x04, 0x00, 0x00)
        assertNull(codec.sniff(data.asBytes()))
    }

    @Test
    fun sniffReturnsNullForShortData() {
        assertNull(codec.sniff(byteArrayOf(0x4F, 0x67, 0x67).asBytes()))
    }

    // -- Encode (requires transmute-gstreamer) --

    @Test
    fun encodeThrowsWithoutGstreamer() = runTest {
        val ir = AudioTestHelpers.sineWave(durationMs = 50)
        assertFailsWith<IllegalStateException> {
            codec.encode(ir, AudioFormat.Ogg, CanonicalAudioEncodeOptions(), AudioTestHelpers.testContext())
        }
    }
}
