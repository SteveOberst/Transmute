package dev.transmute

import dev.transmute.audio.AudioFormat
import dev.transmute.audio.AudioIR
import dev.transmute.audio.AudioSamples
import dev.transmute.audio.CanonicalAudioDecodeOptions
import dev.transmute.audio.CanonicalAudioEncodeOptions
import dev.transmute.audio.codecs.WavEncoder
import dev.transmute.audio.codecs.jvm.JvmMp3Codec
import dev.transmute.common.PipelineContext
import dev.transmute.common.PrintLogger
import dev.transmute.image.*
import dev.transmute.image.codecs.bmp.BmpImageEncoder
import dev.transmute.image.codecs.jvm.JvmImageIoEncoder
import dev.transmute.structure.audio.*
import dev.transmute.structure.image.*
import kotlinx.coroutines.test.runTest
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.*

/**
 * Platform-agnostic end-to-end tests that require only the JVM/desktop codecs
 * bundled in `transmute-audio` and `transmute-image` (no GStreamer, no libheif).
 *
 * Exercises the full pipeline: encode to real bytes -> StructureReader ->
 * decode roundtrip, for every format covered by pure-JVM codecs.
 *
 * These tests complement [dev.transmute.gstreamer.TrueEndToEndTest] (which
 * tests GStreamer-dependent codecs) and the libheif desktopTest suite.
 */
class CoreEndToEndTest {

    private fun testContext(): PipelineContext = PipelineContext(logger = PrintLogger)

    // -----------------------------------------------------------------------
    // Synthetic-media helpers (inline -- no dependency on GStreamer test code)
    // -----------------------------------------------------------------------

    private fun sineWave(
        durationMs: Long = 500,
        sampleRate: Int = 44100,
        channelCount: Int = 1,
    ): AudioIR {
        val totalSamples = ((durationMs * sampleRate * channelCount) / 1000).toInt()
        val data = FloatArray(totalSamples)
        for (i in 0 until totalSamples step channelCount) {
            val t = i.toFloat() / (sampleRate * channelCount)
            val sample = 0.5f * sin(2 * PI.toFloat() * 440f * t)
            for (ch in 0 until channelCount) data[i + ch] = sample
        }
        return AudioIR(samples = AudioSamples(data, sampleRate, channelCount), sampleRate = sampleRate)
    }

    private fun solidColor(
        width: Int, height: Int,
        r: Int = 128, g: Int = 128, b: Int = 128, a: Int = 255,
    ): ImageIR {
        val rgba = ByteArray(width * height * 4)
        for (i in 0 until width * height) {
            rgba[i * 4]     = r.toByte()
            rgba[i * 4 + 1] = g.toByte()
            rgba[i * 4 + 2] = b.toByte()
            rgba[i * 4 + 3] = a.toByte()
        }
        return ImageIR(
            buffer = ByteArrayPixelBuffer(rgba),
            width = width,
            height = height,
            stride = width * 4,
            pixelFormat = PixelFormat.RGBA_8888,
            alphaSemantics = AlphaSemantics.STRAIGHT,
            colorInfo = ColorInfo(),
            orientation = Orientation.NORMAL,
            metadata = ImageMetadata(),
        )
    }

    // =======================================================================
    // AUDIO: pure-JVM codecs
    // =======================================================================

    @Test
    fun wav_realMedia_structureReaderAccepts() = runTest {
        val ir = sineWave(durationMs = 200, sampleRate = 44100)
        val wavBytes = WavEncoder().encode(ir, AudioFormat.Wav, CanonicalAudioEncodeOptions(), testContext())

        val structure = WavStructureReader().read(wavBytes)
        assertNotNull(structure, "WavStructure must not be null")
        assertTrue(structure.riff.children.isNotEmpty(), "WAV must have RIFF children")
    }

    @Test
    fun mp3_realMedia_structureReaderAccepts() = runTest {
        val ir = sineWave(durationMs = 500, sampleRate = 44100)
        val mp3Bytes = JvmMp3Codec().encode(ir, AudioFormat.Mp3, CanonicalAudioEncodeOptions(), testContext())

        val structure = Mp3StructureReader().read(mp3Bytes)
        assertNotNull(structure, "Mp3Structure must not be null")
        assertTrue(structure.audioData.isNotEmpty(), "MP3 must have audio data")
    }

