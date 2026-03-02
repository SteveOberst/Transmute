package dev.transmute.gstreamer

import dev.transmute.transmute
import dev.transmute.audio.AudioFormat
import dev.transmute.audio.CanonicalAudioEncodeOptions
import dev.transmute.audio.codecs.WavEncoder
import dev.transmute.audio.codecs.jvm.JvmMp3Codec
import dev.transmute.codec.OutputFormat
import dev.transmute.gstreamer.GStreamerTestHelpers.requireGStreamer
import dev.transmute.gstreamer.GStreamerTestHelpers.requireGStreamerElement
import dev.transmute.gstreamer.GStreamerTestHelpers.testContext
import dev.transmute.image.CanonicalImageEncodeOptions
import dev.transmute.image.HeifEncodeOptions
import dev.transmute.image.ImageFormat
import dev.transmute.image.JpegEncodeOptions
import dev.transmute.image.PngEncodeOptions
import dev.transmute.image.WebPEncodeOptions
import dev.transmute.model.core.Bytes
import dev.transmute.model.core.UnknownFormat
import dev.transmute.model.core.asBytes
import dev.transmute.video.CanonicalVideoEncodeOptions
import dev.transmute.video.VideoFormat
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * End-to-end tests for [dev.transmute.TransmuteInspect.detectFormat].
 *
 * Generates **real media bytes** for every supported format and verifies
 * that `Transmute.inspect.detectFormat()` correctly identifies each one.
 * This exercises the full detection chain: magic-byte sniffing, BMFF
 * disambiguation, and the image->video->audio priority cascade.
 *
 * Soft-skipped when GStreamer is not available locally.
 */
class DetectFormatEndToEndTest {

    private val ctx = testContext()

    private val transmute = transmute {
        plugins {
            install(GStreamer)
        }
    }

    // =======================================================================
    // IMAGE FORMAT DETECTION
    // =======================================================================

    @Test
    fun detectFormat_jpeg() = runTest {
        val imageIR = GStreamerTestHelpers.solidColor(32, 32, r = 180, g = 90, b = 45)
        val encoder = dev.transmute.image.codecs.jvm.JvmImageIoEncoder()
        val bytes = encoder.encode(imageIR, ImageFormat.Jpeg, JpegEncodeOptions(), ctx)

        val detected = transmute.inspect.detectFormat(bytes)
        assertEquals(ImageFormat.Jpeg, detected, "JPEG must be detected")
    }

    @Test
    fun detectFormat_png() = runTest {
        val imageIR = GStreamerTestHelpers.solidColor(32, 32, r = 100, g = 150, b = 200)
        val encoder = dev.transmute.image.codecs.jvm.JvmImageIoEncoder()
        val bytes = encoder.encode(imageIR, ImageFormat.Png, PngEncodeOptions(), ctx)

        val detected = transmute.inspect.detectFormat(bytes)
        assertEquals(ImageFormat.Png, detected, "PNG must be detected")
    }

    @Test
    fun detectFormat_gif() = runTest {
        val imageIR = GStreamerTestHelpers.solidColor(32, 32, r = 50, g = 100, b = 150)
        val encoder = dev.transmute.image.codecs.jvm.JvmImageIoEncoder()
        val bytes = encoder.encode(imageIR, ImageFormat.Gif, CanonicalImageEncodeOptions(), ctx)

        val detected = transmute.inspect.detectFormat(bytes)
        assertEquals(ImageFormat.Gif, detected, "GIF must be detected")
    }

    @Test
    fun detectFormat_bmp() = runTest {
        val imageIR = GStreamerTestHelpers.solidColor(32, 32, r = 100, g = 100, b = 100)
        val encoder = dev.transmute.image.codecs.bmp.BmpImageEncoder()
        val bytes = encoder.encode(imageIR, ImageFormat.Bmp, CanonicalImageEncodeOptions(), ctx)

        val detected = transmute.inspect.detectFormat(bytes)
        assertEquals(ImageFormat.Bmp, detected, "BMP must be detected")
    }

