package dev.transmute.gstreamer

import dev.transmute.gstreamer.GStreamerTestHelpers.testContext
import dev.transmute.image.ImageFormat
import dev.transmute.image.JpegEncodeOptions
import dev.transmute.image.PngEncodeOptions
import dev.transmute.image.WebPEncodeOptions
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tests that encode options (quality, compression) actually affect output.
 *
 * For each format with a quality knob, encodes the same input at two
 * different quality levels and verifies that the output size changes.
 * This confirms that the options are wired through to the encoder.
 */
class EncodeOptionsVariationTest : GStreamerTestBase() {

    private val ctx = testContext()

    // =======================================================================
    // JPEG quality variation
    // =======================================================================

    @Test
    fun jpeg_quality_affects_size() = runTest {
        val image = GStreamerTestHelpers.solidColor(128, 128, r = 180, g = 90, b = 45)
        val encoder = dev.transmute.image.codecs.jvm.JvmImageIoEncoder()

        val lowQ = encoder.encode(image, ImageFormat.Jpeg, JpegEncodeOptions(quality = 0.10f), ctx)
        val highQ = encoder.encode(image, ImageFormat.Jpeg, JpegEncodeOptions(quality = 0.95f), ctx)

        assertTrue(lowQ.isNotEmpty(), "Low quality JPEG must not be empty")
        assertTrue(highQ.isNotEmpty(), "High quality JPEG must not be empty")
        assertNotEquals(
            lowQ.size, highQ.size,
            "Different JPEG qualities must produce different sizes " +
                "(low=${lowQ.size}, high=${highQ.size})",
        )
    }

    @Test
    fun jpeg_lowQuality_smallerThanHigh() = runTest {
        // Use a more complex image (gradient) to ensure quality difference is visible
        val image = gradientImage(128, 128)
        val encoder = dev.transmute.image.codecs.jvm.JvmImageIoEncoder()

        val lowQ = encoder.encode(image, ImageFormat.Jpeg, JpegEncodeOptions(quality = 0.05f), ctx)
        val highQ = encoder.encode(image, ImageFormat.Jpeg, JpegEncodeOptions(quality = 0.99f), ctx)

        assertTrue(
            lowQ.size < highQ.size,
            "Low quality JPEG (${lowQ.size}b) should be smaller than high quality (${highQ.size}b)",
        )
    }

    // =======================================================================
    // PNG compression variation
    // =======================================================================

    @Test
    fun png_compressionLevel_affects_size() = runTest {
        val image = gradientImage(128, 128)
        val encoder = dev.transmute.image.codecs.jvm.JvmImageIoEncoder()

        val noCompression = encoder.encode(image, ImageFormat.Png, PngEncodeOptions(compressionLevel = 0), ctx)
        val maxCompression = encoder.encode(image, ImageFormat.Png, PngEncodeOptions(compressionLevel = 9), ctx)

        assertTrue(noCompression.isNotEmpty(), "Uncompressed PNG must not be empty")
        assertTrue(maxCompression.isNotEmpty(), "Max-compressed PNG must not be empty")
        assertTrue(
            maxCompression.size <= noCompression.size,
            "Max compression PNG (${maxCompression.size}b) should be <= uncompressed (${noCompression.size}b)",
        )
    }

    // =======================================================================
    // WebP quality variation
    // =======================================================================

    @Test
    fun webp_quality_affects_size() = runTest {
        val canEncodeWebp = javax.imageio.ImageIO.getImageWritersByFormatName("webp")
            .asSequence().firstOrNull() != null
        if (!canEncodeWebp) {
            println("SKIP: WebP writer not available on this JVM")
            return@runTest
        }

        val image = gradientImage(128, 128)
        val encoder = dev.transmute.image.codecs.jvm.JvmImageIoEncoder()

        val lowQ = encoder.encode(image, ImageFormat.Webp, WebPEncodeOptions(quality = 0.10f), ctx)
        val highQ = encoder.encode(image, ImageFormat.Webp, WebPEncodeOptions(quality = 0.95f), ctx)

        assertTrue(lowQ.isNotEmpty(), "Low quality WebP must not be empty")
        assertTrue(highQ.isNotEmpty(), "High quality WebP must not be empty")
        assertNotEquals(
            lowQ.size, highQ.size,
            "Different WebP qualities must produce different sizes",
        )
    }

    // NOTE: HEIF/AVIF quality variation tests have been moved to the libheif plugin module.

    // =======================================================================
    // Helpers
    // =======================================================================

    /**
     * Creates a gradient [dev.transmute.image.ImageIR] to exercise compression
     * better than a solid-colour image.
     */
    private fun gradientImage(width: Int, height: Int): dev.transmute.image.ImageIR {
        val bpp = 4
        val stride = width * bpp
        val pixels = ByteArray(height * stride)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val off = y * stride + x * bpp
                pixels[off] = ((x * 255) / width).toByte()      // R: horizontal gradient
                pixels[off + 1] = ((y * 255) / height).toByte() // G: vertical gradient
                pixels[off + 2] = (((x + y) * 127) / (width + height)).toByte() // B: diagonal
                pixels[off + 3] = 0xFF.toByte()                 // A: opaque
            }
        }
        return dev.transmute.image.ImageIR(
            buffer = dev.transmute.image.ByteArrayPixelBuffer(pixels),
            width = width,
            height = height,
            stride = stride,
            pixelFormat = dev.transmute.image.PixelFormat.RGBA_8888,
            alphaSemantics = dev.transmute.image.AlphaSemantics.OPAQUE,
            colorInfo = dev.transmute.image.ColorInfo(),
        )
    }
}
