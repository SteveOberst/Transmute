package dev.transmute.libheif

import dev.transmute.image.CanonicalImageDecodeOptions
import dev.transmute.image.HeifEncodeOptions
import dev.transmute.image.ImageFormat
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end integration tests for libheif image codecs on Desktop/JVM.
 *
 * Tests exercise the full encode/decode pipeline for HEIF, HEIC, and AVIF
 * using libheif CLI tools (`heif-dec` / `heif-enc`) as subprocesses.
 *
 * The `desktopTest` Gradle task is gated by `TRANSMUTE_LIBHEIF_TESTS` so
 * all tests may safely assume a working libheif installation (decoder +
 * encoder) is present.
 *
 * Encode tests require `heif-enc` -- annotated separately so they can be
 * individually skipped in encoder-only libheif builds (rare in practice).
 */
class LibHeifImageCodecTest {

    private val ctx     = LibHeifTestHelpers.testContext()
    private val decoder = LibHeifImageDecoder()
    private val encoder = LibHeifImageEncoder()

    // -- Decoder metadata --------------------------------------------------

    @Test
    fun decoder_supportedFormats_containsHeifHeicAvif() {
        val formats = decoder.supportedFormats
        assertTrue(ImageFormat.Heif in formats, "Must support HEIF")
        assertTrue(ImageFormat.Heic in formats, "Must support HEIC")
        assertTrue(ImageFormat.Avif in formats, "Must support AVIF")
    }

    // -- Encoder metadata --------------------------------------------------

    @Test
    fun encoder_supportedFormats_containsHeifHeicAvif() {
        val formats = encoder.supportedFormats
        assertTrue(ImageFormat.Heif in formats, "Must support HEIF")
        assertTrue(ImageFormat.Heic in formats, "Must support HEIC")
        assertTrue(ImageFormat.Avif in formats, "Must support AVIF")
    }

    // -- HEIF encode -> decode roundtrip -----------------------------------

