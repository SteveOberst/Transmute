package dev.transmute.audio.codecs.jvm

import dev.transmute.audio.AudioTestHelpers
import dev.transmute.core.AudioFormat
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class JvmOggVorbisCodecTest {

    private val codec = JvmOggVorbisCodec()

    // ── Format declarations ──

    @Test
    fun decodableFormatsContainsOgg() {
        assertTrue(AudioFormat.OGG in codec.decodableFormats)
    }

    @Test
    fun encodableFormatsReflectsFfmpegAvailability() {
        if (FfmpegAudioEngine.available) {
            assertTrue(AudioFormat.OGG in codec.encodableFormats)
        } else {
            assertTrue(codec.encodableFormats.isEmpty())
        }
    }

    // ── Sniff ──

    @Test
    fun sniffDetectsOggVorbisMagic() {
        // "OggS" + page header bytes + "\x01vorbis" at offset 28
        val data = ByteArray(35)
        // "OggS" at offset 0
        data[0] = 0x4F; data[1] = 0x67; data[2] = 0x67; data[3] = 0x53
        // "\x01vorbis" at offset 28
        data[28] = 0x01
        data[29] = 0x76; data[30] = 0x6F; data[31] = 0x72
        data[32] = 0x62; data[33] = 0x69; data[34] = 0x73
        assertEquals(AudioFormat.OGG, codec.sniff(data))
    }

    @Test
    fun sniffReturnsNullForNonOgg() {
        val data = byteArrayOf(0x49, 0x44, 0x33, 0x04, 0x00, 0x00)
        assertNull(codec.sniff(data))
    }

    @Test
    fun sniffReturnsNullForShortData() {
        assertNull(codec.sniff(byteArrayOf(0x4F, 0x67, 0x67)))
    }

    // ── Encode + decode round-trip (FFmpeg required for encode) ──

    @Test
    fun encodeDecodeOggRoundTrip() = runTest {
        if (!FfmpegAudioEngine.available) {
            println("Skipping OGG encode/decode test — FFmpeg not available")
            return@runTest
        }

        val original = AudioTestHelpers.sineWave(
            frequency = 440f,
            durationMs = 300,
            sampleRate = 44100,
            amplitude = 0.5f,
            channelCount = 1,
        )

        val encoded = try {
            codec.encode(original, AudioTestHelpers.testContext())
        } catch (e: Exception) {
            if ("FFmpeg" in e.message.orEmpty() || "libvorbis" in e.message.orEmpty()) {
                println("SKIPPED: OGG/Vorbis encoding not available: ${e.message}")
                return@runTest
            }
            throw e
        }
        assertTrue(encoded.isNotEmpty(), "Encoded OGG should not be empty")

        // OGG starts with "OggS"
        assertEquals(0x4F.toByte(), encoded[0])
        assertEquals(0x67.toByte(), encoded[1])

        val decoded = codec.decode(encoded, AudioTestHelpers.testContext())
        assertEquals(original.sampleRate, decoded.sampleRate)
        assertEquals(original.channelCount, decoded.channelCount)
        assertTrue(decoded.samples.data.isNotEmpty())
    }

    @Test
    fun encodeThrowsWithoutFfmpeg() = runTest {
        if (FfmpegAudioEngine.available) {
            println("Skipping — FFmpeg is available, cannot test failure path")
            return@runTest
        }

        val ir = AudioTestHelpers.sineWave(durationMs = 50)
        assertFailsWith<IllegalStateException> {
            codec.encode(ir, AudioTestHelpers.testContext())
        }
    }
}