    @Test
    fun mp3_decodeRoundtrip() = runTest {
        val ir = sineWave(durationMs = 500, sampleRate = 44100)
        val mp3Bytes = JvmMp3Codec().encode(ir, AudioFormat.Mp3, CanonicalAudioEncodeOptions(), testContext())

        val decoded = JvmMp3Codec().decode(mp3Bytes, CanonicalAudioDecodeOptions(), testContext())
        assertTrue(decoded.durationMs > 0, "MP3 must decode with positive duration")
    }

    // =======================================================================
    // IMAGE: pure-JVM codecs (JvmImageIo / BmpImageEncoder)
    // =======================================================================

    @Test
    fun jpeg_realMedia_structureReaderAccepts() = runTest {
        val ir = solidColor(64, 64, r = 180, g = 90, b = 45)
        val jpegBytes = JvmImageIoEncoder().encode(ir, ImageFormat.Jpeg, JpegEncodeOptions(), testContext())

        val structure = JpegStructureReader().read(jpegBytes)
        assertNotNull(structure, "JpegStructure must not be null")
        assertTrue(structure.segments.isNotEmpty(), "JPEG must have segments")
    }

    @Test
    fun jpeg_decodeRoundtrip() = runTest {
        val ir = solidColor(64, 64, r = 180, g = 90, b = 45)
        val jpegBytes = JvmImageIoEncoder().encode(ir, ImageFormat.Jpeg, JpegEncodeOptions(), testContext())

        val decoded = dev.transmute.image.codecs.jvm.JvmImageIoDecoder()
            .decode(jpegBytes, CanonicalImageDecodeOptions(), testContext())
        assertEquals(64, decoded.width)
        assertEquals(64, decoded.height)
    }

    @Test
    fun png_realMedia_structureReaderAccepts() = runTest {
        val ir = solidColor(64, 64, r = 100, g = 150, b = 200)
        val pngBytes = JvmImageIoEncoder().encode(ir, ImageFormat.Png, PngEncodeOptions(), testContext())

        val structure = PngStructureReader().read(pngBytes)
        assertNotNull(structure, "PngStructure must not be null")
        assertTrue(structure.chunks.isNotEmpty(), "PNG must have chunks")
    }

    @Test
    fun gif_realMedia_structureReaderAccepts() = runTest {
        val ir = solidColor(64, 64, r = 50, g = 100, b = 150)
        val gifBytes = JvmImageIoEncoder().encode(ir, ImageFormat.Gif, CanonicalImageEncodeOptions(), testContext())

        val structure = GifStructureReader().read(gifBytes)
        assertNotNull(structure, "GifStructure must not be null")
        // GIF blocks may be empty for a solid-color image with no extensions -- that is valid.
    }

    @Test
    fun tiff_realMedia_structureReaderAccepts() = runTest {
        val ir = solidColor(32, 32, r = 75, g = 125, b = 175)
        val tiffBytes = JvmImageIoEncoder().encode(ir, ImageFormat.Tiff, CanonicalImageEncodeOptions(), testContext())

        val structure = TiffStructureReader().read(tiffBytes)
        assertNotNull(structure, "TiffStructure must not be null")
        assertTrue(structure.ifds.isNotEmpty(), "TIFF must have IFDs")
    }

    @Test
    fun bmp_realMedia_structureReaderAccepts() = runTest {
        val ir = solidColor(32, 32, r = 100, g = 100, b = 100)
        val bmpBytes = BmpImageEncoder().encode(ir, ImageFormat.Bmp, CanonicalImageEncodeOptions(), testContext())

        val structure = BmpStructureReader().read(bmpBytes)
        assertNotNull(structure, "BmpStructure must not be null")
    }

    @Test
    fun webp_realMedia_structureReaderAccepts() = runTest {
        // WebP write requires the TwelveMonkeys plugin which is optional.
        val canEncodeWebp = javax.imageio.ImageIO.getImageWritersByFormatName("webp")
            .asSequence().firstOrNull() != null
        if (!canEncodeWebp) {
            println("SKIP webp_realMedia_structureReaderAccepts: WebP writer not available on this JVM")
            return@runTest
        }

        val ir = solidColor(64, 64, r = 200, g = 50, b = 100)
        val webpBytes = JvmImageIoEncoder().encode(ir, ImageFormat.Webp, WebPEncodeOptions(), testContext())

        val structure = WebpStructureReader().read(webpBytes)
        assertNotNull(structure, "WebpStructure must not be null")
        assertTrue(structure.riff.children.isNotEmpty(), "WebP must have RIFF children")
    }
}