    @Test
    fun detectFormat_tiff() = runTest {
        val imageIR = GStreamerTestHelpers.solidColor(32, 32, r = 75, g = 125, b = 175)
        val encoder = dev.transmute.image.codecs.jvm.JvmImageIoEncoder()
        val bytes = encoder.encode(imageIR, ImageFormat.Tiff, CanonicalImageEncodeOptions(), ctx)

        val detected = transmute.inspect.detectFormat(bytes)
        assertEquals(ImageFormat.Tiff, detected, "TIFF must be detected")
    }

    @Test
    fun detectFormat_webp() = runTest {
        val canEncodeWebp = javax.imageio.ImageIO.getImageWritersByFormatName("webp")
            .asSequence().firstOrNull() != null
        if (!canEncodeWebp) {
            println("SKIP: WebP writer not available on this JVM")
            return@runTest
        }

        val imageIR = GStreamerTestHelpers.solidColor(32, 32, r = 200, g = 50, b = 100)
        val encoder = dev.transmute.image.codecs.jvm.JvmImageIoEncoder()
        val bytes = encoder.encode(imageIR, ImageFormat.Webp, WebPEncodeOptions(), ctx)

        val detected = transmute.inspect.detectFormat(bytes)
        assertEquals(ImageFormat.Webp, detected, "WebP must be detected")
    }

    @Test
    fun detectFormat_heif() = runTest {
        requireGStreamerElement("x265enc") {
            val imageIR = GStreamerTestHelpers.solidColor(64, 64, r = 128, g = 64, b = 32)
            val bytes = GstImageEncoder().encode(imageIR, ImageFormat.Heif, HeifEncodeOptions(), ctx)

            val detected = transmute.inspect.detectFormat(bytes)
            // HEIF could detect as Heif or Heic depending on the ftyp brand
            val isHeif = detected == ImageFormat.Heif || detected == ImageFormat.Heic
            assert(isHeif) { "HEIF must be detected as Heif or Heic, got $detected" }
        }
    }

    @Test
    fun detectFormat_avif() = runTest {
        requireGStreamerElement("av1enc") {
            val imageIR = GStreamerTestHelpers.solidColor(64, 64, r = 50, g = 100, b = 200)
            val bytes = GstImageEncoder().encode(
                imageIR, ImageFormat.Avif,
                HeifEncodeOptions(format = ImageFormat.Avif), ctx,
            )

            val detected = transmute.inspect.detectFormat(bytes)
            assertEquals(ImageFormat.Avif, detected, "AVIF must be detected")
        }
    }

    // =======================================================================
    // AUDIO FORMAT DETECTION
    // =======================================================================

    @Test
    fun detectFormat_aac() = runTest {
        requireGStreamer {
            val audioIR = GStreamerTestHelpers.sineWave(durationMs = 200, sampleRate = 44100)
            val bytes = GstAacCodec().encode(audioIR, AudioFormat.Aac, CanonicalAudioEncodeOptions(), ctx)

            val detected = transmute.inspect.detectFormat(bytes)
            assertEquals(AudioFormat.Aac, detected, "AAC must be detected")
        }
    }

    @Test
    fun detectFormat_m4a() = runTest {
        requireGStreamer {
            val audioIR = GStreamerTestHelpers.sineWave(durationMs = 200, sampleRate = 44100)
            val bytes = GstM4aCodec().encode(audioIR, AudioFormat.M4a, CanonicalAudioEncodeOptions(), ctx)

            val detected = transmute.inspect.detectFormat(bytes)
            assertEquals(AudioFormat.M4a, detected, "M4A must be detected")
        }
    }

    @Test
    fun detectFormat_opus() = runTest {
        requireGStreamer {
            val audioIR = GStreamerTestHelpers.sineWave(durationMs = 200, sampleRate = 48000)
            val bytes = GstOpusCodec().encode(audioIR, AudioFormat.Opus, CanonicalAudioEncodeOptions(), ctx)

            val detected = transmute.inspect.detectFormat(bytes)
            // Opus is wrapped in OGG, so depending on detection it may be Opus or Ogg
            val isOpusOrOgg = detected == AudioFormat.Opus || detected == AudioFormat.Ogg
            assert(isOpusOrOgg) { "Opus/OGG must be detected, got $detected" }
        }
    }

