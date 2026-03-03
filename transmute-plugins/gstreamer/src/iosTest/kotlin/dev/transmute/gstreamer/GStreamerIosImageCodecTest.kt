package dev.transmute.gstreamer

import dev.transmute.gstreamer.GStreamerIosTestHelpers.requireGStreamerElement
import dev.transmute.gstreamer.GStreamerIosTestHelpers.testContext
import dev.transmute.image.CanonicalImageDecodeOptions
import dev.transmute.image.HeifEncodeOptions
import dev.transmute.image.ImageFormat
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end integration tests for GStreamer iOS image codecs.
 *
 * Tests exercise HEIF/HEIC/AVIF decode (GStreamer -> PNG -> CoreGraphics)
 * and encode (CoreGraphics -> PNG -> GStreamer -> target) pipelines.
 *
 * Encode tests require specific GStreamer elements:
 * - `x265enc` for HEIF/HEIC
 * - `av1enc` for AVIF
 *
 * Soft-skipped when the required capability is absent.
 *
 * Run: `./gradlew :transmute-gstreamer:iosSimulatorArm64Test`
 */
class GStreamerIosImageCodecTest {

    private val ctx = testContext()
    private val decoder = GstIosImageDecoder()
    private val encoder = GstIosImageEncoder()

    // -- Decoder metadata ---------------------------------------------------

    @Test
    fun decoder_supportedFormats_containsHeifHeicAvif() {
        val formats = decoder.supportedFormats
        assertTrue(ImageFormat.Heif in formats, "Must support HEIF")
        assertTrue(ImageFormat.Heic in formats, "Must support HEIC")
        assertTrue(ImageFormat.Avif in formats, "Must support AVIF")
    }

    // -- Encoder metadata ---------------------------------------------------

    @Test
    fun encoder_supportedFormats_containsHeifHeicAvif() {
        val formats = encoder.supportedFormats
        assertTrue(ImageFormat.Heif in formats, "Must support HEIF")
        assertTrue(ImageFormat.Heic in formats, "Must support HEIC")
        assertTrue(ImageFormat.Avif in formats, "Must support AVIF")
    }

    // -- HEIF encode -> decode roundtrip -------------------------------------

    @Test
    fun heif_encodeAndDecode_roundTrip() = runTest {
        requireGStreamerElement("x265enc") {
            val ir = GStreamerIosTestHelpers.solidColor(64, 64, r = 128, g = 64, b = 32)
            val encoded = encoder.encode(ir, ImageFormat.Heif, HeifEncodeOptions(), ctx)
            assertTrue(encoded.isNotEmpty(), "Encoded HEIF output must not be empty")

            val decoded = decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)
            assertNotNull(decoded, "Decoded ImageIR must not be null")
            assertEquals(64, decoded.width, "Width must survive roundtrip")
            assertEquals(64, decoded.height, "Height must survive roundtrip")
        }
    }

    // -- HEIC encode -> decode roundtrip -------------------------------------

    @Test
    fun heic_encodeAndDecode_roundTrip() = runTest {
        requireGStreamerElement("x265enc") {
            val ir = GStreamerIosTestHelpers.solidColor(64, 64, r = 200, g = 100, b = 50)
            val encoded = encoder.encode(
                ir, ImageFormat.Heic,
                HeifEncodeOptions(format = ImageFormat.Heic), ctx,
            )
            assertTrue(encoded.isNotEmpty(), "Encoded HEIC output must not be empty")

            val decoded = decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)
            assertNotNull(decoded)
            assertEquals(64, decoded.width)
            assertEquals(64, decoded.height)
        }
    }

    // -- AVIF encode -> decode roundtrip -------------------------------------

    @Test
    fun avif_encodeAndDecode_roundTrip() = runTest {
        requireGStreamerElement("av1enc") {
            val ir = GStreamerIosTestHelpers.solidColor(64, 64, r = 100, g = 200, b = 150)
            val encoded = encoder.encode(ir, ImageFormat.Avif, HeifEncodeOptions(), ctx)
            assertTrue(encoded.isNotEmpty(), "Encoded AVIF output must not be empty")

            val decoded = decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)
            assertNotNull(decoded)
            assertEquals(64, decoded.width)
            assertEquals(64, decoded.height)
        }
    }
}
