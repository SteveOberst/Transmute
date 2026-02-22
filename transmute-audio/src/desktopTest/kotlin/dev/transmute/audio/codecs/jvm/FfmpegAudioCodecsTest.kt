package dev.transmute.audio.codecs.jvm

import dev.transmute.audio.AudioEncodeOptions
import dev.transmute.audio.AudioTestHelpers
import dev.transmute.core.AudioFormat
import dev.transmute.core.PrintLogger
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import dev.transmute.audio.DefaultAudioDecodeOptions
import dev.transmute.audio.DefaultAudioEncodeOptions

class FfmpegAudioCodecsTest {

    private val log = PrintLogger

    // -- FfmpegAudioEngine availability --

    @Test
    fun ffmpegAvailabilityIsDeterministic() {
        // Calling it twice should return the same result (lazy val)
        val first = FfmpegAudioEngine.available
        val second = FfmpegAudioEngine.available
        assertEquals(first, second, "FFmpeg availability should be stable")
    }

    // -- JvmAacCodec --

    @Test
    fun aacCodecFormatsCorrect() {
        if (!FfmpegAudioEngine.available) {
            log.warn("Skipping - FFmpeg not available")
            return
        }
        val codec = JvmAacCodec()
        assertTrue(AudioFormat.AAC in codec.decodableFormats)
        assertTrue(AudioFormat.AAC in codec.encodableFormats)
    }

    @Test
    fun aacCodecSniffDetectsAdts() {
        if (!FfmpegAudioEngine.available) {
            log.warn("Skipping - FFmpeg not available")
            return
        }
        val codec = JvmAacCodec()
        // ADTS sync: 0xFF 0xF1
        val adts = byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x50, 0x80.toByte())
        assertEquals(AudioFormat.AAC, codec.sniff(adts))
    }

    @Test
    fun aacCodecSniffReturnsNullForMp3() {
        if (!FfmpegAudioEngine.available) {
            log.warn("Skipping - FFmpeg not available")
            return
        }
        val codec = JvmAacCodec()
        val mp3 = byteArrayOf(0x49, 0x44, 0x33, 0x04, 0x00, 0x00)
        assertNull(codec.sniff(mp3))
    }

    @Test
    fun aacEncodeDecodeRoundTrip() = runTest {
        if (!FfmpegAudioEngine.available) {
            log.warn("Skipping - FFmpeg not available")
            return@runTest
        }
        val codec = JvmAacCodec()
        val original = AudioTestHelpers.sineWave(
            frequency = 440f,
            durationMs = 300,
            sampleRate = 44100,
            amplitude = 0.5f,
        )
        val encoded = codec.encode(original, AudioFormat.AAC, DefaultAudioEncodeOptions(), AudioTestHelpers.testContext())
        assertTrue(encoded.isNotEmpty())
        val decoded = codec.decode(encoded, DefaultAudioDecodeOptions(), AudioTestHelpers.testContext())
        assertEquals(original.sampleRate, decoded.sampleRate)
        assertTrue(decoded.samples.data.isNotEmpty())
    }

    // -- JvmM4aCodec --

    @Test
    fun m4aCodecFormatsCorrect() {
        if (!FfmpegAudioEngine.available) {
            log.warn("Skipping - FFmpeg not available")
            return
        }
        val codec = JvmM4aCodec()
        assertTrue(AudioFormat.M4A in codec.decodableFormats)
        assertTrue(AudioFormat.M4A in codec.encodableFormats)
    }

    @Test
    fun m4aCodecSniffDetectsFtyp() {
        if (!FfmpegAudioEngine.available) {
            log.warn("Skipping - FFmpeg not available")
            return
        }
        val codec = JvmM4aCodec()
        // ftyp box with M4A brand
        val data = ByteArray(12)
        // "ftyp" at offset 4
        data[4] = 0x66; data[5] = 0x74; data[6] = 0x79; data[7] = 0x70
        // "M4A " brand at offset 8
        data[8] = 0x4D; data[9] = 0x34; data[10] = 0x41; data[11] = 0x20
        assertEquals(AudioFormat.M4A, codec.sniff(data))
    }

    @Test
    fun m4aEncodeDecodeRoundTrip() = runTest {
        if (!FfmpegAudioEngine.available) {
            log.warn("Skipping - FFmpeg not available")
            return@runTest
        }
        val codec = JvmM4aCodec()
        val original = AudioTestHelpers.sineWave(
            frequency = 440f,
            durationMs = 300,
            sampleRate = 44100,
            amplitude = 0.5f,
        )
        val encoded = codec.encode(original, AudioFormat.M4A, DefaultAudioEncodeOptions(), AudioTestHelpers.testContext())
        assertTrue(encoded.isNotEmpty())
        val decoded = codec.decode(encoded, DefaultAudioDecodeOptions(), AudioTestHelpers.testContext())
        assertEquals(original.sampleRate, decoded.sampleRate)
        assertTrue(decoded.samples.data.isNotEmpty())
    }

    // -- JvmOpusCodec --

    @Test
    fun opusCodecFormatsCorrect() {
        if (!FfmpegAudioEngine.available) {
            log.warn("Skipping - FFmpeg not available")
            return
        }
        val codec = JvmOpusCodec()
        assertTrue(AudioFormat.OPUS in codec.decodableFormats)
        assertTrue(AudioFormat.OPUS in codec.encodableFormats)
    }

    @Test
    fun opusCodecSniffDetectsOpusInOgg() {
        if (!FfmpegAudioEngine.available) {
            log.warn("Skipping - FFmpeg not available")
            return
        }
        val codec = JvmOpusCodec()
        // OggS + "OpusHead" at offset 28
        val data = ByteArray(36)
        data[0] = 0x4F; data[1] = 0x67; data[2] = 0x67; data[3] = 0x53
        // "OpusHead" at offset 28
        val opus = "OpusHead".toByteArray()
        for (i in opus.indices) data[28 + i] = opus[i]
        assertEquals(AudioFormat.OPUS, codec.sniff(data))
    }

    @Test
    fun opusEncodeDecodeRoundTrip() = runTest {
        if (!FfmpegAudioEngine.available) {
            log.warn("Skipping - FFmpeg not available")
            return@runTest
        }
        val codec = JvmOpusCodec()
        val original = AudioTestHelpers.sineWave(
            frequency = 440f,
            durationMs = 300,
            sampleRate = 48000, // Opus prefers 48kHz
            amplitude = 0.5f,
        )
        val encoded = codec.encode(original, AudioFormat.OPUS, DefaultAudioEncodeOptions(), AudioTestHelpers.testContext())
        assertTrue(encoded.isNotEmpty())
        val decoded = codec.decode(encoded, DefaultAudioDecodeOptions(), AudioTestHelpers.testContext())
        assertTrue(decoded.samples.data.isNotEmpty())
    }
}