    @Test
    fun detectFormat_flac() = runTest {
        requireGStreamer {
            val audioIR = GStreamerTestHelpers.sineWave(durationMs = 200, sampleRate = 44100)
            val bytes = GstFlacEncoder().encode(audioIR, AudioFormat.Flac, CanonicalAudioEncodeOptions(), ctx)

            val detected = transmute.inspect.detectFormat(bytes)
            assertEquals(AudioFormat.Flac, detected, "FLAC must be detected")
        }
    }

    @Test
    fun detectFormat_ogg() = runTest {
        requireGStreamer {
            val audioIR = GStreamerTestHelpers.sineWave(durationMs = 200, sampleRate = 44100)
            val bytes = GstOggVorbisEncoder().encode(
                audioIR, AudioFormat.Ogg, CanonicalAudioEncodeOptions(), ctx,
            )

            val detected = transmute.inspect.detectFormat(bytes)
            assertEquals(AudioFormat.Ogg, detected, "OGG must be detected")
        }
    }

    @Test
    fun detectFormat_wav() = runTest {
        val audioIR = GStreamerTestHelpers.sineWave(durationMs = 200, sampleRate = 44100)
        val bytes = WavEncoder().encode(audioIR, AudioFormat.Wav, CanonicalAudioEncodeOptions(), ctx)

        val detected = transmute.inspect.detectFormat(bytes)
        assertEquals(AudioFormat.Wav, detected, "WAV must be detected")
    }

    @Test
    fun detectFormat_mp3() = runTest {
        val audioIR = GStreamerTestHelpers.sineWave(durationMs = 500, sampleRate = 44100)
        val bytes = JvmMp3Codec().encode(audioIR, AudioFormat.Mp3, CanonicalAudioEncodeOptions(), ctx)

        val detected = transmute.inspect.detectFormat(bytes)
        assertEquals(AudioFormat.Mp3, detected, "MP3 must be detected")
    }

    // =======================================================================
    // VIDEO FORMAT DETECTION
    // =======================================================================

    @Test
    fun detectFormat_mp4() = runTest {
        requireGStreamer {
            val videoIR = GStreamerTestHelpers.syntheticVideo(
                width = 160, height = 120, frameRate = 10.0, durationMs = 200,
            )
            val bytes = GstMp4Codec().encode(videoIR, VideoFormat.Mp4, CanonicalVideoEncodeOptions(), ctx)

            val detected = transmute.inspect.detectFormat(bytes)
            assertEquals(VideoFormat.Mp4, detected, "MP4 must be detected")
        }
    }

    @Test
    fun detectFormat_mov() = runTest {
        requireGStreamer {
            val videoIR = GStreamerTestHelpers.syntheticVideo(
                width = 160, height = 120, frameRate = 10.0, durationMs = 200,
            )
            val bytes = GstMovCodec().encode(videoIR, VideoFormat.Mov, CanonicalVideoEncodeOptions(), ctx)

            val detected = transmute.inspect.detectFormat(bytes)
            assertEquals(VideoFormat.Mov, detected, "MOV must be detected")
        }
    }

    @Test
    fun detectFormat_webm() = runTest {
        requireGStreamer {
            val videoIR = GStreamerTestHelpers.syntheticVideo(
                width = 160, height = 120, frameRate = 10.0, durationMs = 200,
            )
            val bytes = GstWebmCodec().encode(videoIR, VideoFormat.Webm, CanonicalVideoEncodeOptions(), ctx)

            val detected = transmute.inspect.detectFormat(bytes)
            assertEquals(VideoFormat.Webm, detected, "WebM must be detected")
        }
    }

    @Test
    fun detectFormat_mkv() = runTest {
        requireGStreamer {
            val videoIR = GStreamerTestHelpers.syntheticVideo(
                width = 160, height = 120, frameRate = 10.0, durationMs = 200,
            )
            val bytes = GstMkvCodec().encode(videoIR, VideoFormat.Mkv, CanonicalVideoEncodeOptions(), ctx)

            val detected = transmute.inspect.detectFormat(bytes)
            assertEquals(VideoFormat.Mkv, detected, "MKV must be detected")
        }
    }

    @Test
    fun detectFormat_avi() = runTest {
        requireGStreamer {
            val videoIR = GStreamerTestHelpers.syntheticVideo(
                width = 160, height = 120, frameRate = 10.0, durationMs = 200,
            )
            val bytes = GstAviCodec().encode(videoIR, VideoFormat.Avi, CanonicalVideoEncodeOptions(), ctx)

            val detected = transmute.inspect.detectFormat(bytes)
            assertEquals(VideoFormat.Avi, detected, "AVI must be detected")
        }
    }