    @Test
    fun heif_encodeAndDecode_roundTrip() = runTest {
        val ir      = LibHeifTestHelpers.solidColor(64, 64, r = 128, g = 64, b = 32)
        val encoded = encoder.encode(ir, ImageFormat.Heif, HeifEncodeOptions(), ctx)
        assertTrue(encoded.isNotEmpty(), "Encoded HEIF output must not be empty")

        val decoded = decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)
        assertNotNull(decoded, "Decoded ImageIR must not be null")
        assertEquals(64, decoded.width,  "Width must survive HEIF roundtrip")
        assertEquals(64, decoded.height, "Height must survive HEIF roundtrip")
    }

    // -- HEIC encode -> decode roundtrip -----------------------------------

    @Test
    fun heic_encodeAndDecode_roundTrip() = runTest {
        val ir      = LibHeifTestHelpers.solidColor(64, 64, r = 200, g = 100, b = 50)
        val encoded = encoder.encode(ir, ImageFormat.Heic, HeifEncodeOptions(), ctx)
        assertTrue(encoded.isNotEmpty(), "Encoded HEIC output must not be empty")

        val decoded = decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)
        assertNotNull(decoded, "Decoded ImageIR must not be null")
        assertEquals(64, decoded.width,  "Width must survive HEIC roundtrip")
        assertEquals(64, decoded.height, "Height must survive HEIC roundtrip")
    }

    // -- AVIF encode -> decode roundtrip -----------------------------------

    @Test
    fun avif_encodeAndDecode_roundTrip() = runTest {
        val ir      = LibHeifTestHelpers.solidColor(64, 64, r = 50, g = 100, b = 200)
        val encoded = encoder.encode(ir, ImageFormat.Avif, HeifEncodeOptions(), ctx)
        assertTrue(encoded.isNotEmpty(), "Encoded AVIF output must not be empty")

        val decoded = decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)
        assertNotNull(decoded, "Decoded ImageIR must not be null")
        assertEquals(64, decoded.width,  "Width must survive AVIF roundtrip")
        assertEquals(64, decoded.height, "Height must survive AVIF roundtrip")
    }

    // -- Encode produces valid ISO BMFF container --------------------------

    @Test
    fun heif_encode_producesIsoBmffOutput() = runTest {
        val ir      = LibHeifTestHelpers.solidColor(32, 32, r = 0, g = 0, b = 0)
        val encoded = encoder.encode(ir, ImageFormat.Heif, HeifEncodeOptions(), ctx)
        assertTrue(encoded.size > 12, "Output must have at least 12 bytes for a valid BMFF box")

        // ISO BMFF: bytes 4-7 are the box type; ftyp is the file-type box present in HEIF/HEIC/AVIF.
        val ftyp = encoded.data.sliceArray(4 until 8).decodeToString()
        assertEquals("ftyp", ftyp, "HEIF encode output must be an ISO BMFF container with ftyp box")
    }

    @Test
    fun heic_encode_producesIsoBmffOutput() = runTest {
        val ir      = LibHeifTestHelpers.solidColor(32, 32, r = 255, g = 255, b = 255)
        val encoded = encoder.encode(ir, ImageFormat.Heic, HeifEncodeOptions(), ctx)
        assertTrue(encoded.size > 12, "Output must have at least 12 bytes")

        val ftyp = encoded.data.sliceArray(4 until 8).decodeToString()
        assertEquals("ftyp", ftyp, "HEIC encode output must be a valid ISO BMFF container")
    }

    @Test
    fun avif_encode_producesIsoBmffOutput() = runTest {
        val ir      = LibHeifTestHelpers.solidColor(32, 32, r = 128, g = 128, b = 128)
        val encoded = encoder.encode(ir, ImageFormat.Avif, HeifEncodeOptions(), ctx)
        assertTrue(encoded.size > 12, "Output must have at least 12 bytes")

        val ftyp = encoded.data.sliceArray(4 until 8).decodeToString()
        assertEquals("ftyp", ftyp, "AVIF encode output must be a valid ISO BMFF container")
    }

    // -- Non-square dimensions survive roundtrip ---------------------------

    @Test
    fun heif_nonSquare_widthAndHeightPreserved() = runTest {
        val ir      = LibHeifTestHelpers.solidColor(120, 80, r = 10, g = 20, b = 30)
        val encoded = encoder.encode(ir, ImageFormat.Heif, HeifEncodeOptions(), ctx)
        val decoded = decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)
        assertEquals(120, decoded.width,  "Non-square width must survive HEIF roundtrip")
        assertEquals(80,  decoded.height, "Non-square height must survive HEIF roundtrip")
    }

    @Test
    fun avif_nonSquare_widthAndHeightPreserved() = runTest {
        val ir      = LibHeifTestHelpers.solidColor(160, 90, r = 30, g = 60, b = 90)
        val encoded = encoder.encode(ir, ImageFormat.Avif, HeifEncodeOptions(), ctx)
        val decoded = decoder.decode(encoded, CanonicalImageDecodeOptions(), ctx)
        assertEquals(160, decoded.width,  "Non-square width must survive AVIF roundtrip")
        assertEquals(90,  decoded.height, "Non-square height must survive AVIF roundtrip")
    }

    // -- Quality option is wired through -----------------------------------

    @Test
    fun heif_highQualityEncode_producesOutput() = runTest {
        val ir      = LibHeifTestHelpers.solidColor(64, 64, r = 100, g = 150, b = 200)
        val encoded = encoder.encode(ir, ImageFormat.Heif, HeifEncodeOptions(quality = 0.95f), ctx)
        assertTrue(encoded.isNotEmpty(), "High-quality HEIF encode must produce non-empty output")
    }

    @Test
    fun heif_lowQualityEncode_producesOutput() = runTest {
        val ir      = LibHeifTestHelpers.solidColor(64, 64, r = 100, g = 150, b = 200)
        val encoded = encoder.encode(ir, ImageFormat.Heif, HeifEncodeOptions(quality = 0.2f), ctx)
        assertTrue(encoded.isNotEmpty(), "Low-quality HEIF encode must produce non-empty output")
    }
}
