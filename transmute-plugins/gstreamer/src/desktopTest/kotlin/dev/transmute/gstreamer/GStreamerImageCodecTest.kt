package dev.transmute.gstreamer

import dev.transmute.gstreamer.GStreamerTestHelpers.requireGStreamerElement
import dev.transmute.gstreamer.GStreamerTestHelpers.testContext
import dev.transmute.image.CanonicalImageDecodeOptions
import dev.transmute.image.HeifEncodeOptions
import dev.transmute.image.ImageFormat
import dev.transmute.model.core.Bytes
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end integration tests for GStreamer image codecs.
 *
 * Tests exercise HEIF/HEIC/AVIF decode (GStreamer -> PNG -> ImageIO) and
 * encode (ImageIO -> PNG -> GStreamer -> target) pipelines.
 *
 * Encode tests require specific GStreamer elements:
 * - `x265enc` for HEIF/HEIC
 * - `av1enc` for AVIF
 *
 * All tests are soft-skipped when the required capability is absent.
 */
class GStreamerImageCodecTest {

    private val ctx = testContext()
    private val decoder = GstImageDecoder()
    private val encoder = GstImageEncoder()

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

    // -- Sniff tests --------------------------------------------------------

    @Test
    fun sniff_heicBrand() {
        val header = byteArrayOf(
            0x00, 0x00, 0x00, 0x20,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'h'.code.toByte(), 'e'.code.toByte(), 'i'.code.toByte(), 'c'.code.toByte(),
        )
        assertEquals(ImageFormat.Heic, decoder.sniff(Bytes(header)))
    }

    @Test
    fun sniff_mif1Brand_isHeif() {
        val header = byteArrayOf(
            0x00, 0x00, 0x00, 0x20,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'm'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), '1'.code.toByte(),
        )
        assertEquals(ImageFormat.Heif, decoder.sniff(Bytes(header)))
    }

    @Test
    fun sniff_avifBrand() {
        val header = byteArrayOf(
            0x00, 0x00, 0x00, 0x20,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'a'.code.toByte(), 'v'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(),
        )
        assertEquals(ImageFormat.Avif, decoder.sniff(Bytes(header)))
    }

    @Test
    fun sniff_unknownBrand_returnsNull() {
        val header = byteArrayOf(
            0x00, 0x00, 0x00, 0x20,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'X'.code.toByte(), 'X'.code.toByte(), 'X'.code.toByte(), 'X'.code.toByte(),
        )
        assertNull(decoder.sniff(Bytes(header)))
    }

    @Test
    fun sniff_nonIsoBmff_returnsNull() {
        // RIFF header - not ISO BMFF
        assertNull(decoder.sniff(Bytes(byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        ))))
    }

    @Test
    fun sniff_shortData_returnsNull() {
        assertNull(decoder.sniff(Bytes(byteArrayOf(0x00, 0x00, 0x00))))
    }

    // -- HEIF encode -> decode roundtrip -------------------------------------

    @Test
    fun heif_encodeAndDecode_roundTrip() = runTest {
        requireGStreamerElement("x265enc") {
            val ir = GStreamerTestHelpers.solidColor(64, 64, r = 128, g = 64, b = 32)
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
            val ir = GStreamerTestHelpers.solidColor(64, 64, r = 200, g = 100, b = 50)
            val encoded = encoder.encode(ir, ImageFormat.Heic, HeifEncodeOptions(format = ImageFormat.Heic), ctx)
            assertTrue(encoded.isNotEmpty(), "Encoded HEIC output must not be empty")

            val decoded = decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)
            assertNotNull(decoded, "Decoded ImageIR must not be null")
            assertEquals(64, decoded.width, "Width must survive roundtrip")
            assertEquals(64, decoded.height, "Height must survive roundtrip")
        }
    }

    // -- AVIF encode -> decode roundtrip -------------------------------------

    @Test
    fun avif_encodeAndDecode_roundTrip() = runTest {
        requireGStreamerElement("av1enc") {
            val ir = GStreamerTestHelpers.solidColor(64, 64, r = 50, g = 100, b = 200)
            val encoded = encoder.encode(ir, ImageFormat.Avif, HeifEncodeOptions(format = ImageFormat.Avif), ctx)
            assertTrue(encoded.isNotEmpty(), "Encoded AVIF output must not be empty")

            val decoded = decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)
            assertNotNull(decoded, "Decoded ImageIR must not be null")
            assertEquals(64, decoded.width, "Width must survive roundtrip")
            assertEquals(64, decoded.height, "Height must survive roundtrip")
        }
    }

    // -- Encode produces valid ISO BMFF header ------------------------------

    @Test
    fun heif_encode_producesIsoBmffOutput() = runTest {
        requireGStreamerElement("x265enc") {
            val ir = GStreamerTestHelpers.solidColor(32, 32, r = 0, g = 0, b = 0)
            val encoded = encoder.encode(ir, ImageFormat.Heif, HeifEncodeOptions(), ctx)
            assertTrue(encoded.size > 12, "Output must have at least 12 bytes")

            // ISO BMFF should have "ftyp" at offset 4
            val ftyp = encoded.data.sliceArray(4 until 8).decodeToString()
            assertEquals("ftyp", ftyp, "HEIF output must be an ISO BMFF container with ftyp box")
        }
    }
}