    // =======================================================================
    // EDGE CASES
    // =======================================================================

    @Test
    fun detectFormat_unknownBytes_returnsUnknown() {
        val garbage = ByteArray(256) { (it * 37).toByte() }
        val detected = transmute.inspect.detectFormat(garbage.asBytes())
        assertEquals(UnknownFormat, detected, "Random bytes must return UnknownFormat")
    }

    @Test
    fun detectFormat_emptyBytes_returnsUnknown() {
        val detected = transmute.inspect.detectFormat(ByteArray(0).asBytes())
        assertEquals(UnknownFormat, detected, "Empty bytes must return UnknownFormat")
    }

    @Test
    fun detectFormat_tooSmall_returnsUnknown() {
        val detected = transmute.inspect.detectFormat(byteArrayOf(0x00, 0x01).asBytes())
        assertEquals(UnknownFormat, detected, "Tiny byte array must return UnknownFormat")
    }

    // =======================================================================
    // DOMAIN-SPECIFIC DETECTION (Inspect sub-APIs)
    // =======================================================================

    @Test
    fun inspectImage_detectFormat_jpeg() = runTest {
        val imageIR = GStreamerTestHelpers.solidColor(32, 32, r = 180, g = 90, b = 45)
        val encoder = dev.transmute.image.codecs.jvm.JvmImageIoEncoder()
        val bytes = encoder.encode(imageIR, ImageFormat.Jpeg, JpegEncodeOptions(), ctx)

        val detected = transmute.inspect.image.detectFormat(bytes)
        assertEquals(ImageFormat.Jpeg, detected, "inspect.image must detect JPEG")
    }

    @Test
    fun inspectAudio_detectFormat_wav() = runTest {
        val audioIR = GStreamerTestHelpers.sineWave(durationMs = 200, sampleRate = 44100)
        val bytes = WavEncoder().encode(audioIR, AudioFormat.Wav, CanonicalAudioEncodeOptions(), ctx)

        val detected = transmute.inspect.audio.detectFormat(bytes)
        assertEquals(AudioFormat.Wav, detected, "inspect.audio must detect WAV")
    }

    @Test
    fun inspectVideo_detectFormat_mp4() = runTest {
        requireGStreamer {
            val videoIR = GStreamerTestHelpers.syntheticVideo(
                width = 160, height = 120, frameRate = 10.0, durationMs = 200,
            )
            val bytes = GstMp4Codec().encode(videoIR, VideoFormat.Mp4, CanonicalVideoEncodeOptions(), ctx)

            val detected = transmute.inspect.video.detectFormat(bytes)
            assertEquals(VideoFormat.Mp4, detected, "inspect.video must detect MP4")
        }
    }

    // =======================================================================
    // BMFF DISAMBIGUATION
    // =======================================================================

    @Test
    fun detectFormat_bmff_mp4_vs_m4a() = runTest {
        requireGStreamer {
            // MP4 with video track
            val videoIR = GStreamerTestHelpers.syntheticVideo(
                width = 160, height = 120, frameRate = 10.0, durationMs = 200,
            )
            val mp4Bytes = GstMp4Codec().encode(videoIR, VideoFormat.Mp4, CanonicalVideoEncodeOptions(), ctx)

            // M4A with audio only
            val audioIR = GStreamerTestHelpers.sineWave(durationMs = 200, sampleRate = 44100)
            val m4aBytes = GstM4aCodec().encode(audioIR, AudioFormat.M4a, CanonicalAudioEncodeOptions(), ctx)

            val mp4Detected = transmute.inspect.detectFormat(mp4Bytes)
            val m4aDetected = transmute.inspect.detectFormat(m4aBytes)

            // Both are BMFF but should be disambiguated
            assertNotEquals(mp4Detected, m4aDetected, "MP4 and M4A must be distinguished")

            // MP4 should be detected as video
            assertEquals(VideoFormat.Mp4, mp4Detected, "MP4 with video track must detect as MP4")
            // M4A should be detected as audio
            assertEquals(AudioFormat.M4a, m4aDetected, "M4A with audio only must detect as M4A")
        }
    }
}
